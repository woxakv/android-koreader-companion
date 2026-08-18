package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.StatsGranularity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketDailyReadingStatsUseCaseTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 12)

    private val useCase = BucketDailyReadingStatsUseCase()

    // Month/year labels are derived via the same DateTimeFormatter patterns the
    // use case itself relies on, rather than hardcoded strings, so the tests
    // don't depend on which month-abbreviation variant (e.g. "Sep" vs "Sept")
    // the running JVM's locale data happens to produce.
    private val monthFormatter = DateTimeFormatter.ofPattern("MMM")
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMM yy")

    // --- DAY ---------------------------------------------------------------

    @Test
    fun `empty stats for DAY granularity yields 90 zero buckets, oldest first`() {
        val buckets = useCase(emptyList(), StatsGranularity.DAY, today)

        assertEquals(BucketDailyReadingStatsUseCase.DAY_GRANULARITY_DAYS, buckets.size)
        assertTrue(buckets.all { it.minutesRead == 0 && it.pagesRead == 0 })

        val dayFormatter = DateTimeFormatter.ofPattern("MMM d")
        val expectedOldest = today.minusDays((BucketDailyReadingStatsUseCase.DAY_GRANULARITY_DAYS - 1).toLong())
        assertEquals(expectedOldest.format(dayFormatter), buckets.first().label)
        assertEquals(today.format(dayFormatter), buckets.last().label)
    }

    @Test
    fun `a single day's stat lands in its own DAY bucket and every other bucket stays zero`() {
        val statDate = today.minusDays(5)
        val stats = listOf(DailyReadingStat(date = statDate, pagesRead = 10, minutesRead = 20))

        val buckets = useCase(stats, StatsGranularity.DAY, today)

        assertEquals(BucketDailyReadingStatsUseCase.DAY_GRANULARITY_DAYS, buckets.size)
        val expectedIndex = BucketDailyReadingStatsUseCase.DAY_GRANULARITY_DAYS - 1 - 5
        val target = buckets[expectedIndex]
        assertEquals(statDate.format(DateTimeFormatter.ofPattern("MMM d")), target.label)
        assertEquals(20, target.minutesRead)
        assertEquals(10, target.pagesRead)

        val others = buckets.filterIndexed { index, _ -> index != expectedIndex }
        assertTrue(others.all { it.minutesRead == 0 && it.pagesRead == 0 })
    }

    // --- MONTH ---------------------------------------------------------------

    @Test
    fun `empty stats for MONTH granularity yields 12 zero buckets, oldest first`() {
        val buckets = useCase(emptyList(), StatsGranularity.MONTH, today)

        assertEquals(12, buckets.size)
        assertTrue(buckets.all { it.minutesRead == 0 && it.pagesRead == 0 })
        // Window is Sep 2025 (today's month minus 11) .. Aug 2026 (today's month).
        assertEquals(YearMonth.of(2025, 9).format(monthYearFormatter), buckets.first().label)
        assertEquals(YearMonth.of(2026, 8).format(monthFormatter), buckets.last().label)
    }

    @Test
    fun `days spanning 2 calendar months produce 12 MONTH buckets with correct sums and ordering`() {
        val stats = listOf(
            DailyReadingStat(date = LocalDate.of(2026, 7, 15), pagesRead = 5, minutesRead = 10),
            DailyReadingStat(date = LocalDate.of(2026, 7, 20), pagesRead = 3, minutesRead = 6),
            DailyReadingStat(date = LocalDate.of(2026, 8, 1), pagesRead = 7, minutesRead = 14),
        )

        val buckets = useCase(stats, StatsGranularity.MONTH, today)

        assertEquals(12, buckets.size)
        val julyLabel = YearMonth.of(2026, 7).format(monthFormatter)
        val augustLabel = YearMonth.of(2026, 8).format(monthFormatter)
        val julyIndex = buckets.indexOfFirst { it.label == julyLabel }
        val augustIndex = buckets.indexOfFirst { it.label == augustLabel }
        assertTrue("July bucket must come before August bucket (oldest first)", julyIndex < augustIndex)

        val julyBucket = buckets[julyIndex]
        assertEquals(16, julyBucket.minutesRead)
        assertEquals(8, julyBucket.pagesRead)

        val augustBucket = buckets[augustIndex]
        assertEquals(14, augustBucket.minutesRead)
        assertEquals(7, augustBucket.pagesRead)

        val otherBuckets = buckets.filterIndexed { index, _ -> index != julyIndex && index != augustIndex }
        assertTrue(otherBuckets.all { it.minutesRead == 0 && it.pagesRead == 0 })
    }

    @Test
    fun `days spanning a year boundary disambiguate MONTH labels with 'MMM yy' and produce correct YEAR buckets`() {
        val stats = listOf(
            DailyReadingStat(date = LocalDate.of(2025, 12, 25), pagesRead = 4, minutesRead = 8),
            DailyReadingStat(date = LocalDate.of(2026, 1, 5), pagesRead = 6, minutesRead = 12),
        )

        val monthBuckets = useCase(stats, StatsGranularity.MONTH, today)

        assertEquals(12, monthBuckets.size)
        // First bucket of the window (Sep 2025) is always disambiguated, being the
        // first bucket of the first year seen.
        assertEquals(YearMonth.of(2025, 9).format(monthYearFormatter), monthBuckets.first().label)
        // December 2025 isn't the first bucket of its year within the window, so it
        // stays plain.
        val decLabel = YearMonth.of(2025, 12).format(monthFormatter)
        val decIndex = monthBuckets.indexOfFirst { it.label == decLabel }
        // January 2026 is the first bucket of the new calendar year within the
        // window, so it gets disambiguated as "Jan 26".
        val janLabel = YearMonth.of(2026, 1).format(monthYearFormatter)
        val janIndex = monthBuckets.indexOfFirst { it.label == janLabel }
        assertTrue("December bucket must come before January bucket (oldest first)", decIndex < janIndex)

        val decBucket = monthBuckets[decIndex]
        assertEquals(8, decBucket.minutesRead)
        assertEquals(4, decBucket.pagesRead)

        val janBucket = monthBuckets[janIndex]
        assertEquals(12, janBucket.minutesRead)
        assertEquals(6, janBucket.pagesRead)

        val yearBuckets = useCase(stats, StatsGranularity.YEAR, today)

        assertEquals(2, yearBuckets.size)
        assertEquals("2025", yearBuckets[0].label)
        assertEquals("2026", yearBuckets[1].label)
        assertEquals(8, yearBuckets[0].minutesRead)
        assertEquals(4, yearBuckets[0].pagesRead)
        assertEquals(12, yearBuckets[1].minutesRead)
        assertEquals(6, yearBuckets[1].pagesRead)
    }

    // --- YEAR ---------------------------------------------------------------

    @Test
    fun `empty stats for YEAR granularity yields a single bucket for today's year`() {
        val buckets = useCase(emptyList(), StatsGranularity.YEAR, today)

        assertEquals(1, buckets.size)
        assertEquals("2026", buckets.single().label)
        assertEquals(0, buckets.single().minutesRead)
        assertEquals(0, buckets.single().pagesRead)
    }

    @Test
    fun `YEAR buckets span every calendar year present through today's year, oldest first`() {
        val stats = listOf(
            DailyReadingStat(date = LocalDate.of(2024, 3, 10), pagesRead = 1, minutesRead = 2),
            DailyReadingStat(date = LocalDate.of(2025, 6, 1), pagesRead = 3, minutesRead = 4),
        )

        val buckets = useCase(stats, StatsGranularity.YEAR, today)

        assertEquals(3, buckets.size)
        assertEquals(listOf("2024", "2025", "2026"), buckets.map { it.label })
        assertEquals(2, buckets[0].minutesRead)
        assertEquals(1, buckets[0].pagesRead)
        assertEquals(4, buckets[1].minutesRead)
        assertEquals(3, buckets[1].pagesRead)
        assertEquals(0, buckets[2].minutesRead)
        assertEquals(0, buckets[2].pagesRead)
    }
}
