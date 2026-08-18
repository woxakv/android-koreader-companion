package io.github.woxakv.koreadercompanion.data.repository

import android.database.sqlite.SQLiteException
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsDatabaseCopier
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsSqliteDataSource
import io.github.woxakv.koreadercompanion.data.local.db.toReadingProgress
import io.github.woxakv.koreadercompanion.data.local.epub.EpubCoverDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.BookProgressDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.HistoryDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.LuaParseException
import io.github.woxakv.koreadercompanion.data.local.lua.LuaParseTimeoutException
import io.github.woxakv.koreadercompanion.data.local.lua.ReaderSettingsDataSource
import io.github.woxakv.koreadercompanion.data.local.saf.KoreaderFileResolver
import io.github.woxakv.koreadercompanion.data.local.saf.StorageAccessLocalDataSource
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.matching.BookTitleMatcher
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.reading.ReadingTimeEstimator
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlin.math.roundToInt

class CurrentBookRepositoryImpl @Inject constructor(
    private val storageAccessLocalDataSource: StorageAccessLocalDataSource,
    private val koreaderFileResolver: KoreaderFileResolver,
    private val readerSettingsDataSource: ReaderSettingsDataSource,
    private val historyDataSource: HistoryDataSource,
    private val statisticsDatabaseCopier: StatisticsDatabaseCopier,
    private val statisticsSqliteDataSource: StatisticsSqliteDataSource,
    private val epubCoverDataSource: EpubCoverDataSource,
    private val bookProgressDataSource: BookProgressDataSource,
) : CurrentBookRepository {

    override suspend fun getCurrentBook(): Try<CurrentBook> {
        val koreaderTreeUriString = storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.KOREADER)
            ?: return Try.Failure(KoreaderError.PermissionNotGranted)

        val koreaderRoot = storageAccessLocalDataSource.resolveGrantedRoot(koreaderTreeUriString)
            ?: return Try.Failure(KoreaderError.PermissionNotGranted)

        return try {
            buildCurrentBook(koreaderRoot)
        } catch (throwable: Throwable) {
            Try.Failure(classifyError(throwable))
        }
    }

    private suspend fun buildCurrentBook(koreaderRoot: DocumentFile): Try<CurrentBook> {
        val statsDocumentFile = koreaderFileResolver.resolve(koreaderRoot, KOREADER_STATISTICS_PATH)
            ?: return Try.Failure(KoreaderError.DataFolderNotFound(KOREADER_STATISTICS_PATH))

        // Retried once on SQLiteException specifically: our own locking can't
        // stop KOReader itself from writing statistics.sqlite3 mid-copy (most
        // likely exactly when a book is finished), which can hand us a
        // transient, partially-written snapshot. A fresh copy+read almost
        // always succeeds. Other exceptions (SecurityException,
        // FileNotFoundException, ...) aren't retried - they won't be fixed by
        // trying again and retrying just wastes time before propagating.
        return try {
            readCurrentBook(koreaderRoot, statsDocumentFile)
        } catch (error: SQLiteException) {
            readCurrentBook(koreaderRoot, statsDocumentFile)
        }
    }

    private suspend fun readCurrentBook(
        koreaderRoot: DocumentFile,
        statsDocumentFile: DocumentFile,
    ): Try<CurrentBook> = statisticsDatabaseCopier.withCachedDatabase(statsDocumentFile) { dbFile ->
        // KOReader's own `lastfile` is the only authority on which book is
        // actually open, so it drives book selection - the statistics DB's
        // most-recent row is merely a candidate list to match against.
        val bookFilePath = resolveCurrentBookFilePath(koreaderRoot)

        // Some books end up stored inside KOReader's own private app-data
        // folder rather than the shared Books folder. No SAF grant, at any
        // scope, can ever reach another app's Android/data/<package>/
        // folder - this is a hard platform restriction, not a permission
        // we're missing - so skip the doomed lookup and explain why instead.
        val bookAccessNote = bookFilePath?.let { inaccessibleAppPrivateStorageNoteOrNull(it) }

        // KOReader records "reading"/"complete"/"on hold" per book in its own
        // sidecar, not in statistics.sqlite3 (confirmed: the book table has
        // no status column). A finished/on-hold book should read as "no book
        // currently open", the same graceful empty state as never-read, not
        // a stale "Currently Reading" card kept alive by fallback stats. This
        // check is bookFilePath-driven (like resolvePercentFinishedCurrentPage
        // below) so it still works once the epub itself has been deleted but
        // its .sdr sidecar survives. Gated on bookAccessNote for the same
        // reason the epub lookup below is: a private-app-storage sidecar is
        // just as unreachable via SAF as the epub itself. An unreadable
        // status (no Books grant, sidecar missing, ...) must not change
        // behavior - only an explicit non-"reading" status short-circuits.
        val bookStatus = if (bookAccessNote == null) {
            bookFilePath?.let { resolveBookStatus(it) }
        } else {
            null
        }
        if (bookStatus != null && bookStatus != "" && bookStatus != "reading") {
            return@withCachedDatabase Try.Failure(KoreaderError.NoBookFound)
        }

        val epubDocumentFile = if (bookAccessNote == null) {
            bookFilePath?.let { resolveEpubDocumentFileIfBooksGranted(it) }
        } else {
            null
        }
        // The original file's own content:// URI - must stay this way (not
        // a copy) so KOReader recognizes it as the exact same book and
        // resumes from the saved reading position, which it tracks via a
        // sidecar file keyed to the original file's identity. Available
        // independent of the title-match gate below, so "open in KOReader"
        // still works even when cover pairing was declined as a mismatch.
        val bookContentUriString = epubDocumentFile?.uri?.toString()
        val epubTitle = epubDocumentFile?.let { epubCoverDataSource.extractTitle(it) }

        val recentStats = statisticsSqliteDataSource.getRecentBookStats(dbFile, RECENT_BOOK_STATS_WINDOW)
        // Pair the open book with its own stats row instead of assuming the
        // newest row is it. On a tie of identically-titled rows the first in
        // last_open DESC order wins - accepted, since titles are the only
        // identity we can compare against.
        val matchedStats = recentStats.firstOrNull { BookTitleMatcher.matches(epubTitle, it.title) }
        val bookStats = matchedStats
            ?: recentStats.firstOrNull()
            ?: return@withCachedDatabase Try.Failure(KoreaderError.NoBookFound)
        val maxPageRead = statisticsSqliteDataSource.getMaxPageRead(dbFile, bookStats.id) ?: 0

        val coverBytes = epubDocumentFile?.let { coverBytesIfTitleMatches(it, epubTitle, bookStats.title) }

        // The sidecar's percent_finished is KOReader's actual current reading
        // position; maxPageRead is only a "furthest page ever reached"
        // watermark. Prefer the sidecar value and fall back to the watermark
        // whenever the sidecar isn't available for any reason - this is a
        // graceful enhancement, not a hard requirement for showing progress.
        // Only safe in the matched branch: the percentage belongs to
        // bookFilePath's book, so scaling it by bookStats.pages needs the two
        // to be the same book. Without a match, the watermark is all we trust.
        val currentPage = (
            if (matchedStats != null && bookAccessNote == null) {
                bookFilePath?.let { resolvePercentFinishedCurrentPage(it, bookStats.pages) }
            } else {
                null
            }
            ) ?: maxPageRead

        val progress = bookStats.toReadingProgress(currentPage)
        val estimatedSecondsRemaining = ReadingTimeEstimator.estimateSecondsRemaining(
            totalPages = bookStats.pages,
            currentPage = currentPage,
            totalReadTimeSeconds = bookStats.totalReadTime,
            totalReadPages = bookStats.totalReadPages,
        )

        Try.Success(
            CurrentBook(
                title = bookStats.title,
                author = bookStats.authors,
                progress = progress,
                estimatedSecondsRemaining = estimatedSecondsRemaining,
                coverImageBytes = coverBytes,
                bookContentUriString = bookContentUriString,
                bookAccessNote = bookAccessNote,
            ),
        )
    }

    private fun inaccessibleAppPrivateStorageNoteOrNull(bookFilePath: String): String? {
        if (!bookFilePath.startsWith(ANDROID_APP_PRIVATE_STORAGE_PREFIX)) return null
        return "This book is stored inside KOReader's own private app folder, which Android " +
            "doesn't let other apps read - no cover or direct-open is possible for it."
    }

    /**
     * Books access is optional (koreader/ and Books/ are typically sibling
     * folders under a root SAF can't grant directly, so they need separate
     * grants) - no Books grant, or the path not resolving there, just means
     * no cover/open-in-KOReader, not a failure.
     */
    private suspend fun resolveEpubDocumentFileIfBooksGranted(bookFilePath: String): DocumentFile? {
        val booksRoot = resolveBooksRoot() ?: return null
        return koreaderFileResolver.resolve(booksRoot, bookFilePath)
    }

    /**
     * Books access is optional (see [resolveEpubDocumentFileIfBooksGranted]) -
     * shared root-resolution so both the epub lookup and the progress-sidecar
     * lookup below treat a missing/ungranted Books tree the same way.
     */
    private suspend fun resolveBooksRoot(): DocumentFile? {
        val booksTreeUriString = storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.BOOKS) ?: return null
        return storageAccessLocalDataSource.resolveGrantedRoot(booksTreeUriString)
    }

    /**
     * KOReader's per-book sidecar (`<bookDir>/<bookName>.sdr/metadata.<ext>.lua`)
     * carries `percent_finished`, the actual current reading position - unlike
     * the statistics DB's `maxPageRead`, which is only a furthest-page-ever-
     * reached watermark. Never throws: any failure (no Books grant, sidecar
     * missing, unreadable, malformed, or missing the key) yields null so the
     * caller falls back to the watermark unchanged.
     */
    private suspend fun resolvePercentFinishedCurrentPage(bookFilePath: String, totalPages: Int): Int? =
        runCatching {
            val booksRoot = resolveBooksRoot() ?: return@runCatching null
            val sidecarDocumentFile = koreaderFileResolver.resolve(booksRoot, sidecarMetadataPath(bookFilePath))
                ?: return@runCatching null
            val percentFinished = bookProgressDataSource.readPercentFinished(sidecarDocumentFile)
                ?: return@runCatching null
            (percentFinished * totalPages).roundToInt().coerceIn(0, totalPages)
        }.getOrNull()

    /**
     * KOReader's per-book sidecar also carries `summary.status`
     * ("reading"/"complete"/"on hold"/...), which the statistics DB doesn't
     * track at all. Never throws: any failure (no Books grant, sidecar
     * missing, unreadable, malformed, or missing the key) yields null so the
     * caller treats status as unknown and leaves behavior unchanged.
     */
    private suspend fun resolveBookStatus(bookFilePath: String): String? =
        runCatching {
            val booksRoot = resolveBooksRoot() ?: return@runCatching null
            val sidecarDocumentFile = koreaderFileResolver.resolve(booksRoot, sidecarMetadataPath(bookFilePath))
                ?: return@runCatching null
            bookProgressDataSource.readStatus(sidecarDocumentFile)
        }.getOrNull()

    private suspend fun coverBytesIfTitleMatches(
        epubDocumentFile: DocumentFile,
        epubTitle: String?,
        statsTitle: String,
    ): ByteArray? {
        if (!BookTitleMatcher.matches(epubTitle, statsTitle)) return null
        return epubCoverDataSource.extractCoverBytes(epubDocumentFile)
    }

    private suspend fun resolveCurrentBookFilePath(koreaderRoot: DocumentFile): String? {
        val settingsDocumentFile = koreaderFileResolver.resolve(koreaderRoot, KOREADER_SETTINGS_READER_PATH)
        val lastFile = settingsDocumentFile?.let { readerSettingsDataSource.readLastFile(it) }
        if (lastFile != null) return lastFile

        val historyDocumentFile = koreaderFileResolver.resolve(koreaderRoot, KOREADER_HISTORY_PATH) ?: return null
        return historyDataSource.readHistory(historyDocumentFile).firstOrNull()?.file
    }

    private fun classifyError(throwable: Throwable): KoreaderError = when (throwable) {
        is SecurityException -> KoreaderError.PermissionNotGranted
        is FileNotFoundException -> KoreaderError.DataFolderNotFound(throwable.message ?: KOREADER_STATISTICS_PATH)
        is SQLiteException -> KoreaderError.StatisticsUnavailable
        is LuaParseTimeoutException -> KoreaderError.ParseTimeout
        is LuaParseException -> KoreaderError.Unknown(throwable.message ?: throwable.toString())
        else -> KoreaderError.Unknown(throwable.message ?: throwable.toString())
    }

    private companion object {
        const val KOREADER_SETTINGS_READER_PATH = "/storage/emulated/0/koreader/settings.reader.lua"
        const val KOREADER_HISTORY_PATH = "/storage/emulated/0/koreader/history.lua"
        const val KOREADER_STATISTICS_PATH = "/storage/emulated/0/koreader/settings/statistics.sqlite3"
        const val ANDROID_APP_PRIVATE_STORAGE_PREFIX = "/storage/emulated/0/Android/"

        // How many recently-opened books to consider when matching the open
        // book to its stats row. The open book is essentially always at the
        // very top by last_open, so this is generous headroom rather than a
        // tuned figure - the query stays trivially cheap either way.
        const val RECENT_BOOK_STATS_WINDOW = 20
    }
}

/**
 * KOReader keeps a per-book sidecar directory next to the book file itself:
 * `<bookDir>/<bookName>.sdr/metadata.<ext>.lua`. Verified against a real
 * device - for `/storage/emulated/0/Books/L'Attentat....epub` this produces
 * `/storage/emulated/0/Books/L'Attentat....sdr/metadata.epub.lua`, the exact
 * sidecar path that exists on-device.
 */
private fun sidecarMetadataPath(bookFilePath: String): String {
    val lastSlash = bookFilePath.lastIndexOf('/')
    val dir = bookFilePath.substring(0, lastSlash + 1)
    val fileName = bookFilePath.substring(lastSlash + 1)
    val ext = fileName.substringAfterLast('.', "")
    val nameWithoutExt = fileName.substringBeforeLast('.')
    return "$dir$nameWithoutExt.sdr/metadata.$ext.lua"
}
