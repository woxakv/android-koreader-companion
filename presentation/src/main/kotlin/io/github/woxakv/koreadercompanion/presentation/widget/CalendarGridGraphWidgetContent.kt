package io.github.woxakv.koreadercompanion.presentation.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

// Strict black on white regardless of system theme, matching the e-ink
// default: no day/night variation to keep.
private val WidgetTextColor = ColorProvider(day = Color.Black, night = Color.Black)

// Kept at 12dp (matching the book/stats rows' own padding) rather than
// trimmed for the combined widget: this padding is breathing room around
// the bitmap within whatever box this section is given, not part of the
// column's fixed-height budget - the graph already claims 100% of the
// column's leftover space via defaultWeight() in CombinedWidgetContent, so
// shrinking this doesn't free space for other sections, it only changes how
// close the heatmap sits to its own box edges. Left as-is for visual
// consistency with the rest of the widget.
private val CONTENT_PADDING = 12.dp

/**
 * Pure Glance content: no reference to MainActivity/KOReader package lookups
 * or any other Android component construction, so the tap action is passed
 * in rather than built here (only the app module has that context).
 */
@Composable
fun CalendarGridGraphWidgetContent(
    bitmap: Bitmap?,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
) {
    Box(
        // No background of our own is drawn here anymore - the launcher/
        // system background shows through instead, since `modifier` always
        // fills the allotted bounds regardless of background color (the
        // gap/border artifact the old white background worked around was
        // caused by not fully filling the bounds, not by the background
        // color itself). This has not yet been visually confirmed on-device;
        // if a border reappears around the widget, revisit this.
        modifier = modifier
            .padding(CONTENT_PADDING)
            .clickable(onClick),
    ) {
        if (bitmap == null) {
            Text(
                text = "No reading data yet",
                style = TextStyle(color = WidgetTextColor, fontSize = 14.sp),
            )
            return@Box
        }

        // Fit fills the whole granted box and scales the bitmap to whatever
        // fits inside it, preserving aspect ratio - so it adapts safely to
        // any box size the widget ends up with, in either direction: a
        // taller box just letterboxes, a shorter one shrinks the image to
        // match, and neither case distorts or clips it. That's what makes
        // it safe to size these widgets purely from the outside (XML
        // min/max height, or the graph section's height in the combined
        // widget) without needing to compute an exact box here.
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Reading activity heatmap",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
