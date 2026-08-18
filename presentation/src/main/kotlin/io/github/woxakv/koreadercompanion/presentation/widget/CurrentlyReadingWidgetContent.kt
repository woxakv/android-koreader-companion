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
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
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

/**
 * Pure Glance content: no reference to MainActivity/KOReader package
 * lookups or any other Android component construction, so the tap action is
 * passed in rather than built here (only the app module has that context).
 */
@Composable
fun CurrentlyReadingWidgetContent(
    book: CurrentBookUi?,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
    coverWidth: Dp = 56.dp,
    coverHeight: Dp = 84.dp,
    contentPadding: Dp = 12.dp,
    emptyStateMessage: String = "Open KOReader Companion to set up",
) {
    Row(
        // No background of our own is drawn here anymore - the launcher/
        // system background shows through instead, since `modifier` always
        // fills the allotted bounds regardless of background color (the
        // gap/border artifact the old white background worked around was
        // caused by not fully filling the bounds, not by the background
        // color itself). This has not yet been visually confirmed on-device;
        // if a border reappears around the widget, revisit this.
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
            )
            book.author?.let {
                Text(text = it, style = TextStyle(color = WidgetTextColor, fontSize = 13.sp))
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
