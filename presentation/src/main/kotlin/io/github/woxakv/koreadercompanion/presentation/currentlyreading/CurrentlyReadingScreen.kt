package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.designsystem.components.BookCoverImage
import io.github.woxakv.koreadercompanion.designsystem.components.FilterChipRow
import io.github.woxakv.koreadercompanion.designsystem.components.ReadingProgressBar
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.presentation.onboarding.GrantAccessPrompt
import io.github.woxakv.koreadercompanion.presentation.util.openInKoreaderIntent

private const val TAG = "CurrentlyReadingScreen"

@Composable
fun CurrentlyReadingScreen(
    state: CurrentlyReadingUiState,
    mihonBook: CurrentBookUi?,
    statsSummary: ReadingStatsSummary?,
    heatmapBitmap: Bitmap?,
    statsSourceFilter: StatsSourceFilter,
    showFilter: Boolean,
    onFilterSelected: (StatsSourceFilter) -> Unit,
    onTreeSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Currently Reading",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Refresh",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onRefresh),
            )
        }
        Spacer(Modifier.height(16.dp))

        when (state) {
            is CurrentlyReadingUiState.Loading -> {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium)
            }

            is CurrentlyReadingUiState.Content -> {
                val book = state.book
                val context = LocalContext.current
                BookCard(
                    book = book,
                    onClick = {
                        val uri = book.openInKoreaderUri
                        if (uri != null) {
                            runCatching { context.startActivity(openInKoreaderIntent(context, uri)) }
                                .onFailure { error ->
                                    // Surfaced instead of swallowed: a silent
                                    // failure here just looks like "nothing
                                    // happens" with no way to diagnose why.
                                    Log.e(TAG, "Failed to open book in KOReader: $uri", error)
                                    Toast.makeText(
                                        context,
                                        "Couldn't open book: ${error.message}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                        }
                    },
                )
            }

            is CurrentlyReadingUiState.PermissionRequired -> {
                GrantAccessPrompt(reason = state.reason, onTreeSelected = onTreeSelected)
            }

            is CurrentlyReadingUiState.Error -> {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Mihon is a second, independent reading-data source - its card is
        // rendered (or entirely omitted, never a placeholder) regardless of
        // KOReader's state above, per plan.md's "two cards, one per app,
        // never merged" decision.
        mihonBook?.let { book ->
            Spacer(Modifier.height(16.dp))
            val context = LocalContext.current
            BookCard(
                book = book,
                onClick = {
                    val packageName = book.launchIntentPackage
                    val intent = packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    // Mihon uninstalled since the backup was read is an edge
                    // case, not a crash path - just no-op.
                    intent?.let { context.startActivity(it) }
                },
            )
        }

        if (showFilter) {
            Spacer(Modifier.height(24.dp))
            FilterChipRow(
                options = listOf(
                    StatsSourceFilter.ALL to "All",
                    StatsSourceFilter.KOREADER to "KOReader",
                    StatsSourceFilter.MIHON to "Mihon",
                ),
                selected = statsSourceFilter,
                onSelected = onFilterSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        StatsRowSection(summary = statsSummary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        ReadingActivitySection(bitmap = heatmapBitmap, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Shared layout for a single "currently reading" book, used for both the
 * KOReader and Mihon cards. Click behavior is intentionally a parameter
 * rather than hardcoded here: the two sources open in fundamentally
 * different ways (KOReader is a `content://` document-open, Mihon is a
 * plain package launch), so this composable stays agnostic to that.
 */
@Composable
private fun BookCard(
    book: CurrentBookUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cover on the left, content on the right - matches the
    // widget's layout for a consistent look across both.
    Row(modifier = modifier.clickable(onClick = onClick).fillMaxWidth()) {
        BookCoverImage(cover = book.coverBitmap, modifier = Modifier.width(120.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(12.dp))
            ReadingProgressBar(progress = book.progressFraction, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            book.timeRemainingLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(book.percentLabel, style = MaterialTheme.typography.bodyMedium)
            if (book.totalPages > 0) {
                Text(
                    "${book.unitLabel} ${book.currentPage} of ${book.totalPages}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            book.accessNote?.let { note ->
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
