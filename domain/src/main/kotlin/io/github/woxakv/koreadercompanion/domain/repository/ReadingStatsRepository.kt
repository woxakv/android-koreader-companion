package io.github.woxakv.koreadercompanion.domain.repository

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat

interface ReadingStatsRepository {
    suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>>
}
