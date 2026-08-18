package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository

class GetStatsSourceFilterUseCase(
    private val repository: StatsFilterRepository,
) {
    suspend operator fun invoke(): StatsSourceFilter = repository.getFilter()
}
