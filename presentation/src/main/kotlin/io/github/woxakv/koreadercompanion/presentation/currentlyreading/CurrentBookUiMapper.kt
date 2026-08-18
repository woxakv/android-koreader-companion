package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook

fun CurrentBook.toUi(unitLabel: String = "Page"): CurrentBookUi = CurrentBookUi(
    title = title,
    author = author,
    coverBitmap = coverImageBytes?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    },
    progressFraction = progress.percentRead,
    percentLabel = formatPercentLabel(progress.percentRead),
    currentPage = progress.currentPage,
    totalPages = progress.totalPages,
    unitLabel = unitLabel,
    timeRemainingLabel = estimatedSecondsRemaining?.let { formatRemainingLabel(it) },
    openInKoreaderUri = bookContentUriString,
    accessNote = bookAccessNote,
    launchIntentPackage = launchIntentPackage,
)

internal fun formatPercentLabel(percentRead: Float): String = "${(percentRead * 100).toInt()}% Read"

internal fun formatRemainingLabel(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m remaining" else "${minutes}m remaining"
}
