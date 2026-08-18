package io.github.woxakv.koreadercompanion.presentation.widget

import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGridGraphBucketingTest {

    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun `empty stats maps every date in window to bucket 0`() {
        val days = 30
        val result = assignBuckets(stats = emptyList(), days = days, today = today)

        val expectedDates = (0 until days).map { today.minusDays(it.toLong()) }.toSet()
        assertEquals(expectedDates, result.keys)
        result.values.forEach { bucket -> assertEquals(0, bucket) }
    }

    @Test
    fun `single nonzero day among the window is bucket 1`() {
        val activeDate = today.minusDays(3)
        val stats = listOf(DailyReadingStat(date = activeDate, pagesRead = 42, minutesRead = 30))

        val result = assignBuckets(stats = stats, days = 30, today = today)

        assertEquals(1, result[activeDate])
        // Every other day in the window has no activity and stays bucket 0.
        result.filterKeys { it != activeDate }.values.forEach { bucket -> assertEquals(0, bucket) }
    }

    @Test
    fun `multiple days with the same nonzero value all land in the same bucket without exception`() {
        val dateA = today.minusDays(1)
        val dateB = today.minusDays(2)
        val dateC = today.minusDays(3)
        val stats = listOf(
            DailyReadingStat(date = dateA, pagesRead = 10, minutesRead = 10),
            DailyReadingStat(date = dateB, pagesRead = 10, minutesRead = 10),
            DailyReadingStat(date = dateC, pagesRead = 10, minutesRead = 10),
        )

        val result = assignBuckets(stats = stats, days = 30, today = today)

        val bucketA = result.getValue(dateA)
        val bucketB = result.getValue(dateB)
        val bucketC = result.getValue(dateC)
        assertEquals(bucketA, bucketB)
        assertEquals(bucketA, bucketC)
        assertTrue(bucketA in 1..4)
    }

    @Test
    fun `buckets are non-decreasing as pages read increases`() {
        val date1 = today.minusDays(1)
        val date5 = today.minusDays(2)
        val date10 = today.minusDays(3)
        val date20 = today.minusDays(4)
        val date50 = today.minusDays(5)
        val stats = listOf(
            DailyReadingStat(date = date1, pagesRead = 1, minutesRead = 1),
            DailyReadingStat(date = date5, pagesRead = 5, minutesRead = 5),
            DailyReadingStat(date = date10, pagesRead = 10, minutesRead = 10),
            DailyReadingStat(date = date20, pagesRead = 20, minutesRead = 20),
            DailyReadingStat(date = date50, pagesRead = 50, minutesRead = 50),
        )

        val result = assignBuckets(stats = stats, days = 30, today = today)

        val orderedDates = listOf(date1, date5, date10, date20, date50)
        val orderedBuckets = orderedDates.map { result.getValue(it) }
        for (i in 0 until orderedBuckets.size - 1) {
            assertTrue(
                "bucket for day ${i + 1} (${orderedBuckets[i]}) should be <= bucket for next day (${orderedBuckets[i + 1]})",
                orderedBuckets[i] <= orderedBuckets[i + 1],
            )
        }
        // The lowest-pages day must never end up in a higher bucket than the highest-pages day.
        assertTrue(orderedBuckets.first() <= orderedBuckets.last())
    }

    @Test
    fun `an explicit minutesRead valueSelector produces different buckets than the pages-based default`() {
        val dateA = today.minusDays(1)
        val dateB = today.minusDays(2)
        val dateC = today.minusDays(3)
        val stats = listOf(
            // Pages ascending, minutes descending - the two selectors must
            // disagree on ordering, proving the selector is actually used.
            DailyReadingStat(date = dateA, pagesRead = 1, minutesRead = 90),
            DailyReadingStat(date = dateB, pagesRead = 10, minutesRead = 45),
            DailyReadingStat(date = dateC, pagesRead = 20, minutesRead = 5),
        )

        val pagesResult = assignBuckets(stats = stats, days = 30, today = today)
        val minutesResult = assignBuckets(
            stats = stats,
            days = 30,
            today = today,
            valueSelector = DailyReadingStat::minutesRead,
        )

        // Under pages, dateC (20 pages) ranks highest; under minutes, dateA
        // (90 minutes) ranks highest - the bucket assignments must diverge.
        assertTrue(pagesResult.getValue(dateA) < pagesResult.getValue(dateC))
        assertTrue(minutesResult.getValue(dateA) > minutesResult.getValue(dateC))
        assertTrue(pagesResult != minutesResult)
    }

    @Test
    fun `days outside the window are never present as keys`() {
        val days = 7
        val outsideDate = today.minusDays(days.toLong()) // one day before the window starts
        val stats = listOf(DailyReadingStat(date = outsideDate, pagesRead = 99, minutesRead = 99))

        val result = assignBuckets(stats = stats, days = days, today = today)

        assertFalse(result.containsKey(outsideDate))
        assertEquals(days, result.size)
        result.keys.forEach { d ->
            assertFalse(d.isBefore(today.minusDays((days - 1).toLong())))
            assertFalse(d.isAfter(today))
        }
    }
}
