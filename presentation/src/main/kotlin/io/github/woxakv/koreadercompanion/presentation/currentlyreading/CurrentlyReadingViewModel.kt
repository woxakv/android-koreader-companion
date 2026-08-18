package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.woxakv.koreadercompanion.core.error.AppError
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.core.result.fold
import io.github.woxakv.koreadercompanion.domain.error.isSetupIssue
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.SetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_SIZE_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_DAYS
import io.github.woxakv.koreadercompanion.presentation.widget.assignBuckets
import io.github.woxakv.koreadercompanion.presentation.widget.render
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CurrentlyReadingViewModel @Inject constructor(
    private val getCurrentBook: GetCurrentBookUseCase,
    private val getMihonCurrentBook: GetMihonCurrentBookUseCase,
    private val grantKoreaderAccess: GrantKoreaderAccessUseCase,
    private val hasKoreaderAccess: HasKoreaderAccessUseCase,
    private val hasMihonAccess: HasMihonAccessUseCase,
    private val getReadingStatsSummary: GetReadingStatsSummaryUseCase,
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
    private val setStatsSourceFilter: SetStatsSourceFilterUseCase,
    private val widgetRefreshScheduler: WidgetRefreshScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow<CurrentlyReadingUiState>(CurrentlyReadingUiState.Loading)
    val state: StateFlow<CurrentlyReadingUiState> = _state.asStateFlow()

    private val _mihonBook = MutableStateFlow<CurrentBookUi?>(null)
    val mihonBook: StateFlow<CurrentBookUi?> = _mihonBook.asStateFlow()

    private val _statsSummary = MutableStateFlow<ReadingStatsSummary?>(null)
    val statsSummary: StateFlow<ReadingStatsSummary?> = _statsSummary.asStateFlow()

    private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
    val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

    private val _filter = MutableStateFlow(StatsSourceFilter.ALL)
    val filter: StateFlow<StatsSourceFilter> = _filter.asStateFlow()

    // Only meaningful when both sources are granted - with just one, ALL and
    // that source produce identical merged results, so the choice is a
    // no-op and the row is hidden rather than shown with only itself to pick.
    private val _showFilter = MutableStateFlow(true)
    val showFilter: StateFlow<Boolean> = _showFilter.asStateFlow()

    init {
        // Reads the persisted filter first so the starting selection matches
        // whatever was last chosen, instead of always starting at ALL in
        // memory - the same persisted value widgets read on their own
        // refresh cycle.
        viewModelScope.launch {
            _filter.value = getStatsSourceFilter()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = CurrentlyReadingUiState.Loading
            getCurrentBook().fold(
                onSuccess = { book -> _state.value = CurrentlyReadingUiState.Content(book.toUi()) },
                onFailure = { error -> _state.value = error.toUiState() },
            )
        }
        refreshMihonBook()
        // Resyncs from persisted storage, then fetches with the resynced
        // value - sequential within one coroutine so the read genuinely
        // happens before the fetch. Stats (a second writer to this same
        // app-wide setting) can change it while this ViewModel is alive
        // but off-screen (its back stack entry survives navigating away
        // and back); without this, returning to Home would keep showing
        // stale, pre-change numbers. Deliberately NOT done inside
        // refreshStatsAndHeatmap() itself, which onFilterSelected() also
        // calls - resyncing there would race the not-yet-completed
        // setStatsSourceFilter() write from a just-tapped chip on this
        // same screen and could revert the user's own selection.
        viewModelScope.launch {
            _filter.value = getStatsSourceFilter()
            refreshStatsAndHeatmap()
        }
        refreshFilterAvailability()
    }

    /**
     * Re-checks which sources are granted every refresh, since Config's
     * revoke action can change this at any time. Snaps a stale non-ALL
     * selection back to ALL when the row becomes hidden, so a filter chosen
     * while both sources were granted doesn't silently keep showing
     * zeroed-out stats for a source that's no longer available.
     */
    private fun refreshFilterAvailability() {
        viewModelScope.launch {
            val bothGranted = hasKoreaderAccess() && hasMihonAccess()
            _showFilter.value = bothGranted
            if (!bothGranted && _filter.value != StatsSourceFilter.ALL) {
                onFilterSelected(StatsSourceFilter.ALL)
            }
        }
    }

    /**
     * Mihon is a second, independent reading-data source (never merged with
     * KOReader's card) - any failure here (ungranted, no `.tachibk`, no
     * history) just means "no Mihon card to show", not a user-visible error.
     */
    private fun refreshMihonBook() {
        viewModelScope.launch {
            _mihonBook.value = (getMihonCurrentBook() as? Try.Success)?.value?.toUi(unitLabel = "Chapter")
        }
    }

    /**
     * Two independent merged fetches, not one shared fetch - the summary
     * (streak in particular) needs GetReadingStatsSummaryUseCase's own
     * 365-day LOOKBACK_DAYS window, same as before this source-merging was
     * added; the heatmap only ever displayed CALENDAR_GRID_GRAPH_DAYS (182).
     * Sharing one 182-day fetch for both would silently truncate any streak
     * longer than 182 days - a real regression from the pre-merge behavior,
     * not something to reintroduce for the sake of one fetch instead of two.
     */
    private fun refreshStatsAndHeatmap() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val filter = _filter.value
            val mergedForSummary = getMergedDailyReadingStats(
                days = GetReadingStatsSummaryUseCase.LOOKBACK_DAYS,
                filter = filter,
            )
            _statsSummary.value = getReadingStatsSummary.summarize(mergedForSummary, today)

            val mergedForHeatmap = getMergedDailyReadingStats(days = CALENDAR_GRID_GRAPH_DAYS, filter = filter)
            val buckets = assignBuckets(mergedForHeatmap, CALENDAR_GRID_GRAPH_DAYS, today)
            _heatmapBitmap.value = render(
                buckets,
                CALENDAR_GRID_GRAPH_DAYS,
                today,
                CALENDAR_GRID_GRAPH_CELL_SIZE_PX,
                CALENDAR_GRID_GRAPH_CELL_GAP_PX,
                CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX,
            )
        }
    }

    /**
     * Called by the All/KOReader/Mihon filter row; re-derives the stats row
     * and heatmap for the new selection, persists it (widgets have no UI of
     * their own to change it, so they read whatever was last chosen here),
     * and requests an immediate widget refresh - mirrors
     * ConfigViewModel.onTreeSelected's re-grant -> requestImmediateRefresh()
     * pattern: a user-initiated change that should be reflected promptly.
     */
    fun onFilterSelected(filter: StatsSourceFilter) {
        _filter.value = filter
        refreshStatsAndHeatmap()
        viewModelScope.launch {
            setStatsSourceFilter(filter)
            widgetRefreshScheduler.requestImmediateRefresh()
        }
    }

    /** Re-grant path for the inline PermissionRequired/Error prompt. */
    fun onTreeSelected(treeUriString: String) {
        viewModelScope.launch {
            grantKoreaderAccess(treeUriString)
            refresh()
        }
    }

    private fun AppError.toUiState(): CurrentlyReadingUiState =
        if (isSetupIssue()) {
            CurrentlyReadingUiState.PermissionRequired(message)
        } else {
            CurrentlyReadingUiState.Error(message = message, retryable = true)
        }
}
