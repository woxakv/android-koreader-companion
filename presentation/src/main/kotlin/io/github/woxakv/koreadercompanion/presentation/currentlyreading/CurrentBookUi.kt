package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import androidx.compose.ui.graphics.ImageBitmap

data class CurrentBookUi(
    val title: String,
    val author: String?,
    val coverBitmap: ImageBitmap?,
    val progressFraction: Float,
    val percentLabel: String,
    val currentPage: Int,
    val totalPages: Int,
    /** "Page" for KOReader, "Chapter" for Mihon - see [io.github.woxakv.koreadercompanion.presentation.currentlyreading.toUi]. */
    val unitLabel: String,
    /** Null when no estimate is available (e.g. Mihon) - the card omits the line entirely rather than showing filler text. */
    val timeRemainingLabel: String?,
    val openInKoreaderUri: String?,
    val accessNote: String?,
    /**
     * Android package to launch (via `PackageManager.getLaunchIntentForPackage`)
     * for sources - Mihon - that open as a plain app launch rather than a
     * document open. Null for sources, KOReader included, where
     * [openInKoreaderUri] is the right open mechanism instead.
     */
    val launchIntentPackage: String?,
)
