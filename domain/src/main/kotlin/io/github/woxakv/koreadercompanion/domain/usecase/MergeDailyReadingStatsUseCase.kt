package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

class MergeDailyReadingStatsUseCase {
    operator fun invoke(
        sources: Map<StorageTarget, List<DailyReadingStat>>,
        filter: StatsSourceFilter,
    ): List<DailyReadingStat> {
        val selected = when (filter) {
            StatsSourceFilter.ALL -> sources.values.flatten()
            StatsSourceFilter.KOREADER -> sources[StorageTarget.KOREADER].orEmpty()
            StatsSourceFilter.MIHON -> sources[StorageTarget.MIHON].orEmpty()
        }
        return selected
            .groupBy { it.date }
            .map { (date, statsForDate) ->
                DailyReadingStat(
                    date = date,
                    pagesRead = statsForDate.sumOf { it.pagesRead },
                    minutesRead = statsForDate.sumOf { it.minutesRead },
                )
            }
    }
}
