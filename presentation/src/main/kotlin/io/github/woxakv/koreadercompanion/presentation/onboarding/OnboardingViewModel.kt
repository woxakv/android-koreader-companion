package io.github.woxakv.koreadercompanion.presentation.onboarding

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

// Near-duplicate of ConfigViewModel by design (see plan 015's Context & decisions) - if you're
// fixing a grant-flow bug here, check presentation/.../config/ConfigViewModel.kt too. The
// grant-dispatch logic itself is shared via StorageGrantCoordinator; what differs here is what
// happens around a successful grant (navigating on Continue, scheduling periodic widget
// refresh on a koreader grant) - Config doesn't do either.
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    hasKoreaderAccess: HasKoreaderAccessUseCase,
    grantKoreaderAccess: GrantKoreaderAccessUseCase,
    revokeKoreaderAccess: RevokeKoreaderAccessUseCase,
    hasBooksAccess: HasBooksAccessUseCase,
    grantBooksAccess: GrantBooksAccessUseCase,
    revokeBooksAccess: RevokeBooksAccessUseCase,
    hasMihonAccess: HasMihonAccessUseCase,
    grantMihonAccess: GrantMihonAccessUseCase,
    revokeMihonAccess: RevokeMihonAccessUseCase,
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
            _state.value = StorageGrantUiState(grants = grantCoordinator.currentGrants())
        }
    }

    fun onTreeSelected(target: StorageTarget, treeUriString: String) {
        viewModelScope.launch {
            grantCoordinator.grant(target, treeUriString).fold(
                onSuccess = {
                    _state.update { it.copy(grants = it.grants + (target to true), error = null) }
                    if (target == StorageTarget.KOREADER) {
                        widgetRefreshScheduler.schedulePeriodicRefresh()
                        widgetRefreshScheduler.requestImmediateRefresh()
                    }
                },
                onFailure = { error -> _state.update { it.copy(error = error.message) } },
            )
        }
    }
}
