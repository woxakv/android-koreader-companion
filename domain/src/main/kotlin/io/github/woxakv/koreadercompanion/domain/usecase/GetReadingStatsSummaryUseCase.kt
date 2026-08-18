package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.core.result.map
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary
import java.time.DayOfWeek
import java.time.LocalDate

class GetReadingStatsSummaryUseCase(
    private val getDailyReadingStats: GetDailyReadingStatsUseCase,
) {
    suspend operator fun invoke(today: LocalDate = LocalDate.now()): Try<ReadingStatsSummary> =
        getDailyReadingStats(days = LOOKBACK_DAYS).map { stats -> summarize(stats, today) }

    fun summarize(stats: List<DailyReadingStat>, today: LocalDate): ReadingStatsSummary {
        val byDate = stats.associateBy { it.date }
        val last7Total = (0..6).sumOf { offset -> byDate[today.minusDays(offset.toLong())]?.minutesRead ?: 0 }
        val avgMinutes = Math.round(last7Total / 7.0).toInt()
        val mostRecentActive = byDate.values.filter { it.pagesRead > 0 }.maxOfOrNull { it.date }
        var streak = 0
        var cursor = mostRecentActive
        while (cursor != null && (byDate[cursor]?.pagesRead ?: 0) > 0) {
            streak++
            cursor = cursor.minusDays(1)
        }
        val todayStat = byDate[today]

        val monthStart = today.withDayOfMonth(1)
        var minutesThisMonth = 0
        var pagesThisMonth = 0
        var cursorMonth = monthStart
        while (!cursorMonth.isAfter(today)) {
            minutesThisMonth += byDate[cursorMonth]?.minutesRead ?: 0
            pagesThisMonth += byDate[cursorMonth]?.pagesRead ?: 0
            cursorMonth = cursorMonth.plusDays(1)
        }

        val weekStart = today.with(DayOfWeek.MONDAY)
        var minutesThisWeek = 0
        var pagesThisWeek = 0
        var cursorWeek = weekStart
        while (!cursorWeek.isAfter(today)) {
            minutesThisWeek += byDate[cursorWeek]?.minutesRead ?: 0
            pagesThisWeek += byDate[cursorWeek]?.pagesRead ?: 0
            cursorWeek = cursorWeek.plusDays(1)
        }

        val yearStart = today.withDayOfYear(1)
        var minutesThisYear = 0
        var pagesThisYear = 0
        var cursorYear = yearStart
        while (!cursorYear.isAfter(today)) {
            minutesThisYear += byDate[cursorYear]?.minutesRead ?: 0
            pagesThisYear += byDate[cursorYear]?.pagesRead ?: 0
            cursorYear = cursorYear.plusDays(1)
        }

        val minutesAllTime = byDate.values.sumOf { it.minutesRead }
        val pagesAllTime = byDate.values.sumOf { it.pagesRead }

        return ReadingStatsSummary(
            avgMinutesLast7Days = avgMinutes,
            streakDays = streak,
            minutesToday = todayStat?.minutesRead ?: 0,
            pagesToday = todayStat?.pagesRead ?: 0,
            minutesThisMonth = minutesThisMonth,
            pagesThisMonth = pagesThisMonth,
            minutesThisWeek = minutesThisWeek,
            pagesThisWeek = pagesThisWeek,
            minutesThisYear = minutesThisYear,
            pagesThisYear = pagesThisYear,
            minutesAllTime = minutesAllTime,
            pagesAllTime = pagesAllTime,
        )
    }

    companion object {
        // Public so callers merging in a second data source (e.g. Mihon
        // integration) can request the same lookback window for their own
        // merged fetch, rather than duplicating the literal 365 elsewhere.
        const val LOOKBACK_DAYS = 365

        // Public so callers (e.g. the Stats page's one-off unbounded fetch)
        // reuse the same literal rather than duplicating it. ~10 years is a
        // pragmatic stand-in for "unbounded" without querying truly all rows.
        const val ALL_TIME_LOOKBACK_DAYS = 3650
    }
}
