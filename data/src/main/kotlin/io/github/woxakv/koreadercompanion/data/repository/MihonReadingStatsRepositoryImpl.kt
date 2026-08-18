package io.github.woxakv.koreadercompanion.data.repository

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.mihon.BackupHistory
import io.github.woxakv.koreadercompanion.data.local.mihon.MihonBackupDataSource
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * One [BackupHistory] entry is treated as one "chapter read" for stats
 * purposes - reasonable since each entry represents one reading session
 * against one chapter. Kept as a named, top-level constant (rather than
 * inlined) since it's explicitly a placeholder weighting the user may want
 * to tune later.
 */
internal const val MIHON_CHAPTER_TO_PAGE_WEIGHT = 3

/**
 * Mirrors [ReadingStatsRepositoryImpl] but reads from a Mihon `.tachibk`
 * backup instead of KOReader's own data. Bound under [io.github.woxakv.koreadercompanion.data.di.MihonSource]
 * so it can coexist with the KOReader implementation behind the same
 * [ReadingStatsRepository] interface.
 */
class MihonReadingStatsRepositoryImpl @Inject constructor(
    private val storageAccessRepository: StorageAccessRepository,
    private val mihonBackupDataSource: MihonBackupDataSource,
) : ReadingStatsRepository {

    override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> {
        // Unlike CurrentBookRepository's "not available" failure, an
        // ungranted/empty Mihon source reads as "no Mihon activity" here -
        // an empty list wrapped in success, not a failure the merge step
        // (Step 6) would have to special-case. KOReader's own
        // ReadingStatsRepositoryImpl fails in the equivalent situation
        // (no grant/root/stats file), but that convention isn't mirrored
        // here deliberately, per this step's plan.
        val treeUriString = storageAccessRepository.grantedTreeUriString(StorageTarget.MIHON)
            ?: return Try.Success(emptyList())

        val backup = mihonBackupDataSource.readLatestBackup(treeUriString)
            ?: return Try.Success(emptyList())

        val since = LocalDate.now().minusDays((days - 1).toLong())

        val stats = backup.backupManga
            .asSequence()
            .flatMap { it.history }
            .map { it to it.lastRead.toLocalDate() }
            .filter { (_, date) -> !date.isBefore(since) }
            .groupBy({ (_, date) -> date }, { (history, _) -> history })
            .map { (date, entries) ->
                DailyReadingStat(
                    date = date,
                    pagesRead = entries.size * MIHON_CHAPTER_TO_PAGE_WEIGHT,
                    minutesRead = (entries.sumOf { it.readDuration } / 60000).toInt(),
                )
            }
            .sortedBy { it.date }

        return Try.Success(stats)
    }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
