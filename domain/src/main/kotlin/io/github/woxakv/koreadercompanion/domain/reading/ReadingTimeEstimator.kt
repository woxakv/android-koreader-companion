package io.github.woxakv.koreadercompanion.domain.reading

/**
 * Approximates remaining reading time from KOReader's own book-level
 * aggregates (total_read_time / total_read_pages), not exact parity with
 * KOReader's own displayed estimate (its precise averaging window is
 * unknown), just a reasonable projection from historical pace.
 */
object ReadingTimeEstimator {

    fun estimateSecondsRemaining(
        totalPages: Int,
        currentPage: Int,
        totalReadTimeSeconds: Long,
        totalReadPages: Int,
    ): Long? {
        if (totalReadPages <= 0) return null
        val remainingPages = (totalPages - currentPage).coerceAtLeast(0)
        val secondsPerPage = totalReadTimeSeconds.toDouble() / totalReadPages
        return (remainingPages * secondsPerPage).toLong()
    }
}
