package io.github.woxakv.koreadercompanion.data.repository

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.mihon.Backup
import io.github.woxakv.koreadercompanion.data.local.mihon.BackupChapter
import io.github.woxakv.koreadercompanion.data.local.mihon.BackupHistory
import io.github.woxakv.koreadercompanion.data.local.mihon.BackupManga
import io.github.woxakv.koreadercompanion.data.local.mihon.MihonBackupDataSource
import io.github.woxakv.koreadercompanion.data.local.mihon.MihonCbzCoverDataSource
import io.github.woxakv.koreadercompanion.data.local.mihon.MihonCoverDataSource
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import javax.inject.Inject

/**
 * Package name of the Mihon app itself, used to build a plain app-launch
 * intent in the presentation layer (`PackageManager.getLaunchIntentForPackage`)
 * - see [CurrentBook.launchIntentPackage].
 */
internal const val MIHON_PACKAGE_NAME = "app.mihon"

/**
 * Mirrors [CurrentBookRepositoryImpl] but reads from a Mihon `.tachibk`
 * backup instead of KOReader's own data. Bound under [io.github.woxakv.koreadercompanion.data.di.MihonSource]
 * so it can coexist with the KOReader implementation behind the same
 * [CurrentBookRepository] interface.
 */
class MihonCurrentBookRepositoryImpl @Inject constructor(
    private val storageAccessRepository: StorageAccessRepository,
    private val mihonBackupDataSource: MihonBackupDataSource,
    private val mihonCoverDataSource: MihonCoverDataSource,
    private val mihonCbzCoverDataSource: MihonCbzCoverDataSource,
) : CurrentBookRepository {

    override suspend fun getCurrentBook(): Try<CurrentBook> {
        // Each "nothing to show yet" case gets the error type that actually
        // matches it, not a single collapsed failure - PermissionNotGranted
        // and DataFolderNotFound both route through AppError.isSetupIssue()
        // to a "please grant access" UI prompt, which would be actively
        // wrong for a folder that IS granted (cases 2 and 3 below): the user
        // would be told to grant something they've already granted. Mirrors
        // the exact lesson from this app's own finished-book-crash-fix plan
        // (KoreaderError.NoBookFound's message was wrong for a reused-error
        // situation there too).
        val treeUriString = storageAccessRepository.grantedTreeUriString(StorageTarget.MIHON)
            ?: return Try.Failure(KoreaderError.PermissionNotGranted)

        val backup = mihonBackupDataSource.readLatestBackup(treeUriString)
            ?: return Try.Failure(KoreaderError.DataFolderNotFound(treeUriString))

        val (manga, history) = findMostRecentlyReadEntry(backup)
            ?: return Try.Failure(KoreaderError.NoBookFound)

        val chapter = findChapter(manga, history)

        return Try.Success(
            CurrentBook(
                title = manga.title,
                // Not parsed: the trimmed proto schema doesn't declare an
                // author field, and guessing its @ProtoNumber risks silently
                // misdecoding a real field. A deliberate scope cut.
                author = null,
                progress = chapter.toReadingProgress(manga),
                // Not computable without per-chapter page/duration totals,
                // same reasoning as ReadingProgress.totalPages below.
                estimatedSecondsRemaining = null,
                // CBZ-first, network-fallback: the CBZ cover is extracted
                // straight from the already-downloaded chapter on disk, so
                // it works even when the source CDN behind thumbnailUrl
                // blocks or rate-limits the request (a real failure mode -
                // see MihonCbzCoverDataSource's doc comment). Network is
                // still tried as a fallback for manga with no local .cbz
                // download yet.
                coverImageBytes = mihonCbzCoverDataSource.fetchCoverBytes(
                    mihonTreeUriString = treeUriString,
                    mangaTitle = manga.title,
                    currentChapterName = chapter?.name,
                    currentChapterNumber = chapter?.chapterNumber ?: 0f,
                ) ?: manga.thumbnailUrl?.let { mihonCoverDataSource.fetchCoverBytes(it) },
                // This is a package-launch, not a document-open - see
                // launchIntentPackage below and CurrentBook's own doc.
                bookContentUriString = null,
                bookAccessNote = chapter?.let(::chapterLabel),
                launchIntentPackage = MIHON_PACKAGE_NAME,
            ),
        )
    }

    /**
     * Every manga's history is considered, not just favorited ones - the
     * backup-time semantics of `favorite` weren't confirmed, and excluding
     * entries risks silently hiding real data.
     */
    private fun findMostRecentlyReadEntry(backup: Backup): Pair<BackupManga, BackupHistory>? {
        var bestManga: BackupManga? = null
        var bestHistory: BackupHistory? = null
        for (manga in backup.backupManga) {
            for (history in manga.history) {
                if (bestHistory == null || history.lastRead > bestHistory.lastRead) {
                    bestManga = manga
                    bestHistory = history
                }
            }
        }
        val manga = bestManga ?: return null
        val history = bestHistory ?: return null
        return manga to history
    }

    /**
     * Falls back to the highest-numbered read chapter if the winning history
     * entry's url doesn't match any current chapter - this can happen if
     * chapter identifiers changed between backups. Never crashes: returns
     * null (degrading gracefully) if neither lookup finds anything.
     */
    private fun findChapter(manga: BackupManga, history: BackupHistory): BackupChapter? =
        manga.chapters.firstOrNull { it.url == history.url }
            ?: manga.chapters.filter { it.read }.maxByOrNull { it.chapterNumber }

    private fun chapterLabel(chapter: BackupChapter): String =
        chapter.name.ifBlank { "Chapter ${chapter.chapterNumber}" }

    /**
     * Progress is chapter-level (current chapter number out of the manga's
     * total known chapter count), not page-level within a chapter - Mihon's
     * trimmed backup schema doesn't carry a chapter's total page count, but
     * it does carry the manga's full chapter list, which is the more useful
     * "how far into this series" signal anyway. totalReadTimeSeconds/
     * totalReadPages are left at their "unknown" values (not derivable from
     * a single chapter).
     */
    private fun BackupChapter?.toReadingProgress(manga: BackupManga): ReadingProgress = ReadingProgress(
        currentPage = this?.chapterNumber?.toInt() ?: 0,
        totalPages = manga.chapters.size,
        totalReadTimeSeconds = 0,
        totalReadPages = 0,
    )
}
