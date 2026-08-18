package io.github.woxakv.koreadercompanion.domain.model

/**
 * The `minutesThisYear`/`pagesThisYear` and `minutesAllTime`/`pagesAllTime`
 * pairs are only accurate when [io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase.summarize]
 * is called with a sufficiently large `stats` list — the widget-facing
 * `invoke()` still only fetches `LOOKBACK_DAYS = 365` days, so its result
 * under-reports Year/All-Time for users with more than 365 days of history.
 */
data class ReadingStatsSummary(
    val avgMinutesLast7Days: Int,
    val streakDays: Int,
    val minutesToday: Int,
    val pagesToday: Int,
    val minutesThisMonth: Int,
    val pagesThisMonth: Int,
    val minutesThisWeek: Int,
    val pagesThisWeek: Int,
    val minutesThisYear: Int = 0,
    val pagesThisYear: Int = 0,
    val minutesAllTime: Int = 0,
    val pagesAllTime: Int = 0,
)
