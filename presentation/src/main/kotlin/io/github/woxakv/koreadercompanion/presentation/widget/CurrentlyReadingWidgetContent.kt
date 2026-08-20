package io.github.woxakv.koreadercompanion.presentation.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentBookUi

// Strict black on white regardless of system theme, matching the e-ink
// default: no day/night variation to keep.
private val WidgetTextColor = ColorProvider(day = Color.Black, night = Color.Black)

// Semi-transparent white (80% opacity) so widget content stays readable
// against a busy home-screen wallpaper (confirmed necessary on a real
// device) without fully hiding it the way an opaque background would.
// Applied only via this composable's own default `modifier` - Combined/
// AllSourcesWidgetContent always pass an explicit modifier for each embedded
// book card, so this only ever takes effect for the standalone widget, and
// those two apply the same background once to their own outer container
// instead (avoiding a "grid of separately-boxed sections" look).
internal val WidgetBackgroundColor = Color(0xCCFFFFFF)

/**
 * Pure Glance content: no reference to MainActivity/KOReader package
 * lookups or any other Android component construction, so the tap action is
 * passed in rather than built here (only the app module has that context).
 */
@Composable
fun CurrentlyReadingWidgetContent(
    book: CurrentBookUi?,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize().background(WidgetBackgroundColor),
    // Default: scale the cover with however much room the launcher actually
    // granted, so hosts that hand out more than the declared minimum (250x100dp,
    // see currently_reading_widget_info.xml) don't leave dead whitespace. Only
    // meaningful when the widget sets SizeMode.Exact; the embedding widgets
    // (Combined / All Sources) pass explicit sizes and are unaffected.
    //
    // Each axis scales independently on purpose: this widget declares no
    // maxResizeHeight, so a height-only resize is the common case, and a shared
    // min()-derived factor would let an unchanged width suppress it entirely.
    // The box may then drift off 2:3, which is fine - ContentScale.Crop below
    // crops the source rather than distorting it.
    // Floor of 1.0 keeps the original tight-and-correct sizing on small hosts
    // (e.g. the Palma 2); 1.6 caps growth so the cover can't crowd out the text.
    coverWidth: Dp = 56.dp * (LocalSize.current.width / 250.dp).coerceIn(1.0f, 1.6f),
    coverHeight: Dp = 84.dp * (LocalSize.current.height / 100.dp).coerceIn(1.0f, 1.6f),
    contentPadding: Dp = 12.dp,
    emptyStateMessage: String = "Open KOReader Companion to set up",
    // Unbounded by default (existing behavior everywhere except AllSourcesWidgetContent,
    // which passes 1 to keep two stacked book cards inside a compact fixed
    // height - see its own call site for why).
    titleAndAuthorMaxLines: Int = Int.MAX_VALUE,
) {
    Row(
        // A background is back (see WidgetBackgroundColor) - semi-transparent
        // rather than the old opaque white, and applied via `modifier`
        // (which always fills the allotted bounds) rather than a separate
        // draw step, so it doesn't reintroduce the old gap/border artifact
        // that came from not fully filling the bounds.
        modifier = modifier
            .padding(contentPadding)
            .clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book == null) {
            Text(
                text = emptyStateMessage,
                style = TextStyle(color = WidgetTextColor, fontSize = 14.sp),
            )
            return@Row
        }

        book.coverBitmap?.let { cover ->
            Image(
                provider = ImageProvider(cover.asAndroidBitmap()),
                contentDescription = null,
                modifier = GlanceModifier.width(coverWidth).height(coverHeight),
                contentScale = ContentScale.Crop,
            )
            Spacer(GlanceModifier.width(12.dp))
        }

        Column(modifier = GlanceModifier.fillMaxHeight()) {
            Text(
                text = book.title,
                style = TextStyle(color = WidgetTextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                maxLines = titleAndAuthorMaxLines,
            )
            book.author?.let {
                Text(
                    text = it,
                    style = TextStyle(color = WidgetTextColor, fontSize = 13.sp),
                    maxLines = titleAndAuthorMaxLines,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = book.progressFraction,
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(day = Color.Black, night = Color.Black),
                backgroundColor = ColorProvider(day = Color(0xFFE0E0E0), night = Color(0xFFE0E0E0)),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = book.timeRemainingLabel?.let { "${book.percentLabel} · $it" } ?: book.percentLabel,
                style = TextStyle(color = WidgetTextColor, fontSize = 12.sp),
            )
            if (book.totalPages > 0) {
                Text(
                    text = "${book.unitLabel} ${book.currentPage} of ${book.totalPages}",
                    style = TextStyle(color = WidgetTextColor, fontSize = 12.sp),
                )
            }
        }
    }
}
