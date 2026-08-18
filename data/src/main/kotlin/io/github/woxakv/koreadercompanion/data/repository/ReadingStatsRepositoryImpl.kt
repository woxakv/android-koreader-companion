package io.github.woxakv.koreadercompanion.data.repository

import android.database.sqlite.SQLiteException
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.db.DailyStatRowDto
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsDatabaseCopier
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsSqliteDataSource
import io.github.woxakv.koreadercompanion.data.local.saf.KoreaderFileResolver
import io.github.woxakv.koreadercompanion.data.local.saf.StorageAccessLocalDataSource
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ReadingStatsRepositoryImpl @Inject constructor(
    private val storageAccessLocalDataSource: StorageAccessLocalDataSource,
    private val koreaderFileResolver: KoreaderFileResolver,
    private val statisticsDatabaseCopier: StatisticsDatabaseCopier,
    private val statisticsSqliteDataSource: StatisticsSqliteDataSource,
) : ReadingStatsRepository {

    override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> {
        val koreaderTreeUriString = storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.KOREADER)
            ?: return Try.Failure(KoreaderError.StatisticsUnavailable)

        val koreaderRoot = storageAccessLocalDataSource.resolveGrantedRoot(koreaderTreeUriString)
            ?: return Try.Failure(KoreaderError.StatisticsUnavailable)

        val statsDocumentFile = koreaderFileResolver.resolve(koreaderRoot, KOREADER_STATISTICS_PATH)
            ?: return Try.Failure(KoreaderError.StatisticsUnavailable)

        // Retried once on SQLiteException specifically: our own locking can't
        // stop KOReader itself from writing statistics.sqlite3 mid-copy, which
        // can hand us a transient, partially-written snapshot. A fresh
        // copy+read almost always succeeds; other exceptions aren't retried
        // since trying again won't fix them.
        return try {
            readDailyStats(statsDocumentFile, days)
        } catch (firstAttempt: SQLiteException) {
            try {
                readDailyStats(statsDocumentFile, days)
            } catch (t: Throwable) {
                Try.Failure(KoreaderError.StatisticsUnavailable)
            }
        } catch (t: Throwable) {
            Try.Failure(KoreaderError.StatisticsUnavailable)
        }
    }

    private suspend fun readDailyStats(
        statsDocumentFile: DocumentFile,
        days: Int,
    ): Try<List<DailyReadingStat>> = statisticsDatabaseCopier.withCachedDatabase(statsDocumentFile) { dbFile ->
        val since = LocalDate.now().minusDays((days - 1).toLong())
        val sinceEpochSeconds = since.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val rows = statisticsSqliteDataSource.getDailyStats(dbFile, sinceEpochSeconds)
        Try.Success(rows.map { it.toDomain() })
    }

    private fun DailyStatRowDto.toDomain() = DailyReadingStat(
        date = LocalDate.parse(day),
        pagesRead = pagesRead,
        minutesRead = (totalSeconds / 60).toInt(),
    )

    private companion object {
        const val KOREADER_STATISTICS_PATH = "/storage/emulated/0/koreader/settings/statistics.sqlite3"
    }
}
