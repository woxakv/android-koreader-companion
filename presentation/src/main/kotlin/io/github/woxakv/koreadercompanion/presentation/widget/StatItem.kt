package io.github.woxakv.koreadercompanion.presentation.widget

import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary

enum class StatItem {
    DAILY_AVG_MINUTES,
    STREAK,
    MINUTES_TODAY,
    PAGES_TODAY,
    THIS_MONTH,
    THIS_WEEK,
    THIS_YEAR,
    ALL_TIME,
}

val DEFAULT_STAT_ROWS: List<List<StatItem>> = listOf(
    listOf(StatItem.DAILY_AVG_MINUTES, StatItem.STREAK, StatItem.MINUTES_TODAY, StatItem.PAGES_TODAY),
    listOf(StatItem.THIS_MONTH, StatItem.THIS_WEEK),
)

private fun formatDurationLabel(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

fun StatItem.label(): String = when (this) {
    StatItem.DAILY_AVG_MINUTES -> "Daily Avg"
    StatItem.STREAK -> "Days Streak"
    StatItem.MINUTES_TODAY -> "Min Today"
    StatItem.PAGES_TODAY -> "Pages Today"
    StatItem.THIS_MONTH -> "This Month"
    StatItem.THIS_WEEK -> "This Week"
    StatItem.THIS_YEAR -> "This Year"
    StatItem.ALL_TIME -> "All Time"
}

fun StatItem.value(summary: ReadingStatsSummary): String = when (this) {
    StatItem.DAILY_AVG_MINUTES -> "${summary.avgMinutesLast7Days}min"
    StatItem.STREAK -> summary.streakDays.toString()
    StatItem.MINUTES_TODAY -> summary.minutesToday.toString()
    StatItem.PAGES_TODAY -> summary.pagesToday.toString()
    StatItem.THIS_MONTH -> "${formatDurationLabel(summary.minutesThisMonth)} · ${summary.pagesThisMonth} pages"
    StatItem.THIS_WEEK -> "${formatDurationLabel(summary.minutesThisWeek)} · ${summary.pagesThisWeek} pages"
    StatItem.THIS_YEAR -> "${formatDurationLabel(summary.minutesThisYear)} · ${summary.pagesThisYear} pages"
    StatItem.ALL_TIME -> "${formatDurationLabel(summary.minutesAllTime)} · ${summary.pagesAllTime} pages"
}
