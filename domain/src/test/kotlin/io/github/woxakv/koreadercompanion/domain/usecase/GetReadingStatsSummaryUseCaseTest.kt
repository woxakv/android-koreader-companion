package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeReadingStatsRepository : ReadingStatsRepository {
    override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> =
        Try.Success(emptyList())
}

class GetReadingStatsSummaryUseCaseTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private val useCase = GetReadingStatsSummaryUseCase(
        GetDailyReadingStatsUseCase(FakeReadingStatsRepository()),
    )

    @Test
    fun `empty history yields all zeros`() {
        val summary = useCase.summarize(emptyList(), today)

        assertEquals(0, summary.avgMinutesLast7Days)
        assertEquals(0, summary.streakDays)
        assertEquals(0, summary.minutesToday)
        assertEquals(0, summary.pagesToday)
        assertEquals(0, summary.minutesThisMonth)
        assertEquals(0, summary.pagesThisMonth)
        assertEquals(0, summary.minutesThisWeek)
        assertEquals(0, summary.pagesThisWeek)
    }

    @Test
    fun `single active day equal to today gives a streak of 1`() {
        val stats = listOf(DailyReadingStat(date = today, pagesRead = 10, minutesRead = 20))

        val summary = useCase.summarize(stats, today)

        assertEquals(1, summary.streakDays)
        assertEquals(20, summary.minutesThisMonth)
        assertEquals(10, summary.pagesThisMonth)
        assertEquals(20, summary.minutesThisWeek)
        assertEquals(10, summary.pagesThisWeek)
    }

    @Test
    fun `single active day in the past with today absent still gives a streak of 1`() {
        // today.minusDays(3) = 2026-08-09 (Sunday): within this month (Aug 1-12) but
        // before this week's Monday (Aug 10), so it counts toward the month, not the week.
        val stats = listOf(DailyReadingStat(date = today.minusDays(3), pagesRead = 5, minutesRead = 10))

        val summary = useCase.summarize(stats, today)

        assertEquals(1, summary.streakDays)
        assertEquals(10, summary.minutesThisMonth)
        assertEquals(5, summary.pagesThisMonth)
        assertEquals(0, summary.minutesThisWeek)
        assertEquals(0, summary.pagesThisWeek)
    }

    @Test
    fun `a gap between active days breaks the streak at 1`() {
        // today.minusDays(1) = Aug 11 (within this week); today.minusDays(3) = Aug 9
        // (within this month but before this week's Monday of Aug 10).
        val stats = listOf(
            DailyReadingStat(date = today.minusDays(1), pagesRead = 5, minutesRead = 10),
            DailyReadingStat(date = today.minusDays(3), pagesRead = 5, minutesRead = 10),
        )

        val summary = useCase.summarize(stats, today)

        assertEquals(1, summary.streakDays)
        assertEquals(20, summary.minutesThisMonth)
        assertEquals(10, summary.pagesThisMonth)
        assertEquals(10, summary.minutesThisWeek)
        assertEquals(5, summary.pagesThisWeek)
    }

    @Test
    fun `five consecutive active days including today give a streak of 5`() {
        val stats = (0..4).map { offset ->
            DailyReadingStat(date = today.minusDays(offset.toLong()), pagesRead = 5, minutesRead = 10)
        }

        val summary = useCase.summarize(stats, today)

        assertEquals(5, summary.streakDays)
        // All 5 days (Aug 8-12) fall within this month (Aug 1-12).
        assertEquals(50, summary.minutesThisMonth)
        assertEquals(25, summary.pagesThisMonth)
        // Only Aug 10-12 fall within this week (Monday-start).
        assertEquals(30, summary.minutesThisWeek)
        assertEquals(15, summary.pagesThisWeek)
    }

    @Test
    fun `streak holds through an empty today instead of resetting to zero`() {
        val stats = (1..4).map { offset ->
            DailyReadingStat(date = today.minusDays(offset.toLong()), pagesRead = 5, minutesRead = 10)
        }

        val summary = useCase.summarize(stats, today)

        assertEquals(4, summary.streakDays)
        // Aug 8-11 all fall within this month; today (Aug 12) is absent -> contributes 0.
        assertEquals(40, summary.minutesThisMonth)
        assertEquals(20, summary.pagesThisMonth)
        // Only Aug 10-11 fall within this week; today (Aug 12) is absent -> contributes 0.
        assertEquals(20, summary.minutesThisWeek)
        assertEquals(10, summary.pagesThisWeek)
    }

    @Test
    fun `7-day average ignores days outside the window and treats missing days as zero`() {
        val stats = listOf(
            DailyReadingStat(date = today, pagesRead = 14, minutesRead = 20),
            DailyReadingStat(date = today.minusDays(1), pagesRead = 0, minutesRead = 5),
            DailyReadingStat(date = today.minusDays(2), pagesRead = 21, minutesRead = 30),
            // today.minusDays(3) missing entirely -> treated as 0 minutes.
            DailyReadingStat(date = today.minusDays(4), pagesRead = 7, minutesRead = 15),
            DailyReadingStat(date = today.minusDays(5), pagesRead = 0, minutesRead = 5),
            DailyReadingStat(date = today.minusDays(6), pagesRead = 7, minutesRead = 15),
            // Outside the 7-day window: must not affect the average, but it IS within
            // this month (Aug 5), so it must still count toward minutesThisMonth.
            DailyReadingStat(date = today.minusDays(7), pagesRead = 100, minutesRead = 200),
        )

        val summary = useCase.summarize(stats, today)

        // Total = 20 + 5 + 30 + 0 + 15 + 5 + 15 = 90; 90 / 7 = 12.857... -> 13
        assertEquals(13, summary.avgMinutesLast7Days)
        // Month (Aug 1-12) includes every stat above, including the Aug 5 outlier.
        // 20 + 5 + 30 + 0 + 15 + 5 + 15 + 200 = 290; pages: 14+0+21+0+7+0+7+100 = 149
        assertEquals(290, summary.minutesThisMonth)
        assertEquals(149, summary.pagesThisMonth)
        // Week (Aug 10-12) only includes today, today-1 (Aug 11), today-2 (Aug 10).
        assertEquals(55, summary.minutesThisWeek)
        assertEquals(35, summary.pagesThisWeek)
    }

    @Test
    fun `today's stat is reported directly when present`() {
        val stats = listOf(DailyReadingStat(date = today, pagesRead = 12, minutesRead = 34))

        val summary = useCase.summarize(stats, today)

        assertEquals(34, summary.minutesToday)
        assertEquals(12, summary.pagesToday)
        assertEquals(34, summary.minutesThisMonth)
        assertEquals(12, summary.pagesThisMonth)
        assertEquals(34, summary.minutesThisWeek)
        assertEquals(12, summary.pagesThisWeek)
    }

    @Test
    fun `this month and this week are partial sums up to today, not full-period totals`() {
        val stats = listOf(
            DailyReadingStat(date = today, pagesRead = 10, minutesRead = 15), // Aug 12
            DailyReadingStat(date = today.minusDays(2), pagesRead = 8, minutesRead = 12), // Aug 10 (Monday)
            DailyReadingStat(date = today.minusDays(3), pagesRead = 6, minutesRead = 9), // Aug 9 (Sunday)
            // Outside this month entirely.
            DailyReadingStat(date = today.minusDays(20), pagesRead = 100, minutesRead = 200), // Jul 23
        )

        val summary = useCase.summarize(stats, today)

        // Month (Aug 1-12): Aug 12 + Aug 10 + Aug 9, Jul 23 excluded.
        assertEquals(36, summary.minutesThisMonth)
        assertEquals(24, summary.pagesThisMonth)
        // Week (Aug 10-12): Aug 12 + Aug 10, Aug 9 excluded (before this week's Monday).
        assertEquals(27, summary.minutesThisWeek)
        assertEquals(18, summary.pagesThisWeek)
    }

    @Test
    fun `month sum is just today's stat when today is the 1st of the month`() {
        val firstOfMonth = LocalDate.of(2026, 9, 1) // Tuesday
        val stats = listOf(
            DailyReadingStat(date = firstOfMonth, pagesRead = 8, minutesRead = 16),
            // Previous month, and before this week's Monday (Aug 31) -> excluded from both.
            DailyReadingStat(date = firstOfMonth.minusDays(2), pagesRead = 50, minutesRead = 90),
        )

        val summary = useCase.summarize(stats, firstOfMonth)

        assertEquals(16, summary.minutesThisMonth)
        assertEquals(8, summary.pagesThisMonth)
        assertEquals(16, summary.minutesThisWeek)
        assertEquals(8, summary.pagesThisWeek)
    }

    @Test
    fun `week sum is just today's stat when today is a Monday`() {
        val monday = LocalDate.of(2026, 8, 10)
        val stats = listOf(
            DailyReadingStat(date = monday, pagesRead = 6, minutesRead = 12),
            // Sunday: previous week, but still within this month -> counts toward the
            // month sum only.
            DailyReadingStat(date = monday.minusDays(1), pagesRead = 40, minutesRead = 80),
        )

        val summary = useCase.summarize(stats, monday)

        assertEquals(12, summary.minutesThisWeek)
        assertEquals(6, summary.pagesThisWeek)
        assertEquals(92, summary.minutesThisMonth)
        assertEquals(46, summary.pagesThisMonth)
    }
}
