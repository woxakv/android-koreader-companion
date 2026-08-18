package io.github.woxakv.koreadercompanion.presentation.stats

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.designsystem.components.FilterChipRow
import io.github.woxakv.koreadercompanion.designsystem.theme.EinkColors
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.domain.model.StatsBucket
import io.github.woxakv.koreadercompanion.domain.model.StatsGranularity
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.ReadingActivitySection
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.StatsRowSection
import io.github.woxakv.koreadercompanion.presentation.widget.DEFAULT_STAT_ROWS
import io.github.woxakv.koreadercompanion.presentation.widget.StatItem
import io.github.woxakv.koreadercompanion.presentation.widget.label

// Duplicated from presentation/widget/StatItem.kt's formatDurationLabel
// rather than shared/imported, per this module's established convention of
// keeping small per-screen formatters local to their file.
private fun formatDurationLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Composable
fun StatsScreen(
    summary: ReadingStatsSummary?,
    buckets: List<StatsBucket>,
    sourceFilter: StatsSourceFilter,
    showSourceFilter: Boolean,
    granularity: StatsGranularity,
    heatmapBitmap: Bitmap?,
    heatmapMetric: HeatmapMetric,
    onSourceFilterSelected: (StatsSourceFilter) -> Unit,
    onGranularitySelected: (StatsGranularity) -> Unit,
    onHeatmapMetricSelected: (HeatmapMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Stats", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        if (showSourceFilter) {
            FilterChipRow(
                options = listOf(
                    StatsSourceFilter.ALL to "All",
                    StatsSourceFilter.KOREADER to "KOReader",
                    StatsSourceFilter.MIHON to "Mihon",
                ),
                selected = sourceFilter,
                onSelected = onSourceFilterSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }

        StatsRowSection(
            summary = summary,
            rows = DEFAULT_STAT_ROWS.take(1),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        PeriodStatsTable(summary = summary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        FilterChipRow(
            options = listOf(
                HeatmapMetric.PAGES to "Pages",
                HeatmapMetric.TIME to "Time",
            ),
            selected = heatmapMetric,
            onSelected = onHeatmapMetricSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        ReadingActivitySection(bitmap = heatmapBitmap, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        FilterChipRow(
            options = listOf(
                StatsGranularity.DAY to "Day",
                StatsGranularity.MONTH to "Month",
                StatsGranularity.YEAR to "Year",
            ),
            selected = granularity,
            onSelected = onGranularitySelected,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Text("Reading Time", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BarChart(
            buckets = buckets,
            valueSelector = { it.minutesRead },
            modifier = Modifier.fillMaxWidth(),
            valueFormatter = ::formatDurationLabel,
        )
        Spacer(Modifier.height(24.dp))

        Text("Pages Read", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BarChart(buckets = buckets, valueSelector = { it.pagesRead }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Bordered 3-column table (Period | Time | Pages) of the four always-shown
 * period totals (week/month/year/all time) - independent of the granularity
 * chips below it, per plan.md's "period cards vs. graph granularity"
 * decision. A header row labels the Time/Pages columns; below it, the
 * period label is left-aligned in column 1 while the time and pages values
 * are each right-aligned in their own column, split out of the combined
 * "Xh Ym · N pages" string so they line up as true columns instead of one
 * run-on value. All 3 columns are `weight(1f)`-bounded so no cell can
 * collide with another regardless of value length - the same technique
 * that fixed a genuine overlap bug in the original 2-column `titleLarge`
 * attempt (caught during step 8 device verification, which is also why
 * this table uses `bodyMedium` rather than a larger style).
 */
@Composable
private fun PeriodStatsTable(summary: ReadingStatsSummary?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.border(1.dp, EinkColors.Outline)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(text = "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "Time",
                style = MaterialTheme.typography.bodyMedium,
                color = EinkColors.Outline,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Pages",
                style = MaterialTheme.typography.bodyMedium,
                color = EinkColors.Outline,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        val rows = listOf(
            StatItem.THIS_WEEK to (summary?.minutesThisWeek to summary?.pagesThisWeek),
            StatItem.THIS_MONTH to (summary?.minutesThisMonth to summary?.pagesThisMonth),
            StatItem.THIS_YEAR to (summary?.minutesThisYear to summary?.pagesThisYear),
            StatItem.ALL_TIME to (summary?.minutesAllTime to summary?.pagesAllTime),
        )
        rows.forEach { (item, values) ->
            val (minutes, pages) = values
            HorizontalDivider(color = EinkColors.Outline, thickness = 1.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = item.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = minutes?.let(::formatDurationLabel) ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pages?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
