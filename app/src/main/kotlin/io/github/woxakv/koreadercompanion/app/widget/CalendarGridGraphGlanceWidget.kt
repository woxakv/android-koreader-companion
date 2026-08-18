package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import io.github.woxakv.koreadercompanion.app.MainActivity
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_SIZE_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_DAYS
import io.github.woxakv.koreadercompanion.presentation.widget.CalendarGridGraphWidgetContent
import io.github.woxakv.koreadercompanion.presentation.widget.assignBuckets
import io.github.woxakv.koreadercompanion.presentation.widget.render
import java.time.LocalDate

/**
 * The concrete GlanceAppWidget lives in :app (not :presentation) because it
 * needs actionStartActivity<MainActivity>(), and MainActivity lives here.
 */
class CalendarGridGraphGlanceWidget(
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
) : GlanceAppWidget() {

    // Single (the default) composes once for one nominal size and lets the
    // host stretch the result to fit - that stretch is what was distorting
    // the graph. Exact recomposes for the widget's real current size, so
    // LocalSize (read in CalendarGridGraphWidgetContent) reflects reality.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val bitmap = runCatching {
            val filter = getStatsSourceFilter()
            getMergedDailyReadingStats(days = CALENDAR_GRID_GRAPH_DAYS, filter = filter)
        }
            .getOrNull()
            ?.let { stats -> assignBuckets(stats, CALENDAR_GRID_GRAPH_DAYS, today) }
            ?.let { buckets ->
                render(
                    buckets,
                    CALENDAR_GRID_GRAPH_DAYS,
                    today,
                    CALENDAR_GRID_GRAPH_CELL_SIZE_PX,
                    CALENDAR_GRID_GRAPH_CELL_GAP_PX,
                    CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX,
                )
            }

        provideContent {
            CalendarGridGraphWidgetContent(bitmap = bitmap, onClick = actionStartActivity<MainActivity>())
        }
    }
}
