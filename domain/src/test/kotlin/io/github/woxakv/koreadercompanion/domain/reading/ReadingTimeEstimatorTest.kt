package io.github.woxakv.koreadercompanion.domain.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingTimeEstimatorTest {

    @Test
    fun `estimates remaining time from verified L'Attentat statistics data`() {
        // Real values from statistics.sqlite3: pages=364, MAX(page)=176,
        // total_read_time=8688s, total_read_pages=148 -> ~184min, close to
        // (not exact against) KOReader's own displayed "2h 59m".
        val seconds = ReadingTimeEstimator.estimateSecondsRemaining(
            totalPages = 364,
            currentPage = 176,
            totalReadTimeSeconds = 8688,
            totalReadPages = 148,
        )

        assertEquals(11036L, seconds)
    }

    @Test
    fun `returns null when there is no reading history`() {
        val seconds = ReadingTimeEstimator.estimateSecondsRemaining(
            totalPages = 364,
            currentPage = 0,
            totalReadTimeSeconds = 0,
            totalReadPages = 0,
        )

        assertNull(seconds)
    }

    @Test
    fun `clamps remaining pages to zero when current page exceeds total`() {
        val seconds = ReadingTimeEstimator.estimateSecondsRemaining(
            totalPages = 100,
            currentPage = 150,
            totalReadTimeSeconds = 1000,
            totalReadPages = 100,
        )

        assertEquals(0L, seconds)
    }
}
