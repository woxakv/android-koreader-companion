package io.github.woxakv.koreadercompanion.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.woxakv.koreadercompanion.app.widget.WidgetUpdateScheduler
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.presentation.navigation.AppDestinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val hasKoreaderAccess: HasKoreaderAccessUseCase,
    private val widgetUpdateScheduler: WidgetUpdateScheduler,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val hasAccess = hasKoreaderAccess()
            _startDestination.value = if (hasAccess) {
                AppDestinations.CURRENTLY_READING
            } else {
                AppDestinations.ONBOARDING
            }
            // Covers "on app start if access already exists". The periodic
            // job alone isn't reliable on its own - OS battery/Doze
            // throttling (common and aggressive on e-ink devices) can delay
            // it far past 30 minutes - so every app open also forces an
            // immediate refresh rather than waiting on that schedule.
            if (hasAccess) {
                widgetUpdateScheduler.schedulePeriodicRefresh()
                widgetUpdateScheduler.requestImmediateRefresh()
            }
        }
    }
}
