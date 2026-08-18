package io.github.woxakv.koreadercompanion.presentation.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// Grayscale fill per bucket (0 = no activity .. 4 = most active), staying
// strictly within the e-ink black/white/gray palette: near-white through
// black, no color hues.
private val BUCKET_FILL_COLORS = intArrayOf(
    0xFFF5F5F5.toInt(),
    0xFFD0D0D0.toInt(),
    0xFFA0A0A0.toInt(),
    0xFF606060.toInt(),
    0xFF000000.toInt(),
)

// Bucket 0 is nearly the same shade as a white widget background, so it
// gets a thin outline to stay visible as a distinct cell.
private const val EMPTY_CELL_STROKE_COLOR = 0xFFE0E0E0.toInt()
private const val EMPTY_CELL_STROKE_WIDTH_PX = 1f

private const val ROWS = 7 // days of week

internal const val LEFT_LABEL_WIDTH_PX = 60
internal const val TOP_LABEL_HEIGHT_PX = 24

/**
 * Column index (0-based from the oldest week) for [date], counted in
 * real Monday-Sunday calendar weeks back from the week containing
 * [thisMonday] — not a rolling "every 7 consecutive days" chunk, so a
 * date from a different calendar week than today never lands in
 * today's column.
 */
internal fun columnIndexFor(date: LocalDate, thisMonday: LocalDate, columns: Int): Int {
    val dateMonday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val weeksBack = ChronoUnit.WEEKS.between(dateMonday, thisMonday).toInt()
    return columns - 1 - weeksBack
}

/**
 * Renders a GitHub-style contribution heatmap into a single [Bitmap]: weeks
 * as columns (oldest week at column 0, today's week last), days-of-week as
 * rows. Drawn as one bitmap via [Canvas] rather than nested Glance layout
 * views, since a widget grid of ~90+ individual Glance views is both slow to
 * compose and not how RemoteViews-backed widgets are meant to be built.
 *
 * @param buckets bucket index (0 until [BUCKET_COUNT]) per date, as produced by [assignBuckets].
 * @param days size of the window ending at [today] (must match what [buckets] was built with).
 * @param today the last day in the window (rendered at the bottom of the last column).
 * @param cellSizePx size in pixels of each (square) day cell, before the gap.
 * @param cellGapPx vertical gap in pixels between adjacent row cells.
 * @param cellGapHorizontalPx horizontal gap in pixels between adjacent column cells.
 */
fun render(
    buckets: Map<LocalDate, Int>,
    days: Int,
    today: LocalDate,
    cellSizePx: Int,
    cellGapPx: Int,
    cellGapHorizontalPx: Int,
): Bitmap {
    val columns = ceil(days / 7.0).toInt()
    val colStep = cellSizePx + cellGapHorizontalPx
    val rowStep = cellSizePx + cellGapPx
    val width = LEFT_LABEL_WIDTH_PX + columns * colStep
    val height = TOP_LABEL_HEIGHT_PX + ROWS * rowStep
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EMPTY_CELL_STROKE_WIDTH_PX
        color = EMPTY_CELL_STROKE_COLOR
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF757575.toInt() // matches design-system's EinkColors.Outline — muted gray, not pure black
        textSize = 18f
    }
    val cornerRadius = cellSizePx / 4f

    // The last day in the window (today) sits at the bottom of the last
    // (rightmost) column; walking backwards from today fills columns from
    // the right, row-by-row, so the oldest week naturally lands at column 0.
    for (i in 0 until days) {
        val date = today.minusDays(i.toLong())
        val bucket = buckets[date] ?: 0
        val column = columnIndexFor(date, thisMonday, columns)
        val row = date.dayOfWeek.value - 1 // Monday=1..Sunday=7 -> Monday=0..Sunday=6

        val left = LEFT_LABEL_WIDTH_PX + column * colStep + cellGapHorizontalPx / 2f
        val top = TOP_LABEL_HEIGHT_PX + row * rowStep + cellGapPx / 2f
        val rect = RectF(left, top, left + cellSizePx, top + cellSizePx)

        fillPaint.color = BUCKET_FILL_COLORS[bucket.coerceIn(0, BUCKET_FILL_COLORS.size - 1)]
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        if (bucket == 0) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
        }
    }

    val dayRowLabels = mapOf(0 to "Mon", 3 to "Thu", 6 to "Sun")
    for ((row, text) in dayRowLabels) {
        val baselineY = TOP_LABEL_HEIGHT_PX + row * rowStep + cellSizePx / 2f + labelPaint.textSize / 3f
        canvas.drawText(text, 0f, baselineY, labelPaint)
    }

    val monthAbbreviations = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthStartColumns = (0 until columns).mapNotNull { column ->
        val columnMonday = thisMonday.minusDays(((columns - 1 - column) * 7).toLong())
        val firstOfMonth = (0..6L).map { columnMonday.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 }
        firstOfMonth?.let { column to it.monthValue }
    }
    monthStartColumns.forEach { (column, monthValue) ->
        val x = (LEFT_LABEL_WIDTH_PX + column * colStep).toFloat()
        canvas.drawText(monthAbbreviations[monthValue - 1], x, TOP_LABEL_HEIGHT_PX - 8f, labelPaint)
    }

    return bitmap
}
