package io.github.woxakv.koreadercompanion.presentation.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.designsystem.theme.EinkColors
import io.github.woxakv.koreadercompanion.domain.model.StatsBucket
import kotlin.math.ceil

private val CHART_HEIGHT_DP = 200.dp
private const val LABEL_AREA_HEIGHT_PX = 40f
private const val LABEL_TEXT_SIZE_PX = 22f
private const val BAR_GAP_FRACTION = 0.2f

// Reserved left-side column for y-axis value labels, matching the heatmap's
// LEFT_LABEL_WIDTH_PX convention (CalendarGridGraphRenderer.kt).
private const val Y_AXIS_LABEL_WIDTH_PX = 60f
private const val Y_AXIS_LABEL_MARGIN_PX = 8f
private const val TICK_HEIGHT_PX = 6f

// Headroom above the tallest bar (and the 100% gridline) so the chart's
// tallest bar never touches the very top edge of the canvas.
private const val TOP_PADDING_PX = 16f

/**
 * One bar per [bucket][StatsBucket], y-axis auto-scaled to the max value in
 * [buckets] (per [valueSelector]). Used for both the reading-time and
 * pages-read graphs on the stats screen — which value each renders is
 * decided entirely by [valueSelector].
 *
 * Drawn directly via [androidx.compose.foundation.Canvas] — unlike the
 * calendar heatmap ([io.github.woxakv.koreadercompanion.presentation.widget.CalendarGridGraphRenderer]),
 * which renders to a [android.graphics.Bitmap] only because Glance widgets
 * require it; a normal Compose screen has no such constraint.
 */
