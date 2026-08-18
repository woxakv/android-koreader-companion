package io.github.woxakv.koreadercompanion.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.db.BookStatsDto
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsDatabaseCopier
import io.github.woxakv.koreadercompanion.data.local.db.StatisticsSqliteDataSource
import io.github.woxakv.koreadercompanion.data.local.epub.EpubCoverDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.BookProgressDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.HistoryDataSource
import io.github.woxakv.koreadercompanion.data.local.lua.ReaderSettingsDataSource
import io.github.woxakv.koreadercompanion.data.local.saf.KoreaderFileResolver
import io.github.woxakv.koreadercompanion.data.local.saf.StorageAccessLocalDataSource
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import java.io.File

class CurrentBookRepositoryImplTest {

    // Mirrors the private constants in CurrentBookRepositoryImpl.
    private val statsPath = "/storage/emulated/0/koreader/settings/statistics.sqlite3"
    private val settingsPath = "/storage/emulated/0/koreader/settings.reader.lua"
    private val bookPath = "/storage/emulated/0/Books/Foo.epub"

    private val storageAccessLocalDataSource = mockk<StorageAccessLocalDataSource>()
    private val koreaderFileResolver = mockk<KoreaderFileResolver>()
    private val readerSettingsDataSource = mockk<ReaderSettingsDataSource>()
    private val historyDataSource = mockk<HistoryDataSource>()
    private val statisticsDatabaseCopier = mockk<StatisticsDatabaseCopier>()
    private val statisticsSqliteDataSource = mockk<StatisticsSqliteDataSource>()
    private val epubCoverDataSource = mockk<EpubCoverDataSource>()
    private val bookProgressDataSource = mockk<BookProgressDataSource>()

    private val repository = CurrentBookRepositoryImpl(
        storageAccessLocalDataSource,
        koreaderFileResolver,
        readerSettingsDataSource,
        historyDataSource,
        statisticsDatabaseCopier,
        statisticsSqliteDataSource,
        epubCoverDataSource,
        bookProgressDataSource,
    )

    private val koreaderRoot = mockk<DocumentFile>(relaxed = true)
    private val booksRoot = mockk<DocumentFile>(relaxed = true)
    private val statsDocumentFile = mockk<DocumentFile>(relaxed = true)
    private val settingsDocumentFile = mockk<DocumentFile>(relaxed = true)
    private val epubDocumentFile = mockk<DocumentFile>(relaxed = true)
    private val sidecarDocumentFile = mockk<DocumentFile>(relaxed = true)
    // Mirrors sidecarMetadataPath(bookPath) in CurrentBookRepositoryImpl.
    private val sidecarPath = "/storage/emulated/0/Books/Foo.sdr/metadata.epub.lua"
    // The original file's own (tree-scoped) SAF URI - not a copy, so
    // KOReader can find the reading-position sidecar next to the real file.
    private val epubUri = mockk<Uri>(relaxed = true)
    private val dbFile = File("fake_stats_cache_for_test.sqlite3")
    // withCachedDatabase() takes the copy+read block as a parameter rather
    // than returning a File directly, so the mock has to capture that block
    // and invoke it with dbFile to exercise the same downstream stats/cover
    // logic these tests were already exercising against copyToCache()'s
    // returned file.
    private val cachedDatabaseBlockSlot = slot<suspend (File) -> Any?>()

    private val bookStats = BookStatsDto(
        id = 2L,
        title = "L'Attentat",
        authors = "Yasmina Khadra",
        lastOpen = 1786395275L,
        pages = 364,
        totalReadTime = 8688L,
        totalReadPages = 148,
    )

