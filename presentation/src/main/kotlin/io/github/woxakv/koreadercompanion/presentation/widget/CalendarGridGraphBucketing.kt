package io.github.woxakv.koreadercompanion.presentation.widget

import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import java.time.LocalDate

const val BUCKET_COUNT = 5 // 0 = no activity, 1..4 = quartiles of nonzero days

/**
 * Assigns each day in the `days`-sized window ending at [today] a shading
 * bucket in `0..4`, relative to the user's own history within that window
 * (quantile-style): the nonzero-activity days are split into 4 buckets by
 * pages-read value. Zero-activity days always land in bucket 0 regardless of
 * how the quantiles fall.
 */
fun assignBuckets(
    stats: List<DailyReadingStat>,
    days: Int,
    today: LocalDate,
    valueSelector: (DailyReadingStat) -> Int = DailyReadingStat::pagesRead,
): Map<LocalDate, Int> {
    val byDate = stats.associateBy { it.date }
    val allDates = (0 until days).map { today.minusDays(it.toLong()) }
    val nonZeroValues = allDates.mapNotNull { d -> byDate[d]?.let(valueSelector)?.takeIf { it > 0 } }.sorted()
    if (nonZeroValues.isEmpty()) return allDates.associateWith { 0 }
    val thresholds = listOf(0.25, 0.5, 0.75).map { p ->
        nonZeroValues[(p * (nonZeroValues.size - 1)).toInt()]
    }
    return allDates.associateWith { d ->
        val value = byDate[d]?.let(valueSelector) ?: 0
        when {
            value <= 0 -> 0
            value <= thresholds[0] -> 1
            value <= thresholds[1] -> 2
            value <= thresholds[2] -> 3
            else -> 4
        }
    }
}
