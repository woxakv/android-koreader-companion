package io.github.woxakv.koreadercompanion.presentation.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.woxakv.koreadercompanion.presentation.config.ConfigScreen
import io.github.woxakv.koreadercompanion.presentation.config.ConfigViewModel
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentlyReadingScreen
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentlyReadingViewModel
import io.github.woxakv.koreadercompanion.presentation.onboarding.OnboardingScreen
import io.github.woxakv.koreadercompanion.presentation.onboarding.OnboardingViewModel
import io.github.woxakv.koreadercompanion.presentation.stats.StatsScreen
import io.github.woxakv.koreadercompanion.presentation.stats.StatsViewModel

object AppDestinations {
    const val ONBOARDING = "onboarding"
    const val CURRENTLY_READING = "currentlyReading"
    const val CONFIG = "config"
    const val STATS = "stats"
}

@Composable
fun AppNavHost(
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // Falls back to startDestination while the back stack state hasn't
    // resolved yet on first composition (currentBackStackEntryAsState()
    // can be null for one frame) - without this fallback, the rail could
    // flash visible for a frame even when starting on Onboarding.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: startDestination

    Column(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f),
        ) {
            composable(AppDestinations.ONBOARDING) {
                val viewModel: OnboardingViewModel = hiltViewModel()
                val state by viewModel.state.collectAsState()

                OnboardingScreen(
                    state = state,
                    onTreeSelected = viewModel::onTreeSelected,
                    onContinue = {
                        navController.navigate(AppDestinations.CURRENTLY_READING) {
                            popUpTo(AppDestinations.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(AppDestinations.CURRENTLY_READING) {
                val viewModel: CurrentlyReadingViewModel = hiltViewModel()
                // The ViewModel is scoped to this destination's back stack entry, which
                // survives navigating to Config and back (Home is never popped) - so its
                // own init-time refresh only ever runs once. Without this, granting or
                // revoking access in Config leaves Home showing stale data (e.g. a
                // just-granted Mihon card not appearing) until the user taps Refresh
                // manually. Compose Navigation removes this composable from composition
                // while Config is showing and re-adds it on return, so LaunchedEffect(Unit)
                // re-fires exactly on that "came back to Home" transition - not on every
                // recomposition.
                LaunchedEffect(Unit) { viewModel.refresh() }
                val state by viewModel.state.collectAsState()
                val mihonBook by viewModel.mihonBook.collectAsState()
                val statsSummary by viewModel.statsSummary.collectAsState()
                val heatmapBitmap by viewModel.heatmapBitmap.collectAsState()
                val statsSourceFilter by viewModel.filter.collectAsState()
                val showFilter by viewModel.showFilter.collectAsState()

                CurrentlyReadingScreen(
                    state = state,
                    mihonBook = mihonBook,
                    statsSummary = statsSummary,
                    heatmapBitmap = heatmapBitmap,
                    statsSourceFilter = statsSourceFilter,
                    showFilter = showFilter,
                    onFilterSelected = viewModel::onFilterSelected,
                    onTreeSelected = viewModel::onTreeSelected,
                    onRefresh = viewModel::refresh,
                )
            }

            composable(AppDestinations.CONFIG) {
                val viewModel: ConfigViewModel = hiltViewModel()
                val state by viewModel.state.collectAsState()

                ConfigScreen(
                    state = state,
                    onTreeSelected = viewModel::onTreeSelected,
                    onRevoke = viewModel::onRevoke,
                )
            }

            composable(AppDestinations.STATS) {
                val viewModel: StatsViewModel = hiltViewModel()
                LaunchedEffect(Unit) { viewModel.refresh() }
                val summary by viewModel.summary.collectAsState()
                val buckets by viewModel.buckets.collectAsState()
                val filter by viewModel.filter.collectAsState()
                val showFilter by viewModel.showFilter.collectAsState()
                val granularity by viewModel.granularity.collectAsState()
                val heatmapBitmap by viewModel.heatmapBitmap.collectAsState()
                val heatmapMetric by viewModel.heatmapMetric.collectAsState()

                StatsScreen(
                    summary = summary,
                    buckets = buckets,
                    sourceFilter = filter,
                    showSourceFilter = showFilter,
                    granularity = granularity,
                    heatmapBitmap = heatmapBitmap,
                    heatmapMetric = heatmapMetric,
                    onSourceFilterSelected = viewModel::onFilterSelected,
                    onGranularitySelected = viewModel::onGranularitySelected,
                    onHeatmapMetricSelected = viewModel::onHeatmapMetricSelected,
                )
            }
        }

        if (currentRoute != AppDestinations.ONBOARDING) {
            // Deliberately pure black, not the app's usual muted
            // EinkColors.Outline gray - confirmed with the user.
            HorizontalDivider(color = Color.Black, thickness = 1.dp)
            BottomNav(
                items = navItems,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Pinned to CURRENTLY_READING specifically, NOT
                        // navController.graph.findStartDestination().id
                        // (the usual bottom-nav/rail idiom) - this graph's
                        // actual start destination is ONBOARDING on a
                        // fresh install, and AppNavHost's own onboarding
                        // completion already does
                        // popUpTo(ONBOARDING){inclusive=true}, removing
                        // ONBOARDING from the back stack entirely. If
                        // popUpTo targeted the graph's start destination
                        // id after that point, it would resolve against
                        // an id no longer on the stack - a silent no-op
                        // that lets Home/Config pushes grow unbounded for
                        // the rest of that session instead of the flat,
                        // bounded switching this rail is supposed to give.
                        // CURRENTLY_READING is always the real "home" of
                        // this rail regardless of what the graph started
                        // on, so pin to it directly.
                        popUpTo(AppDestinations.CURRENTLY_READING) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
