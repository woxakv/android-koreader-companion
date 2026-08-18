package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import io.github.woxakv.koreadercompanion.presentation.widget.DEFAULT_STAT_ROWS
import io.github.woxakv.koreadercompanion.presentation.widget.StatItem
import io.github.woxakv.koreadercompanion.presentation.widget.label
import io.github.woxakv.koreadercompanion.presentation.widget.value

@Composable
fun StatsRowSection(
    summary: ReadingStatsSummary?,
    rows: List<List<StatItem>> = DEFAULT_STAT_ROWS,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            if (row.size <= 2) {
                // This Month/This Week: one centered line combining both
                // stats (regular-weight label, bold value), matching the
                // widget's final row-2 layout - not a row of separate
                // label-above-value tiles like row 1.
                Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    row.forEachIndexed { itemIndex, item ->
                        Text(text = "${item.label()}: ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = summary?.let { item.value(it) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (itemIndex != row.lastIndex) {
                            Text(text = "   ", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = item.label(), style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = summary?.let { item.value(it) } ?: "—",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
