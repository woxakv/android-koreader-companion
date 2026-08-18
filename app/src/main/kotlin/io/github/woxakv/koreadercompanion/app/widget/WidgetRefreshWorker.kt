package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "WidgetRefreshWorker"

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val getCurrentBook: GetCurrentBookUseCase,
    private val getReadingStatsSummary: GetReadingStatsSummaryUseCase,
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
    private val getMihonCurrentBook: GetMihonCurrentBookUseCase,
) : CoroutineWorker(context, params) {

    companion object {
        internal val refreshMutex = Mutex()
    }

    override suspend fun doWork(): Result = refreshMutex.withLock {
        try {
            val manager = GlanceAppWidgetManager(applicationContext)

            val currentlyReading = CurrentlyReadingGlanceWidget(getCurrentBook)
            manager.getGlanceIds(CurrentlyReadingGlanceWidget::class.java)
                .forEach { id -> currentlyReading.update(applicationContext, id) }

            val stats = StatsGlanceWidget(getReadingStatsSummary, getMergedDailyReadingStats, getStatsSourceFilter)
            manager.getGlanceIds(StatsGlanceWidget::class.java)
                .forEach { id -> stats.update(applicationContext, id) }

            val calendarGridGraph = CalendarGridGraphGlanceWidget(getMergedDailyReadingStats, getStatsSourceFilter)
            manager.getGlanceIds(CalendarGridGraphGlanceWidget::class.java)
                .forEach { id -> calendarGridGraph.update(applicationContext, id) }

            val combined = CombinedGlanceWidget(
                getCurrentBook,
                getReadingStatsSummary,
                getMergedDailyReadingStats,
                getStatsSourceFilter,
            )
            manager.getGlanceIds(CombinedGlanceWidget::class.java)
                .forEach { id ->
                    // Clears any in-flight "Loading..." state (set by
                    // WidgetManualRefreshAction) now that the refresh it
                    // was waiting on has actually completed - unconditional
                    // for every combined widget, not just a manually-tapped
                    // one, so a stuck flag from a killed process self-heals
                    // on the very next refresh cycle regardless of cause.
                    updateAppWidgetState(applicationContext, id) { prefs ->
                        prefs[IS_REFRESHING_KEY] = false
                    }
                    combined.update(applicationContext, id)
                }

            val allSources = AllSourcesGlanceWidget(
                getCurrentBook,
                getMihonCurrentBook,
                getReadingStatsSummary,
                getMergedDailyReadingStats,
                getStatsSourceFilter,
            )
            manager.getGlanceIds(AllSourcesGlanceWidget::class.java)
                .forEach { id ->
                    // Clears any in-flight "Loading..." state (set by
                    // AllSourcesManualRefreshAction) now that the refresh it
                    // was waiting on has actually completed - unconditional
                    // for every AllSources widget, not just a manually-tapped
                    // one, so a stuck flag from a killed process self-heals
                    // on the very next refresh cycle regardless of cause.
                    updateAppWidgetState(applicationContext, id) { prefs ->
                        prefs[IS_REFRESHING_KEY] = false
                    }
                    allSources.update(applicationContext, id)
                }

            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Widget refresh failed, will retry", t)
            Result.retry()
        }
    }
}
