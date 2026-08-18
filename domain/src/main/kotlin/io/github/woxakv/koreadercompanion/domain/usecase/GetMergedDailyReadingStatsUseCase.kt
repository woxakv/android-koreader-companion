package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.getOrNull
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

/**
 * Centralizes "fetch both sources, merge by filter" so both the
 * currently-reading screen's ViewModel and the widget refresh path call the
 * same orchestration rather than duplicating it. A fetch failure on either
 * source is treated as "no data from that source" rather than propagated -
 * matches the ViewModel's existing `as? Try.Success` pattern for stats.
 */
class GetMergedDailyReadingStatsUseCase(
    private val getKoreaderStats: GetDailyReadingStatsUseCase,
    private val getMihonStats: GetMihonDailyReadingStatsUseCase,
    private val merge: MergeDailyReadingStatsUseCase,
) {
    suspend operator fun invoke(days: Int, filter: StatsSourceFilter): List<DailyReadingStat> {
        val koreader = getKoreaderStats(days).getOrNull().orEmpty()
        val mihon = getMihonStats(days).getOrNull().orEmpty()
        return merge(
            mapOf(StorageTarget.KOREADER to koreader, StorageTarget.MIHON to mihon),
            filter,
        )
    }
}
