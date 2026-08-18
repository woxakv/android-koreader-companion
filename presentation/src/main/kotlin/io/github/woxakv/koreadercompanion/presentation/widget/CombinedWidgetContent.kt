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
// CurrentlyReadingWidgetContent.kt/StatsWidgetContent.kt).
private val WidgetTextColor = ColorProvider(day = Color.Black, night = Color.Black)

// Combined-only: the book cover is notably bigger here than in the
// standalone Currently Reading widget (which keeps CurrentlyReadingWidgetContent's
// 56x84dp default) since this view has more room to spend on it.
private val COMBINED_COVER_WIDTH = 76.dp
private val COMBINED_COVER_HEIGHT = 114.dp

/**
 * Stacks the title/refresh row and the three existing widget sections into
 * one combined widget. Each section keeps its own tap behavior unchanged
 * (the book section may open KOReader directly; the other two always open
 * the app) rather than unifying them into one shared tap zone.
 */
@Composable
fun CombinedWidgetContent(
    book: CurrentBookUi?,
    summary: ReadingStatsSummary?,
    heatmapBitmap: Bitmap?,
    onBookClick: Action,
    onOtherClick: Action,
    onRefreshClick: Action,
    isRefreshing: Boolean = false,
    bookEmptyStateMessage: String = "Open KOReader Companion to set up",
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
            book = book,
            onClick = onBookClick,
            modifier = GlanceModifier.fillMaxWidth().height(140.dp),
            coverWidth = COMBINED_COVER_WIDTH,
            coverHeight = COMBINED_COVER_HEIGHT,
            emptyStateMessage = bookEmptyStateMessage,
        )
        StatsWidgetContent(
            summary = summary,
            onClick = onOtherClick,
            modifier = GlanceModifier.fillMaxWidth().height(85.dp),
        )
        CalendarGridGraphWidgetContent(
            bitmap = heatmapBitmap,
            onClick = onOtherClick,
            // defaultWeight() again: this is what originally stretched the
            // cells out of proportion, but that was paired with FillBounds
            // (stretch-to-exactly-fill). CalendarGridGraphWidgetContent now
            // uses ContentScale.Fit, which can only ever scale the graph up
            // to the largest size that preserves its aspect ratio within
            // whatever space it's given - so claiming the column's leftover
            // space now makes the graph bigger, never distorted, and never
            // clipped. Since the widget's overall box height has a hard
            // floor on this launcher that a smaller fixed height here can't
            // get under anyway, there's no reason to leave that leftover
            // space blank instead of giving it to the graph.
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}
