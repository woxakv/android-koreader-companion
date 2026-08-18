package io.github.woxakv.koreadercompanion.presentation.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.woxakv.koreadercompanion.core.result.fold
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler
import io.github.woxakv.koreadercompanion.domain.usecase.GrantBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.IsMihonGrantNarrowUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeMihonAccessUseCase
import io.github.woxakv.koreadercompanion.presentation.navigation.StorageGrantCoordinator
import io.github.woxakv.koreadercompanion.presentation.navigation.StorageGrantUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Near-duplicate of OnboardingViewModel by design (see plan 015's Context & decisions) - if
// you're fixing a grant-flow bug here, check presentation/.../onboarding/OnboardingViewModel.kt
// too. The grant-dispatch logic itself is shared via StorageGrantCoordinator; what differs here
// is what happens around a successful grant - Config doesn't navigate away and doesn't schedule
// periodic widget refresh on a koreader grant (only Onboarding, its first-grant path, does).
@HiltViewModel
class ConfigViewModel @Inject constructor(
    hasKoreaderAccess: HasKoreaderAccessUseCase,
    grantKoreaderAccess: GrantKoreaderAccessUseCase,
    revokeKoreaderAccess: RevokeKoreaderAccessUseCase,
    hasBooksAccess: HasBooksAccessUseCase,
    grantBooksAccess: GrantBooksAccessUseCase,
    revokeBooksAccess: RevokeBooksAccessUseCase,
    hasMihonAccess: HasMihonAccessUseCase,
    grantMihonAccess: GrantMihonAccessUseCase,
    revokeMihonAccess: RevokeMihonAccessUseCase,
    private val isMihonGrantNarrow: IsMihonGrantNarrowUseCase,
    private val widgetRefreshScheduler: WidgetRefreshScheduler,
) : ViewModel() {

    private val grantCoordinator = StorageGrantCoordinator(
        hasKoreaderAccess = hasKoreaderAccess,
        grantKoreaderAccess = grantKoreaderAccess,
        revokeKoreaderAccess = revokeKoreaderAccess,
        hasBooksAccess = hasBooksAccess,
        grantBooksAccess = grantBooksAccess,
        revokeBooksAccess = revokeBooksAccess,
        hasMihonAccess = hasMihonAccess,
        grantMihonAccess = grantMihonAccess,
        revokeMihonAccess = revokeMihonAccess,
    )

    private val _state = MutableStateFlow(StorageGrantUiState())
    val state: StateFlow<StorageGrantUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val grants = grantCoordinator.currentGrants()
            _state.value = StorageGrantUiState(
                grants = grants,
                mihonGrantNarrow = if (grants[StorageTarget.MIHON] == true) isMihonGrantNarrow() else false,
            )
        }
    }

    fun onTreeSelected(target: StorageTarget, treeUriString: String) {
        viewModelScope.launch {
            grantCoordinator.grant(target, treeUriString).fold(
                onSuccess = {
                    val mihonGrantNarrow = if (target == StorageTarget.MIHON) isMihonGrantNarrow() else null
                    _state.update {
                        it.copy(
                            grants = it.grants + (target to true),
                            error = null,
                            mihonGrantNarrow = mihonGrantNarrow ?: it.mihonGrantNarrow,
                        )
                    }
                    if (target == StorageTarget.KOREADER) {
                        widgetRefreshScheduler.requestImmediateRefresh()
                    }
                },
                onFailure = { error -> _state.update { it.copy(error = error.message) } },
            )
        }
    }

    /**
     * KOReader and Mihon are the app's two reading-data sources - at least
     * one must stay granted or every currently-reading card and stat goes
     * blank with no way back in from this screen. Books (cover art only)
     * has no such floor. Blocked client-side rather than letting the
     * revoke happen and surfacing a broken app afterwards.
     */
    fun onRevoke(target: StorageTarget) {
        val grants = _state.value.grants
        val otherReadingSourceGranted = when (target) {
            StorageTarget.KOREADER -> grants[StorageTarget.MIHON] == true
            StorageTarget.MIHON -> grants[StorageTarget.KOREADER] == true
            StorageTarget.BOOKS -> true
        }
        if (!otherReadingSourceGranted) {
            _state.update { it.copy(error = "Keep at least one of KOReader or Mihon granted.") }
            return
        }
        viewModelScope.launch {
            grantCoordinator.revoke(target)
            _state.update {
                it.copy(
                    grants = it.grants + (target to false),
                    error = null,
                    mihonGrantNarrow = if (target == StorageTarget.MIHON) false else it.mihonGrantNarrow,
                )
            }
        }
    }
}
