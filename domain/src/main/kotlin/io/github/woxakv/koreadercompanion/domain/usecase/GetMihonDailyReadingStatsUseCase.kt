package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository

class GetMihonDailyReadingStatsUseCase(
    private val repository: ReadingStatsRepository,
) {
    suspend operator fun invoke(days: Int): Try<List<DailyReadingStat>> =
        repository.getDailyStats(days)
}
