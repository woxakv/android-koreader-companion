package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
fun ReadingActivitySection(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap == null) {
        Text(text = "No reading activity yet", modifier = modifier)
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Reading activity heatmap",
        // Locks the box to the bitmap's own aspect ratio instead of
        // stretching to fill an arbitrary height. Capped below full width:
        // filling the entire screen made the graph render larger than
        // intended.
        modifier = modifier
            .fillMaxWidth(0.75f)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
        // Fit, not FillBounds: with aspectRatio() already locking the box to
        // the bitmap's exact ratio, FillBounds and Fit render identically -
        // but Fit degrades safely (small letterbox) if that ever drifts,
        // where FillBounds would silently distort instead.
        contentScale = ContentScale.Fit,
    )
}
