package io.github.woxakv.koreadercompanion.data.local.db

data class DailyStatRowDto(
    val day: String,
    val totalSeconds: Long,
    val pagesRead: Int,
)
