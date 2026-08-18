package io.github.woxakv.koreadercompanion.presentation.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarChartTest {

    @Test
    fun `selectLabeledIndices labels every bar when they all fit`() {
        val bucketCount = 12

        val indices = selectLabeledIndices(
            bucketCount = bucketCount,
            availableWidthPx = 1000f,
            labelWidthPx = 40f,
        )

        assertEquals((0 until bucketCount).toSet(), indices)
    }

    @Test
    fun `selectLabeledIndices skips evenly and keeps first and last when labels don't fit`() {
        val bucketCount = 90

        val indices = selectLabeledIndices(
            bucketCount = bucketCount,
            availableWidthPx = 1000f,
            labelWidthPx = 80f,
        )

        // Not every bar got a label - that's the whole point of skipping.
        assertTrue(indices.size < bucketCount)

        // The chart's edges must always be labeled, or it looks broken.
        assertTrue(0 in indices)
        assertTrue((bucketCount - 1) in indices)

        // Evenly spaced: every gap between consecutive kept indices is the
        // same fixed step, except possibly the final gap into the
        // explicitly-appended last index, which can be shorter.
        val sorted = indices.sorted()
        val gaps = sorted.zipWithNext { a, b -> b - a }
        val regularGaps = gaps.dropLast(1)
        assertTrue(regularGaps.isNotEmpty())
        assertTrue(regularGaps.all { it == regularGaps.first() })
    }

    @Test
    fun `selectLabeledIndices drops a regular index that would collide with the forced last index`() {
        // Caught on a real device: bucketCount=90 with a step that doesn't
        // evenly divide it left the last regular index (84) only 5 apart
        // from the forced final index (89) - well under the step's own
        // 7-index spacing guarantee, so their labels visually overlapped
        // ("Aug 13" drawn on top of "Aug 18"). The crowded regular index
        // must be dropped in favor of the final index alone.
        val bucketCount = 90

        val indices = selectLabeledIndices(
            bucketCount = bucketCount,
            availableWidthPx = 1000f,
            labelWidthPx = 76f,
        )

        assertTrue(0 in indices)
        assertTrue(89 in indices)
        assertTrue(84 !in indices)
    }

    @Test
    fun `selectLabeledIndices with a single bucket labels it`() {
        val indices = selectLabeledIndices(
            bucketCount = 1,
            availableWidthPx = 1000f,
            labelWidthPx = 500f,
        )

        assertEquals(setOf(0), indices)
    }

    @Test
    fun `selectLabeledIndices with no buckets returns no labels`() {
        val indices = selectLabeledIndices(
            bucketCount = 0,
            availableWidthPx = 1000f,
            labelWidthPx = 40f,
        )

        assertTrue(indices.isEmpty())
    }

    @Test
    fun `gridlineValues returns 0, half, and max of maxValue`() {
        val values = gridlineValues(120)

        assertEquals(listOf(0, 60, 120), values)
    }

    @Test
    fun `clampLabelX leaves a label that already fits unchanged`() {
        val x = clampLabelX(centeredX = 100f, labelWidthPx = 40f, minX = 60f, maxX = 500f)

        assertEquals(100f, x)
    }

    @Test
    fun `clampLabelX pushes a label that would overflow left to minX`() {
        val x = clampLabelX(centeredX = 10f, labelWidthPx = 40f, minX = 60f, maxX = 500f)

        assertEquals(60f, x)
    }

    @Test
    fun `clampLabelX pushes a label that would overflow right so it ends exactly at maxX`() {
        val labelWidthPx = 40f
        val maxX = 500f

        val x = clampLabelX(centeredX = 480f, labelWidthPx = labelWidthPx, minX = 60f, maxX = maxX)

        assertEquals(maxX, x + labelWidthPx)
    }

    @Test
    fun `dropOverlappingLabels drops an interior label that collides with a clamped first label`() {
        // Reproduces the exact on-device bug: the first bucket's label
        // clamps hard right (from a negative centered position) and would
        // overlap the very next kept label if both were drawn.
        val first = LabelPlacement(index = 0, x = 60f, width = 60f, barCenterX = 20f)
        val collidingSecond = LabelPlacement(index = 8, x = 90f, width = 60f, barCenterX = 100f)
        val third = LabelPlacement(index = 16, x = 200f, width = 60f, barCenterX = 220f)
        val last = LabelPlacement(index = 89, x = 700f, width = 60f, barCenterX = 760f)

        val kept = dropOverlappingLabels(listOf(first, collidingSecond, third, last))

        assertEquals(listOf(0, 16, 89), kept.map { it.index })
    }

    @Test
    fun `dropOverlappingLabels keeps everything when nothing overlaps`() {
        val placements = listOf(
            LabelPlacement(index = 0, x = 60f, width = 40f, barCenterX = 20f),
            LabelPlacement(index = 8, x = 200f, width = 40f, barCenterX = 220f),
            LabelPlacement(index = 89, x = 700f, width = 40f, barCenterX = 760f),
        )

        val kept = dropOverlappingLabels(placements)

        assertEquals(listOf(0, 8, 89), kept.map { it.index })
    }

    @Test
    fun `dropOverlappingLabels always keeps first and last even with only two placements`() {
        val placements = listOf(
            LabelPlacement(index = 0, x = 60f, width = 40f, barCenterX = 20f),
            LabelPlacement(index = 89, x = 90f, width = 40f, barCenterX = 760f),
        )

        val kept = dropOverlappingLabels(placements)

        assertEquals(listOf(0, 89), kept.map { it.index })
    }
}
