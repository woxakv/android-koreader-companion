package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import io.github.woxakv.koreadercompanion.app.MainActivity
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.error.isSetupIssue
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentBookUi
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.toUi
import io.github.woxakv.koreadercompanion.presentation.util.openInKoreaderIntent
import io.github.woxakv.koreadercompanion.presentation.widget.AllSourcesWidgetContent
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_HORIZONTAL_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_GAP_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_CELL_SIZE_PX
import io.github.woxakv.koreadercompanion.presentation.widget.CALENDAR_GRID_GRAPH_DAYS
import io.github.woxakv.koreadercompanion.presentation.widget.assignBuckets
import io.github.woxakv.koreadercompanion.presentation.widget.render
import java.time.LocalDate

// Duplicated per-file rather than shared, matching this module's existing
// convention (see CurrentlyReadingGlanceWidget.kt / CombinedGlanceWidget.kt).
private const val SETUP_MESSAGE = "Open KOReader Companion to set up"

/**
 * The concrete GlanceAppWidget lives in :app (not :presentation) because it
 * needs actionStartActivity<MainActivity>()/actionStartActivity(Intent), and
 * MainActivity lives here. Peer of [CombinedGlanceWidget] that additionally
 * fetches and renders the Mihon "currently reading" card alongside the
 * KOReader one.
 */
class AllSourcesGlanceWidget(
    private val getCurrentBook: GetCurrentBookUseCase,
    private val getMihonCurrentBook: GetMihonCurrentBookUseCase,
    private val getReadingStatsSummary: GetReadingStatsSummaryUseCase,
    private val getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase,
    private val getStatsSourceFilter: GetStatsSourceFilterUseCase,
) : GlanceAppWidget() {

    // See CalendarGridGraphGlanceWidget for why Exact (not the Single
    // default) is required - the embedded graph section needs LocalSize to
    // reflect this widget's real current bounds, not one nominal size.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bookResult = runCatching { getCurrentBook() }.getOrNull()
        val book = (bookResult as? Try.Success)?.value?.toUi()
        // Only a genuinely never-configured error keeps the generic setup
        // prompt; every other failure surfaces its own message instead of
        // collapsing to that same misleading text (see KoreaderError.kt).
        val bookEmptyStateMessage = (bookResult as? Try.Failure)?.error
            ?.takeUnless { it.isSetupIssue() }
            ?.message
            ?: SETUP_MESSAGE

        // "Chapter" unit label matches CurrentlyReadingViewModel.refreshMihonBook's
        // existing convention for the in-app Mihon card.
        val mihonBookResult = runCatching { getMihonCurrentBook() }.getOrNull()
        val mihonBook = (mihonBookResult as? Try.Success)?.value?.toUi(unitLabel = "Chapter")

        val today = LocalDate.now()
        val filter = runCatching { getStatsSourceFilter() }.getOrNull()

        // Two independent merged fetches, not one shared fetch - see
        // CurrentlyReadingViewModel.refreshStatsAndHeatmap for why: the
        // summary's streak calculation needs GetReadingStatsSummaryUseCase's
        // own 365-day LOOKBACK_DAYS window, while the heatmap only ever
        // displayed CALENDAR_GRID_GRAPH_DAYS (182). Sharing one 182-day fetch
        // for both would silently truncate any streak longer than 182 days.
        val summary = filter?.let {
            runCatching {
                val stats = getMergedDailyReadingStats(days = GetReadingStatsSummaryUseCase.LOOKBACK_DAYS, filter = it)
                getReadingStatsSummary.summarize(stats, today)
            }.getOrNull()
        }

        val bitmap = filter?.let {
            runCatching { getMergedDailyReadingStats(days = CALENDAR_GRID_GRAPH_DAYS, filter = it) }.getOrNull()
        }
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
            val isRefreshing = currentState(key = IS_REFRESHING_KEY) ?: false
            AllSourcesWidgetContent(
                koreaderBook = book,
                mihonBook = mihonBook,
                summary = summary,
                heatmapBitmap = bitmap,
                onKoreaderBookClick = actionStartActivityIntent(koreaderTapIntent(context, book)),
                onMihonBookClick = actionStartActivityIntent(mihonTapIntent(context, mihonBook)),
                onOtherClick = actionStartActivity<MainActivity>(),
                onRefreshClick = actionRunCallback<AllSourcesManualRefreshAction>(),
                isRefreshing = isRefreshing,
                bookEmptyStateMessage = bookEmptyStateMessage,
            )
        }
    }

    /** Tapping the KOReader book section opens it directly in KOReader when possible, else opens the app. */
    private fun koreaderTapIntent(context: Context, book: CurrentBookUi?): Intent =
        book?.openInKoreaderUri?.let { openInKoreaderIntent(context, it) }
            ?: Intent(context, MainActivity::class.java)

    /**
     * Tapping the Mihon book section opens Mihon directly when possible,
     * else opens this app - mirrors CurrentlyReadingScreen's in-app Mihon
     * card tap handler.
     */
    private fun mihonTapIntent(context: Context, book: CurrentBookUi?): Intent =
        book?.launchIntentPackage
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(context, MainActivity::class.java)
}
