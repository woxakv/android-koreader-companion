package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.StatsBucket
import io.github.woxakv.koreadercompanion.domain.model.StatsGranularity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class BucketDailyReadingStatsUseCase {
    operator fun invoke(
        stats: List<DailyReadingStat>,
        granularity: StatsGranularity,
        today: LocalDate,
    ): List<StatsBucket> = when (granularity) {
        StatsGranularity.DAY -> bucketByDay(stats, today)
        StatsGranularity.MONTH -> bucketByMonth(stats, today)
        StatsGranularity.YEAR -> bucketByYear(stats, today)
    }

    private fun bucketByDay(stats: List<DailyReadingStat>, today: LocalDate): List<StatsBucket> {
        val byDate = stats.groupBy { it.date }
        val startDate = today.minusDays((DAY_GRANULARITY_DAYS - 1).toLong())
        return (0 until DAY_GRANULARITY_DAYS).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val statsForDate = byDate[date].orEmpty()
            StatsBucket(
                label = date.format(DAY_LABEL_FORMATTER),
                minutesRead = statsForDate.sumOf { it.minutesRead },
                pagesRead = statsForDate.sumOf { it.pagesRead },
            )
        }
    }

    private fun bucketByMonth(stats: List<DailyReadingStat>, today: LocalDate): List<StatsBucket> {
        val byMonth = stats.groupBy { YearMonth.from(it.date) }
        val startMonth = YearMonth.from(today).minusMonths(11)
        val seenYears = mutableSetOf<Int>()
        return (0..11).map { offset ->
            val month = startMonth.plusMonths(offset.toLong())
            val statsForMonth = byMonth[month].orEmpty()
            // The first bucket of each calendar year present in the 12-month window
            // gets "MMM yy" to disambiguate a window that crosses a year boundary;
            // every other bucket just gets "MMM". Every bucket is labeled.
            val label = if (seenYears.add(month.year)) {
                month.format(MONTH_YEAR_LABEL_FORMATTER)
            } else {
                month.format(MONTH_LABEL_FORMATTER)
            }
            StatsBucket(
                label = label,
                minutesRead = statsForMonth.sumOf { it.minutesRead },
                pagesRead = statsForMonth.sumOf { it.pagesRead },
            )
        }
    }

    private fun bucketByYear(stats: List<DailyReadingStat>, today: LocalDate): List<StatsBucket> {
        val byYear = stats.groupBy { it.date.year }
        val startYear = stats.minOfOrNull { it.date.year } ?: today.year
        return (startYear..today.year).map { year ->
            val statsForYear = byYear[year].orEmpty()
            StatsBucket(
                label = year.toString(),
                minutesRead = statsForYear.sumOf { it.minutesRead },
                pagesRead = statsForYear.sumOf { it.pagesRead },
            )
        }
    }

    companion object {
        // Public so a later step's ViewModel can request exactly this many days
        // for DAY granularity without duplicating the literal (mirrors
        // GetReadingStatsSummaryUseCase.LOOKBACK_DAYS's pattern).
        const val DAY_GRANULARITY_DAYS = 90

        private val DAY_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        private val MONTH_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        private val MONTH_YEAR_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yy")
    }
}
