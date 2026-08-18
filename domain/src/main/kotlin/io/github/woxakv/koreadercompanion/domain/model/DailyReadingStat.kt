package io.github.woxakv.koreadercompanion.domain.model

data class DailyReadingStat(
    val date: java.time.LocalDate,
    val pagesRead: Int,
    val minutesRead: Int,
)