@Composable
fun BarChart(
    buckets: List<StatsBucket>,
    valueSelector: (StatsBucket) -> Int,
    modifier: Modifier = Modifier,
    emptyStateMessage: String = "No reading data yet",
    valueFormatter: (Int) -> String = { it.toString() },
) {
    if (buckets.isEmpty()) {
        Text(text = emptyStateMessage, modifier = modifier)
        return
    }

    val maxValue = buckets.maxOfOrNull(valueSelector) ?: 0
    val barColor = EinkColors.OnBackground
    val labelColor = EinkColors.Outline
    // Muted grey - same as x-axis label text and the period table's own
    // border - so gridlines and ticks read as background scaffolding
    // rather than data, while still being visible on a real device
    // (EinkColors.ProgressTrack was too light to see on-device).
    val gridColor = EinkColors.Outline

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP),
    ) {
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = LABEL_TEXT_SIZE_PX
        }

        val chartHeightPx = (size.height - LABEL_AREA_HEIGHT_PX - TOP_PADDING_PX).coerceAtLeast(0f)
        val gridlineLabels = gridlineValues(maxValue).map(valueFormatter)
        // Sized from the actual widest y-axis label, not a fixed guess - a
        // fixed 60px column clipped longer values like "14h 45m" on-device
        // (found in this plan's own device verification). Floored at
        // Y_AXIS_LABEL_WIDTH_PX so very short labels ("0") still get
        // reasonable breathing room.
        val yAxisLabelWidthPx = (gridlineLabels.maxOf { labelPaint.measureText(it) } + Y_AXIS_LABEL_MARGIN_PX * 2)
            .coerceAtLeast(Y_AXIS_LABEL_WIDTH_PX)
        val chartWidthPx = (size.width - yAxisLabelWidthPx).coerceAtLeast(0f)
        val widestLabelPx = buckets.maxOf { labelPaint.measureText(it.label) }
        val labeledIndices = selectLabeledIndices(
            bucketCount = buckets.size,
            availableWidthPx = chartWidthPx,
            labelWidthPx = widestLabelPx,
        )
        val barRects = computeBarRects(
            values = buckets.map(valueSelector),
            maxValue = maxValue,
            chartWidthPx = chartWidthPx,
            chartHeightPx = chartHeightPx,
        )

        barRects.forEach { rect ->
            drawRect(
                color = barColor,
                topLeft = Offset(yAxisLabelWidthPx + rect.left, TOP_PADDING_PX + rect.top),
                size = Size(rect.width, rect.height),
            )
        }

        // Y-axis gridlines at 0%, 50%, 100% of maxValue, each with a
        // right-aligned value label in the reserved left margin. Offset by
        // TOP_PADDING_PX so the 100% line (and the tallest bar) never sits
        // flush against the canvas's top edge.
        val gridlineYs = listOf(
            TOP_PADDING_PX + chartHeightPx,
            TOP_PADDING_PX + chartHeightPx / 2f,
            TOP_PADDING_PX,
        )
        gridlineLabels.forEachIndexed { index, label ->
            val y = gridlineYs[index]
            drawLine(
                color = gridColor,
                start = Offset(yAxisLabelWidthPx, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val textWidth = labelPaint.measureText(label)
            val labelX = yAxisLabelWidthPx - Y_AXIS_LABEL_MARGIN_PX - textWidth
            // Exact vertical centering on the tick's y-coordinate, via the
            // paint's real font metrics - the previous `y + textSize / 3f`
            // was only a rough approximation and read as visibly off-center.
            val fontMetrics = labelPaint.fontMetrics
            val labelY = y - (fontMetrics.ascent + fontMetrics.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, labelPaint)

            // Y-axis tick mark at this gridline's y-position, mirroring the
            // x-axis ticks drawn below.
            drawLine(
                color = gridColor,
                start = Offset(yAxisLabelWidthPx - TICK_HEIGHT_PX, y),
                end = Offset(yAxisLabelWidthPx, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Explicit y-axis vertical line - the left edge of the plot area,
        // previously implicit (nothing was drawn there).
        drawLine(
            color = gridColor,
            start = Offset(yAxisLabelWidthPx, TOP_PADDING_PX),
            end = Offset(yAxisLabelWidthPx, TOP_PADDING_PX + chartHeightPx),
            strokeWidth = 1.dp.toPx(),
        )

        // Compute every candidate label's clamped position first, then drop
        // any that would still overlap after clamping - pushing an edge
        // label inward (clampLabelX) can shove it into its neighbor even
        // though selectLabeledIndices' own index-based step guaranteed
        // enough room for the *unclamped*, centered positions. Found
        // on-device: the first bucket's label ("May 21") clamped hard right
        // and collided with the very next kept label ("May 31").
        val candidatePlacements = labeledIndices.sorted().map { index ->
            val rect = barRects[index]
            val label = buckets[index].label
            val textWidth = labelPaint.measureText(label)
            val barCenterX = yAxisLabelWidthPx + rect.left + rect.width / 2f
            val x = clampLabelX(
                centeredX = barCenterX - textWidth / 2f,
                labelWidthPx = textWidth,
                minX = yAxisLabelWidthPx,
                maxX = size.width,
            )
            LabelPlacement(index = index, x = x, width = textWidth, barCenterX = barCenterX)
        }

        dropOverlappingLabels(candidatePlacements).forEach { placement ->
            val label = buckets[placement.index].label
            val y = size.height - LABEL_AREA_HEIGHT_PX / 2f + labelPaint.textSize / 3f
            drawContext.canvas.nativeCanvas.drawText(label, placement.x, y, labelPaint)

            drawLine(
                color = gridColor,
                start = Offset(placement.barCenterX, TOP_PADDING_PX + chartHeightPx),
                end = Offset(placement.barCenterX, TOP_PADDING_PX + chartHeightPx + TICK_HEIGHT_PX),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

/** Geometry of a single bar, in canvas pixels, top-left origin. */
internal data class BarRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Pure geometry for the bars themselves — no [android.graphics.Paint] or
 * [androidx.compose.foundation.Canvas] involved, so it's plain-JUnit
 * testable, mirroring how [io.github.woxakv.koreadercompanion.presentation.widget.CalendarGridGraphRenderer]
 * keeps `columnIndexFor` standalone from `render()`.
 *
 * A [maxValue] of 0 (e.g. every bucket has zero minutes/pages) yields flat
 * (zero-height) bars rather than dividing by zero.
 *
 * [chartWidthPx] must already exclude the reserved y-axis label column
 * (sized dynamically from the actual label text, not a fixed constant) —
 * callers add that width back in only when actually drawing.
 */
internal fun computeBarRects(
    values: List<Int>,
    maxValue: Int,
    chartWidthPx: Float,
    chartHeightPx: Float,
): List<BarRect> {
    if (values.isEmpty()) return emptyList()
    val slotWidth = chartWidthPx / values.size
    val barWidth = slotWidth * (1f - BAR_GAP_FRACTION)
    val slotPadding = (slotWidth - barWidth) / 2f
    return values.mapIndexed { index, value ->
        val heightFraction = if (maxValue > 0) value.toFloat() / maxValue.toFloat() else 0f
        val barHeight = chartHeightPx * heightFraction
        BarRect(
            left = index * slotWidth + slotPadding,
            top = chartHeightPx - barHeight,
            width = barWidth,
            height = barHeight,
        )
    }
}

/**
 * Which bar indices get an x-axis label, driven by how many labels of
 * [labelWidthPx] actually fit in [availableWidthPx] — not a hardcoded
 * parity/modulo skip. For small bucket counts (e.g. 12 monthly buckets)
 * this naturally returns "label everything" once they all fit. For large
 * counts (e.g. ~90 daily buckets) it picks evenly-spaced indices, always
 * including index 0 and the last index so the chart's edges are never
 * unlabeled.
 */
internal fun selectLabeledIndices(
    bucketCount: Int,
    availableWidthPx: Float,
    labelWidthPx: Float,
): Set<Int> {
    if (bucketCount <= 0) return emptySet()
    if (bucketCount == 1) return setOf(0)

    val maxLabels = (availableWidthPx / labelWidthPx).toInt().coerceAtLeast(1)
    if (maxLabels >= bucketCount) {
        return (0 until bucketCount).toSet()
    }

    val step = ceil(bucketCount / maxLabels.toFloat()).toInt().coerceAtLeast(1)
    val indices = mutableListOf<Int>()
    var index = 0
    while (index < bucketCount) {
        indices.add(index)
        index += step
    }

    // The regular step guarantees consecutive kept indices are far enough
    // apart for their labels not to collide - but forcing the very last
    // bucket in unconditionally can land closer to the previous kept index
    // than that, if bucketCount doesn't divide evenly by step (e.g. 88 and
    // 89 out of 90 buckets: only 1 index apart, well under one label's
    // width). Drop the crowded neighbor rather than draw two overlapping
    // labels; the last bucket must still always be labeled.
    val lastIndex = bucketCount - 1
    if (indices.last() != lastIndex) {
        if (lastIndex - indices.last() < step) {
            indices.removeAt(indices.lastIndex)
        }
        indices.add(lastIndex)
    }
    return indices.toSet()
}

/**
 * The three y-axis gridline values (0%, 50%, 100% of [maxValue]), in the
 * order they're drawn top-to-bottom on screen: 0 at the chart's bottom,
 * maxValue/2 at mid-height, maxValue at the top.
 */
internal fun gridlineValues(maxValue: Int): List<Int> = listOf(0, maxValue / 2, maxValue)

/**
 * Clamps a horizontally-centered label's draw-x so the full label stays
 * within [minX, maxX] — used to stop x-axis labels at the chart's edges
 * (e.g. Day granularity's first/last bucket) from being drawn partly off
 * the reserved y-axis column or off the right edge of the canvas.
 *
 * A label that already fits is returned unchanged.
 */
internal fun clampLabelX(centeredX: Float, labelWidthPx: Float, minX: Float, maxX: Float): Float {
    val rightBound = (maxX - labelWidthPx).coerceAtLeast(minX)
    return centeredX.coerceIn(minX, rightBound)
}

/** A candidate x-axis label, already positioned via [clampLabelX]. */
internal data class LabelPlacement(val index: Int, val x: Float, val width: Float, val barCenterX: Float)

/**
 * Drops any candidate label that would overlap another after [clampLabelX]
 * clamping — clamping an edge label inward can shove it into its neighbor
 * even though [selectLabeledIndices]' own index-based spacing guaranteed
 * enough room for the *unclamped*, centered positions (found on-device: the
 * first bucket's clamped label collided with the very next kept label).
 *
 * The first and last entries in [placements] (by index order) are always
 * kept — matching [selectLabeledIndices]' "chart edges are never unlabeled"
 * guarantee — interior candidates are kept greedily, left to right, only
 * when they don't overlap the previously-kept label or the mandatory last
 * one. [placements] must already be sorted by ascending index.
 */
internal fun dropOverlappingLabels(placements: List<LabelPlacement>): List<LabelPlacement> {
    if (placements.size <= 2) return placements
    val first = placements.first()
    val last = placements.last()
    val kept = mutableListOf(first)
    var rightEdge = first.x + first.width
    for (placement in placements.subList(1, placements.size - 1)) {
        val fitsAfterPrevious = placement.x >= rightEdge
        val fitsBeforeLast = placement.x + placement.width <= last.x
        if (fitsAfterPrevious && fitsBeforeLast) {
            kept.add(placement)
            rightEdge = placement.x + placement.width
        }
    }
    kept.add(last)
    return kept
}
