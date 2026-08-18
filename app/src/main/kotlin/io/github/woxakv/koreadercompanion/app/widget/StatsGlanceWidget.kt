package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import io.github.woxakv.koreadercompanion.app.MainActivity
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.presentation.widget.StatsWidgetContent
import java.time.LocalDate

/**
 * The concrete GlanceAppWidget lives in :app (not :presentation) because it
 * needs actionStartActivity<MainActivity>(), and MainActivity lives here.
 */
class StatsGlanceWidget(
    private val getReadingStatsSummary: GetReadingStatsSummaryUseCase,
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // getMergedDailyReadingStats() already collapses a source fetch
        // failure to "no data from that source", but this stays defensive
        // so a bug here surfaces as the widget's normal "no data" fallback
        // rather than leaving it frozen on stale data.
        val summary = runCatching {
            val filter = getStatsSourceFilter()
            val stats = getMergedDailyReadingStats(days = GetReadingStatsSummaryUseCase.LOOKBACK_DAYS, filter = filter)
            getReadingStatsSummary.summarize(stats, LocalDate.now())
        }.getOrNull()

        provideContent {
            StatsWidgetContent(
                summary = summary,
                onClick = actionStartActivity<MainActivity>(),
            )
        }
    }
}
