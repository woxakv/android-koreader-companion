package io.github.woxakv.koreadercompanion.presentation.stats

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.domain.model.StatsBucket
import io.github.woxakv.koreadercompanion.domain.model.StatsGranularity
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.usecase.BucketDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
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
class StatsViewModel @Inject constructor(
    private val getReadingStatsSummary: GetReadingStatsSummaryUseCase,
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val bucketDailyReadingStats: BucketDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
    private val setStatsSourceFilter: SetStatsSourceFilterUseCase,
    private val hasKoreaderAccess: HasKoreaderAccessUseCase,
    private val hasMihonAccess: HasMihonAccessUseCase,
) : ViewModel() {

    private val _summary = MutableStateFlow<ReadingStatsSummary?>(null)
    val summary: StateFlow<ReadingStatsSummary?> = _summary.asStateFlow()

    private val _buckets = MutableStateFlow<List<StatsBucket>>(emptyList())
    val buckets: StateFlow<List<StatsBucket>> = _buckets.asStateFlow()

    private val _filter = MutableStateFlow(StatsSourceFilter.ALL)
    val filter: StateFlow<StatsSourceFilter> = _filter.asStateFlow()

    private val _granularity = MutableStateFlow(StatsGranularity.MONTH)
    val granularity: StateFlow<StatsGranularity> = _granularity.asStateFlow()

    private val _heatmapMetric = MutableStateFlow(HeatmapMetric.PAGES)
    val heatmapMetric: StateFlow<HeatmapMetric> = _heatmapMetric.asStateFlow()

    private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
    val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

    // Only meaningful when both sources are granted - with just one, ALL and
    // that source produce identical merged results, so the choice is a
    // no-op and the row is hidden rather than shown with only itself to pick.
    private val _showFilter = MutableStateFlow(true)
    val showFilter: StateFlow<Boolean> = _showFilter.asStateFlow()

    // The one-off ALL_TIME_LOOKBACK_DAYS fetch backing every period card and
    // both graphs' buckets - cached here so a granularity change re-buckets
    // in-memory instead of re-fetching (avoiding StatisticsDatabaseCopier's
    // full-DB-copy cost on every granularity tap).
    private var lastFetchedStats: List<DailyReadingStat> = emptyList()

    init {
        // Reads the persisted filter first so the starting selection matches
        // whatever was last chosen, instead of always starting at ALL in
        // memory - the same persisted value Home and widgets read.
        viewModelScope.launch {
            _filter.value = getStatsSourceFilter()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // Re-reads the persisted filter on every refresh, not just at
            // init - Home (a second writer to this same app-wide setting)
            // can change it while this ViewModel is alive but off-screen
            // (its back stack entry survives navigating away and back).
            // Without this, returning to Stats after changing the filter
            // on Home would keep showing stale, pre-change data.
            _filter.value = getStatsSourceFilter()
            val bothGranted = hasKoreaderAccess() && hasMihonAccess()
            _showFilter.value = bothGranted
            if (!bothGranted && _filter.value != StatsSourceFilter.ALL) {
                onFilterSelected(StatsSourceFilter.ALL)
            } else {
                fetchAndDerive()
            }
        }
    }

    private suspend fun fetchAndDerive() {
        val today = LocalDate.now()
        lastFetchedStats = getMergedDailyReadingStats(
            days = GetReadingStatsSummaryUseCase.ALL_TIME_LOOKBACK_DAYS,
            filter = _filter.value,
        )
        _summary.value = getReadingStatsSummary.summarize(lastFetchedStats, today)
        _buckets.value = bucketDailyReadingStats(lastFetchedStats, _granularity.value, today)
        renderHeatmap()
    }

    /**
     * Only re-buckets the two graphs from the already-fetched
     * [lastFetchedStats] - no refetch. The 4 period cards are unaffected by
     * granularity and stay derived from the last [fetchAndDerive] call.
     */
    fun onGranularitySelected(granularity: StatsGranularity) {
        _granularity.value = granularity
        _buckets.value = bucketDailyReadingStats(lastFetchedStats, granularity, LocalDate.now())
    }

    /**
     * Re-renders the heatmap bitmap from the already-fetched
     * [lastFetchedStats] - no refetch. [assignBuckets] only reads the dates
     * within its own CALENDAR_GRID_GRAPH_DAYS-sized window, so passing the
     * larger ALL_TIME_LOOKBACK_DAYS-backed list is safe and avoids a second
     * StatisticsDatabaseCopier round trip.
     */
    private fun renderHeatmap() {
        val today = LocalDate.now()
        val valueSelector = if (_heatmapMetric.value == HeatmapMetric.TIME) {
            DailyReadingStat::minutesRead
        } else {
            DailyReadingStat::pagesRead
        }
        val buckets = assignBuckets(lastFetchedStats, CALENDAR_GRID_GRAPH_DAYS, today, valueSelector = valueSelector)
        _heatmapBitmap.value = render(
            buckets,
            CALENDAR_GRID_GRAPH_DAYS,
            today,
            CALENDAR_GRID_GRAPH_CELL_SIZE_PX,
            CALENDAR_GRID_GRAPH_CELL_GAP_PX,
            CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX,
        )
    }

    /**
     * Only re-renders the heatmap from the already-fetched [lastFetchedStats]
     * - no refetch, same in-memory-recompute principle as
     * [onGranularitySelected].
     */
    fun onHeatmapMetricSelected(metric: HeatmapMetric) {
        _heatmapMetric.value = metric
        renderHeatmap()
    }

    /**
     * A filter change is app-wide (shared with Home and every widget via
     * [SetStatsSourceFilterUseCase]), so it needs a genuinely new merged
     * fetch, unlike granularity. No widget-refresh scheduling here - nothing
     * on this page is widget-visible.
     */
    fun onFilterSelected(filter: StatsSourceFilter) {
        _filter.value = filter
        viewModelScope.launch {
            setStatsSourceFilter(filter)
            refresh()
        }
    }
}
