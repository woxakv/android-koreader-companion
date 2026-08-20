package io.github.woxakv.koreadercompanion.presentation.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.background
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
// a launcher's 4x3 grid cell, so padding here is tighter than the standalone
// widget's 12dp default - shrinking just this widget's own book rows rather
// than the shared default, so CurrentlyReadingGlanceWidget/CombinedGlanceWidget
// are unaffected.
private val ALL_SOURCES_CARD_PADDING = 4.dp

// Text-content floor for one book card: title (1 line, forced via
// titleAndAuthorMaxLines below) + author (1 line) + spacer (6dp) + progress
// bar (4dp) + spacer (6dp) + percent/time (1 line) + page count (1 line) =
// ~85dp content, plus this card's own 4dp*2 padding = ~93dp. Below this the
// text itself starts clipping regardless of device - confirmed on-device.
private val ALL_SOURCES_CARD_MIN_HEIGHT = 93.dp

// Ceiling on how big a card (and its cover) is allowed to grow even when a
// generous grant would allow more - keeps the cover from dominating the
// whole widget on an unusually tall grant.
private val ALL_SOURCES_CARD_MAX_HEIGHT = 145.dp

// Floor on the heatmap's own share - see the sizing comment inside
// AllSourcesWidgetContent for why this exists: fixed dp card/cover sizes
// tuned against one device's grant (a Boox Palma 2) left this at ~26dp - not
// visibly rendering at all - once tested against a Pixel 9 Pro's smaller
// grant for the same widget declaration. Guaranteeing a floor here, and
// deriving card height from *actual* available space (LocalSize.current)
// rather than a fixed constant, is what fixes that for any device rather
// than just the two seen so far.
private val ALL_SOURCES_HEATMAP_MIN_HEIGHT = 90.dp

// Fixed, not measured from LocalSize: this is a single line of 18sp text
// with no horizontal padding beyond the row's own 12dp, so its height
// doesn't vary with the widget's granted size the way the covers/heatmap do.
private val ALL_SOURCES_TITLE_ROW_HEIGHT = 26.dp

// Fixed: StatsWidgetContent's content (4-column row + always-two-line
// This Month/This Week row) is entirely text, so it doesn't need or benefit
// from growing with the widget the way the covers/heatmap do. 115dp (not
// 100dp): confirmed on a Pixel 9 Pro that even a value measured precisely
// on a Boox Palma 2 (100dp) still clipped "This Week" there (21px rendered
// vs its sibling "This Month" line's 43px) - text line-height apparently
// isn't identical across devices/Android versions even at the same density
// and font_scale, so a flat dp figure needs real margin, not just the
// minimum one device happened to need.
private val ALL_SOURCES_STATS_HEIGHT = 115.dp

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
    // Card (and cover) height is derived from the widget's *actual* granted
    // size rather than a fixed constant - a fixed value tuned to look good
    // on one device (a Boox Palma 2, which happened to grant a generous
    // amount of room) left the heatmap with essentially nothing on a Pixel 9
    // Pro, whose launcher grants meaningfully less for the same widget
    // declaration. Reserve the fixed-content sections (title, stats) and a
    // guaranteed heatmap floor first; whatever's left is split across the
    // book card(s), clamped to a sensible min/max so text never clips and
    // the cover never balloons past a reasonable size on an unusually tall
    // grant.
    val cardCount = if (mihonBook != null) 2 else 1
    val reserved = ALL_SOURCES_TITLE_ROW_HEIGHT + ALL_SOURCES_STATS_HEIGHT + ALL_SOURCES_HEATMAP_MIN_HEIGHT
    val availableForCards = (LocalSize.current.height - reserved)
        .coerceAtLeast(ALL_SOURCES_CARD_MIN_HEIGHT * cardCount)
    val cardHeight = (availableForCards / cardCount)
        .coerceIn(ALL_SOURCES_CARD_MIN_HEIGHT, ALL_SOURCES_CARD_MAX_HEIGHT)
    val coverHeight = cardHeight - ALL_SOURCES_CARD_PADDING * 2
    val coverWidth = coverHeight * 2f / 3f

    Column(
        // Semi-transparent white (see CurrentlyReadingWidgetContent's
        // WidgetBackgroundColor), applied once here rather than per embedded
        // section below - each of those always gets an explicit modifier
        // from this call site, so their own default background never
        // applies, avoiding a "grid of separately-boxed sections" look.
        modifier = GlanceModifier.fillMaxSize().background(WidgetBackgroundColor),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 0.dp),
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
            modifier = GlanceModifier.fillMaxWidth().height(cardHeight),
            coverWidth = coverWidth,
            coverHeight = coverHeight,
            contentPadding = ALL_SOURCES_CARD_PADDING,
            emptyStateMessage = bookEmptyStateMessage,
            // Bounds the title (and author) to one line so a long real title
            // can never wrap past what cardHeight accounts for - ellipsized
            // instead, which is the right trade-off in a card this compact
            // (two of these plus stats plus a heatmap all share one widget).
            titleAndAuthorMaxLines = 1,
        )
        if (mihonBook != null) {
            CurrentlyReadingWidgetContent(
                book = mihonBook,
                onClick = onMihonBookClick,
                modifier = GlanceModifier.fillMaxWidth().height(cardHeight),
                coverWidth = coverWidth,
                coverHeight = coverHeight,
                contentPadding = ALL_SOURCES_CARD_PADDING,
                titleAndAuthorMaxLines = 1,
            )
        }
        StatsWidgetContent(
            summary = summary,
            onClick = onOtherClick,
            // Fixed dp, not derived from LocalSize - see
            // ALL_SOURCES_STATS_HEIGHT's own comment: this section is all
            // text, sized once by precise on-device measurement, and doesn't
            // need or benefit from growing with the widget.
            modifier = GlanceModifier.fillMaxWidth().height(ALL_SOURCES_STATS_HEIGHT),
        )
        CalendarGridGraphWidgetContent(
            bitmap = heatmapBitmap,
            onClick = onOtherClick,
            // defaultWeight(): claims whatever's left after the fixed-size
            // siblings above (title, book card(s), stats) take their exact
            // height - guaranteed to be at least ALL_SOURCES_HEATMAP_MIN_HEIGHT
            // by construction, since that's reserved before cardHeight is
            // computed above, and can be considerably more on a generous
            // grant. A Column gives every fixed-size sibling its exact
            // height first regardless of what a weighted sibling does, so
            // this can only ever claim space the fixed siblings didn't need
            // - never take room away from them. Matches CombinedWidgetContent's
            // own graph section, which uses the same reasoning.
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}
