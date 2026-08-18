package io.github.woxakv.koreadercompanion.presentation.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentBookUi

// Strict black on white regardless of system theme, matching the e-ink
// default: no day/night variation to keep. Duplicated per-file rather than
// shared, matching this module's existing convention (see
// CurrentlyReadingWidgetContent.kt/CombinedWidgetContent.kt).
private val WidgetTextColor = ColorProvider(day = Color.Black, night = Color.Black)

// AllSources-only: two book cards plus stats plus the heatmap need to fit
// a launcher's 4x3 grid cell, so each card here is smaller (both cover and
// padding) than the standalone widget's 56x84dp/12dp default - shrinking
// just this widget's own book rows rather than the shared default, so
// CurrentlyReadingGlanceWidget/CombinedGlanceWidget are unaffected.
private val ALL_SOURCES_COVER_WIDTH = 48.dp
private val ALL_SOURCES_COVER_HEIGHT = 72.dp
private val ALL_SOURCES_CARD_PADDING = 8.dp
private val ALL_SOURCES_CARD_HEIGHT = ALL_SOURCES_COVER_HEIGHT + ALL_SOURCES_CARD_PADDING * 2

/**
 * Stacks the title/refresh row, both currently-reading cards (KOReader then
 * Mihon), and the existing stats/heatmap sections into one combined widget -
 * the "All sources" peer of [CombinedWidgetContent], which only ever shows
 * the KOReader card. The Mihon card only renders when [mihonBook] is
 * non-null - no placeholder/empty card reserves space for a missing Mihon
 * book, the same rule CurrentlyReadingScreen's `mihonBook?.let { ... }`
 * follows. Each section keeps its own tap behavior unchanged (each book
 * section may open its own app directly; the other two always open this
 * app) rather than unifying them into one shared tap zone.
 */
@Composable
fun AllSourcesWidgetContent(
    koreaderBook: CurrentBookUi?,
    mihonBook: CurrentBookUi?,
    summary: ReadingStatsSummary?,
    heatmapBitmap: Bitmap?,
    onKoreaderBookClick: Action,
    onMihonBookClick: Action,
    onOtherClick: Action,
    onRefreshClick: Action,
    isRefreshing: Boolean = false,
    bookEmptyStateMessage: String = "Open KOReader Companion to set up",
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        ) {
            Text(
                text = "Currently Reading",
                style = TextStyle(color = WidgetTextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (isRefreshing) "Loading..." else "Refresh",
                style = TextStyle(color = WidgetTextColor, fontSize = 12.sp),
                modifier = GlanceModifier.clickable(onRefreshClick),
            )
        }
        CurrentlyReadingWidgetContent(
            book = koreaderBook,
            onClick = onKoreaderBookClick,
            modifier = GlanceModifier.fillMaxWidth().height(ALL_SOURCES_CARD_HEIGHT),
            coverWidth = ALL_SOURCES_COVER_WIDTH,
            coverHeight = ALL_SOURCES_COVER_HEIGHT,
            contentPadding = ALL_SOURCES_CARD_PADDING,
            emptyStateMessage = bookEmptyStateMessage,
        )
        if (mihonBook != null) {
            CurrentlyReadingWidgetContent(
                book = mihonBook,
                onClick = onMihonBookClick,
                modifier = GlanceModifier.fillMaxWidth().height(ALL_SOURCES_CARD_HEIGHT),
                coverWidth = ALL_SOURCES_COVER_WIDTH,
                coverHeight = ALL_SOURCES_COVER_HEIGHT,
                contentPadding = ALL_SOURCES_CARD_PADDING,
            )
        }
        StatsWidgetContent(
            summary = summary,
            onClick = onOtherClick,
            // 70dp (not the 58dp this widget briefly shrank to): below this,
            // StatsWidgetContent's second row - the "This Month: ... This
            // Week: ..." line - gets clipped by its parent Column's fixed
            // height, since that Column doesn't scroll or reflow. Matches
            // the standalone Stats widget's own height for the same reason.
            modifier = GlanceModifier.fillMaxWidth().height(70.dp),
        )
        CalendarGridGraphWidgetContent(
            bitmap = heatmapBitmap,
            onClick = onOtherClick,
            // Fixed height, NOT defaultWeight() (unlike CombinedWidgetContent's
            // equivalent) - this widget has an extra fixed-height section
            // Combined doesn't (the second, Mihon book card), so its total
            // fixed-content height is taller. Claiming "whatever's left over"
            // for the heatmap left too little guaranteed room for the stats
            // row's second line ("This Month: ... This Week: ...") on-device,
            // clipping it. A smaller, fixed heatmap height guarantees stats
            // always has its full 70dp, at the cost of some empty space below
            // the heatmap on a generously-sized real allocation - an explicit
            // trade-off, not an oversight.
            modifier = GlanceModifier.fillMaxWidth().height(110.dp),
        )
    }
}
