package io.github.woxakv.koreadercompanion.presentation.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarGridGraphRendererTest {

    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun `render returns a bitmap sized per the documented formula`() {
        val days = 30
        val cellSizePx = 20
        val cellGapPx = 4
        val cellGapHorizontalPx = 4
        val buckets = assignBuckets(
            stats = listOf(
                io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat(
                    date = today,
                    pagesRead = 10,
                    minutesRead = 10,
                ),
            ),
            days = days,
            today = today,
        )

        val bitmap = render(
            buckets = buckets,
            days = days,
            today = today,
            cellSizePx = cellSizePx,
            cellGapPx = cellGapPx,
            cellGapHorizontalPx = cellGapHorizontalPx,
        )

        val expectedColumns = kotlin.math.ceil(days / 7.0).toInt()
        assertEquals(LEFT_LABEL_WIDTH_PX + expectedColumns * (cellSizePx + cellGapHorizontalPx), bitmap.width)
        assertEquals(TOP_LABEL_HEIGHT_PX + 7 * (cellSizePx + cellGapPx), bitmap.height)
    }

    @Test
    fun `render with an all-zero bucket map does not throw and is correctly sized`() {
        val days = 91
        val cellSizePx = 12
        val cellGapPx = 2
        val cellGapHorizontalPx = 2
        val buckets = assignBuckets(stats = emptyList(), days = days, today = today)

        val bitmap = render(
            buckets = buckets,
            days = days,
            today = today,
            cellSizePx = cellSizePx,
            cellGapPx = cellGapPx,
            cellGapHorizontalPx = cellGapHorizontalPx,
        )

        val expectedColumns = kotlin.math.ceil(days / 7.0).toInt()
        assertEquals(LEFT_LABEL_WIDTH_PX + expectedColumns * (cellSizePx + cellGapHorizontalPx), bitmap.width)
        assertEquals(TOP_LABEL_HEIGHT_PX + 7 * (cellSizePx + cellGapPx), bitmap.height)
    }

    @Test
    fun `render with an empty bucket map still returns a correctly sized bitmap`() {
        val days = 7
        val cellSizePx = 16
        val cellGapPx = 3
        val cellGapHorizontalPx = 3

        val bitmap = render(
            buckets = emptyMap(),
            days = days,
            today = today,
            cellSizePx = cellSizePx,
            cellGapPx = cellGapPx,
            cellGapHorizontalPx = cellGapHorizontalPx,
        )

        assertEquals(LEFT_LABEL_WIDTH_PX + 1 * (cellSizePx + cellGapHorizontalPx), bitmap.width)
        assertEquals(TOP_LABEL_HEIGHT_PX + 7 * (cellSizePx + cellGapPx), bitmap.height)
    }

    @Test
    fun `columnIndexFor places today in the last column`() {
        val thisMonday = LocalDate.of(2026, 8, 10)
        val columns = 13

        assertEquals(12, columnIndexFor(today, thisMonday, columns))
    }

    @Test
    fun `columnIndexFor places this week's Monday in the same column as today`() {
        val thisMonday = LocalDate.of(2026, 8, 10)
        val columns = 13

        assertEquals(12, columnIndexFor(thisMonday, thisMonday, columns))
    }

    @Test
    fun `columnIndexFor places last Sunday in the previous column, not today's`() {
        val thisMonday = LocalDate.of(2026, 8, 10)
        val columns = 13

        assertEquals(11, columnIndexFor(LocalDate.of(2026, 8, 9), thisMonday, columns))
    }

    @Test
    fun `columnIndexFor places 12 weeks back in the first column`() {
        val thisMonday = LocalDate.of(2026, 8, 10)
        val columns = 13

        assertEquals(0, columnIndexFor(LocalDate.of(2026, 5, 18), thisMonday, columns))
    }
}
