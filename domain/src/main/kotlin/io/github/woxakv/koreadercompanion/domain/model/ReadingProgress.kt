package io.github.woxakv.koreadercompanion.domain.model

data class ReadingProgress(
    val currentPage: Int,
    val totalPages: Int,
    val totalReadTimeSeconds: Long,
    val totalReadPages: Int,
) {
    val percentRead: Float
        get() = if (totalPages <= 0) 0f else (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
}