    @Before
    fun setUp() {
        coEvery { storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.KOREADER) } returns "content://tree/primary:koreader"
        coEvery { storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.BOOKS) } returns "content://tree/primary:Books"
        every { storageAccessLocalDataSource.resolveGrantedRoot("content://tree/primary:koreader") } returns koreaderRoot
        every { storageAccessLocalDataSource.resolveGrantedRoot("content://tree/primary:Books") } returns booksRoot
        every { koreaderFileResolver.resolve(koreaderRoot, statsPath) } returns statsDocumentFile
        coEvery {
            statisticsDatabaseCopier.withCachedDatabase(statsDocumentFile, any(), capture(cachedDatabaseBlockSlot))
        } coAnswers {
            cachedDatabaseBlockSlot.captured(dbFile)
        }
        coEvery { statisticsSqliteDataSource.getRecentBookStats(dbFile, any()) } returns listOf(bookStats)
        coEvery { statisticsSqliteDataSource.getMaxPageRead(dbFile, 2L) } returns 176
        every { koreaderFileResolver.resolve(koreaderRoot, settingsPath) } returns settingsDocumentFile
        coEvery { readerSettingsDataSource.readLastFile(settingsDocumentFile) } returns bookPath
        every { koreaderFileResolver.resolve(booksRoot, bookPath) } returns epubDocumentFile
        // The epub's own title now drives which stats row is picked, so every
        // test needs it stubbed; tests that care override it below.
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        every { epubDocumentFile.uri } returns epubUri
        every { epubUri.toString() } returns "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2FFoo.epub"
    }

    @Test
    fun `happy path pairs stats with a matching cover`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertEquals("Yasmina Khadra", book.author)
        assertEquals(176, book.progress.currentPage)
        assertEquals(364, book.progress.totalPages)
        assertArrayEquals(byteArrayOf(1, 2, 3), book.coverImageBytes)
        assertEquals(epubUri.toString(), book.bookContentUriString)
    }

    @Test
    fun `percent_finished from the sidecar overrides maxPageRead as the current page`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns 0.5

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        // 0.5 * 364 pages = 182, not the maxPageRead-derived 176.
        assertEquals(182, book.progress.currentPage)
        assertEquals(364, book.progress.totalPages)
    }

    @Test
    fun `sidecar readable but missing percent_finished falls back to maxPageRead`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals(176, book.progress.currentPage)
    }

    @Test
    fun `sidecar not resolvable falls back to maxPageRead`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals(176, book.progress.currentPage)
        coVerify(exactly = 0) { bookProgressDataSource.readPercentFinished(any()) }
    }

    @Test
    fun `no Books grant falls back to maxPageRead for current page too`() = runBlocking {
        coEvery { storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.BOOKS) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals(176, book.progress.currentPage)
        coVerify(exactly = 0) { bookProgressDataSource.readPercentFinished(any()) }
    }

    @Test
    fun `title mismatch yields stats without a cover but keeps the open-in-KOReader URI`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "A Completely Different Book"
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns 0.5

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertNull(book.coverImageBytes)
        assertEquals(epubUri.toString(), book.bookContentUriString)
        coVerify(exactly = 0) { epubCoverDataSource.extractCoverBytes(any()) }
        // The open book and the fallback stats row are not the same book, so
        // scaling one's percent_finished by the other's page count would be
        // exactly the cross-contamination this guard exists to prevent:
        // maxPageRead, not 0.5 * 364 = 182.
        assertEquals(176, book.progress.currentPage)
    }

    @Test
    fun `no Books grant yields stats without a cover or an open-in-KOReader URI`() = runBlocking {
        coEvery { storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.BOOKS) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertNull(book.coverImageBytes)
        assertNull(book.bookContentUriString)
        coVerify(exactly = 0) { epubCoverDataSource.extractTitle(any()) }
    }

    @Test
    fun `a book inside another app's private storage gets an explanatory note, not a failed lookup`() = runBlocking {
        val privatePath = "/storage/emulated/0/Android/data/org.koreader.launcher/files/Foo.epub"
        coEvery { readerSettingsDataSource.readLastFile(settingsDocumentFile) } returns privatePath

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertNull(book.coverImageBytes)
        assertNull(book.bookContentUriString)
        assertTrue(book.bookAccessNote?.contains("private app folder") == true)
        // The doomed SAF lookup is skipped entirely rather than attempted and failing.
        coVerify(exactly = 0) { koreaderFileResolver.resolve(booksRoot, any()) }
    }

    @Test
    fun `missing koreader grant fails with PermissionNotGranted`() = runBlocking {
        coEvery { storageAccessLocalDataSource.grantedTreeUriString(StorageTarget.KOREADER) } returns null

        val result = repository.getCurrentBook()

        assertEquals(Try.Failure(KoreaderError.PermissionNotGranted), result)
    }

    @Test
    fun `empty book table fails with NoBookFound`() = runBlocking {
        coEvery { statisticsSqliteDataSource.getRecentBookStats(dbFile, any()) } returns emptyList()

        val result = repository.getCurrentBook()

        assertEquals(Try.Failure(KoreaderError.NoBookFound), result)
    }

    @Test
    fun `picks the stats row matching the open book, not the most recently opened one`() = runBlocking {
        val moreRecentOtherBook = BookStatsDto(
            id = 9L,
            title = "Some Other Book",
            authors = "Someone Else",
            lastOpen = 1786400000L,
            pages = 2989,
            totalReadTime = 100L,
            totalReadPages = 10,
        )
        coEvery { statisticsSqliteDataSource.getRecentBookStats(dbFile, any()) } returns
            listOf(moreRecentOtherBook, bookStats)
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns 0.5

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertEquals("Yasmina Khadra", book.author)
        assertEquals(364, book.progress.totalPages)
        // 0.5 scaled by the matched book's 364 pages, not the newer row's 2989.
        assertEquals(182, book.progress.currentPage)
    }

    @Test
    fun `no matching stats row falls back to the most recent one with maxPageRead`() = runBlocking {
        val moreRecentOtherBook = BookStatsDto(
            id = 9L,
            title = "Some Other Book",
            authors = "Someone Else",
            lastOpen = 1786400000L,
            pages = 2989,
            totalReadTime = 100L,
            totalReadPages = 10,
        )
        coEvery { statisticsSqliteDataSource.getRecentBookStats(dbFile, any()) } returns
            listOf(moreRecentOtherBook, bookStats)
        coEvery { statisticsSqliteDataSource.getMaxPageRead(dbFile, 9L) } returns 42
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "A Completely Different Book"
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns 0.5

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("Some Other Book", book.title)
        assertEquals(2989, book.progress.totalPages)
        // maxPageRead, not 0.5 * 2989 - the sidecar percent belongs to a
        // different book than the fallback stats row.
        assertEquals(42, book.progress.currentPage)
        assertNull(book.coverImageBytes)
    }

    @Test
    fun `book marked complete in its sidecar fails with NoBookFound instead of a stale card`() = runBlocking {
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readStatus(sidecarDocumentFile) } returns "complete"

        val result = repository.getCurrentBook()

        assertEquals(Try.Failure(KoreaderError.NoBookFound), result)
    }

    @Test
    fun `book marked on hold in its sidecar fails with NoBookFound instead of a stale card`() = runBlocking {
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readStatus(sidecarDocumentFile) } returns "on_hold"

        val result = repository.getCurrentBook()

        assertEquals(Try.Failure(KoreaderError.NoBookFound), result)
    }

    @Test
    fun `sidecar with a summary table but no status key behaves as before this step`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns sidecarDocumentFile
        coEvery { bookProgressDataSource.readStatus(sidecarDocumentFile) } returns null
        coEvery { bookProgressDataSource.readPercentFinished(sidecarDocumentFile) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertEquals(176, book.progress.currentPage)
    }

    @Test
    fun `sidecar with no summary table at all behaves as before this step`() = runBlocking {
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)
        every { koreaderFileResolver.resolve(booksRoot, sidecarPath) } returns null

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals("L'Attentat", book.title)
        assertEquals(176, book.progress.currentPage)
    }

    @Test
    fun `duplicate titles resolve to the first row in last_open order`() = runBlocking {
        val staleDuplicate = BookStatsDto(
            id = 7L,
            title = "L'Attentat",
            authors = "Yasmina Khadra",
            lastOpen = 1000L,
            pages = 999,
            totalReadTime = 10L,
            totalReadPages = 1,
        )
        // Same normalized title, two distinct rows - the newer one wins simply
        // by coming first in last_open DESC order. Accepted limitation: titles
        // are the only identity available to match on.
        coEvery { statisticsSqliteDataSource.getRecentBookStats(dbFile, any()) } returns
            listOf(bookStats, staleDuplicate)
        coEvery { epubCoverDataSource.extractTitle(epubDocumentFile) } returns "L'Attentat"
        coEvery { epubCoverDataSource.extractCoverBytes(epubDocumentFile) } returns byteArrayOf(1, 2, 3)

        val result = repository.getCurrentBook()

        assertTrue(result is Try.Success)
        val book = (result as Try.Success).value
        assertEquals(364, book.progress.totalPages)
        assertEquals(176, book.progress.currentPage)
    }
}
