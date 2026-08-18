package io.github.woxakv.koreadercompanion.data.local.db

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.coroutines.StandardDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Regression coverage for the writer/writer race described in
 * [StatisticsDatabaseCopier]: multiple widgets refreshing at once used to
 * call the copy step concurrently against the same fixed cache path with no
 * synchronization, producing a corrupted (sometimes deleted-by-sqlite)
 * output file. The [Mutex]/temp-file-then-rename fix, now covering both the
 * copy and the caller's read via [StatisticsDatabaseCopier.withCachedDatabase],
 * should make concurrent callers serialize so the resulting file is always a
 * complete, valid SQLite database - never a partial write from an
 * interleaved copy.
 *
 * Runs under Robolectric purely to get a real, native-backed
 * [SQLiteDatabase] to validate the output file with - Context/ContentResolver
 * /DocumentFile are all mocked, matching how this class is tested elsewhere
 * (see CurrentBookRepositoryImplTest).
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsDatabaseCopierTest {

    private lateinit var cacheDir: File
    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val dispatcherProvider = StandardDispatcherProvider()
    private val copier = StatisticsDatabaseCopier(context, dispatcherProvider)

    @Before
    fun setUp() {
        cacheDir = Files.createTempDirectory("statistics_copier_test").toFile()
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns contentResolver
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `concurrent copyToCache calls both succeed and leave a valid sqlite file`() = runBlocking {
        val bytesA = fakeSqliteBytes(marker = "A")
        val bytesB = fakeSqliteBytes(marker = "B")

        val documentFileA = fakeDocumentFile(bytesA)
        val documentFileB = fakeDocumentFile(bytesB)

        val results = coroutineScope {
            val deferredA = async { copier.withCachedDatabase(documentFileA) { file -> file } }
            val deferredB = async { copier.withCachedDatabase(documentFileB) { file -> file } }
            awaitAll(deferredA, deferredB)
        }

        // Both calls completed without throwing (awaitAll would have
        // propagated a SQLiteCantOpenDatabaseException-style failure).
        val outFile = results.first()
        assertTrue("both calls should resolve to the same cache path", results.all { it == outFile })

        assertTrue("output file should exist", outFile.exists())

        // No leftover temp files: either the rename succeeded, or the
        // copyTo+delete fallback ran and cleaned up after itself.
        val leftoverTempFiles = cacheDir.listFiles { file -> file.name.endsWith(".tmp") }.orEmpty()
        assertTrue("no .tmp files should remain: ${leftoverTempFiles.toList()}", leftoverTempFiles.isEmpty())

        // The resulting file must be a fully-formed, openable SQLite
        // database - never a partial write from an interleaved copy.
        val db = SQLiteDatabase.openDatabase(outFile.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = db.rawQuery("SELECT marker FROM marker_table", null)
            cursor.use {
                assertTrue(it.moveToFirst())
                val marker = it.getString(0)
                assertTrue("marker should be either A or B, was $marker", marker == "A" || marker == "B")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `copyToCache also copies wal and shm sidecar files when present`() = runBlocking {
        val mainBytes = fakeSqliteBytes(marker = "MAIN")
        val walBytes = "wal-content".toByteArray()
        val shmBytes = "shm-content".toByteArray()

        val walDocumentFile = fakeSidecarFile(walBytes)
        val shmDocumentFile = fakeSidecarFile(shmBytes)
        val parent = fakeParentFile(walFile = walDocumentFile, shmFile = shmDocumentFile)
        val documentFile = fakeDocumentFile(mainBytes, parent = parent)

        copier.withCachedDatabase(documentFile) { outFile ->
            val walCacheFile = File(cacheDir, "${outFile.name}-wal")
            val shmCacheFile = File(cacheDir, "${outFile.name}-shm")
            assertTrue("wal sidecar should be copied to cache", walCacheFile.exists())
            assertTrue("shm sidecar should be copied to cache", shmCacheFile.exists())
            assertTrue("wal sidecar content should match source", walCacheFile.readBytes().contentEquals(walBytes))
            assertTrue("shm sidecar content should match source", shmCacheFile.readBytes().contentEquals(shmBytes))
        }
    }

    @Test
    fun `copyToCache succeeds when no sidecar files exist`() = runBlocking {
        val mainBytes = fakeSqliteBytes(marker = "MAIN")
        val parent = fakeParentFile(walFile = null, shmFile = null)
        val documentFile = fakeDocumentFile(mainBytes, parent = parent)

        copier.withCachedDatabase(documentFile) { outFile ->
            assertTrue("main output file should exist", outFile.exists())
            assertTrue("no wal sidecar should be created", !File(cacheDir, "${outFile.name}-wal").exists())
            assertTrue("no shm sidecar should be created", !File(cacheDir, "${outFile.name}-shm").exists())
        }
    }

    @Test
    fun `copyToCache still succeeds when a sidecar copy fails`() = runBlocking {
        val mainBytes = fakeSqliteBytes(marker = "MAIN")

        val walUri = mockk<Uri>(relaxed = true)
        val walDocumentFile = mockk<DocumentFile>(relaxed = true)
        every { walDocumentFile.uri } returns walUri
        every { contentResolver.openInputStream(walUri) } throws IOException("boom")

        val parent = fakeParentFile(walFile = walDocumentFile, shmFile = null)
        val documentFile = fakeDocumentFile(mainBytes, parent = parent)

        copier.withCachedDatabase(documentFile) { outFile ->
            assertTrue("main output file should still exist despite sidecar failure", outFile.exists())
            assertTrue("no wal sidecar should exist after failed copy", !File(cacheDir, "${outFile.name}-wal").exists())
        }
    }

    private fun fakeDocumentFile(
        bytes: ByteArray,
        name: String = "statistics.sqlite3",
        parent: DocumentFile? = null,
    ): DocumentFile {
        val uri = mockk<Uri>(relaxed = true)
        val documentFile = mockk<DocumentFile>(relaxed = true)
        every { documentFile.uri } returns uri
        every { documentFile.name } returns name
        every { documentFile.parentFile } returns parent
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes) }
        return documentFile
    }

    private fun fakeParentFile(
        walFile: DocumentFile?,
        shmFile: DocumentFile?,
        mainName: String = "statistics.sqlite3",
    ): DocumentFile {
        val parent = mockk<DocumentFile>()
        every { parent.findFile("$mainName-wal") } returns walFile
        every { parent.findFile("$mainName-shm") } returns shmFile
        return parent
    }

    private fun fakeSidecarFile(bytes: ByteArray): DocumentFile {
        val uri = mockk<Uri>(relaxed = true)
        val documentFile = mockk<DocumentFile>(relaxed = true)
        every { documentFile.uri } returns uri
        every { contentResolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes) }
        return documentFile
    }

    private fun fakeSqliteBytes(marker: String): ByteArray {
        val sourceFile = File.createTempFile("statistics_copier_source_$marker", ".sqlite3")
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(sourceFile, null)
            db.execSQL("CREATE TABLE marker_table (marker TEXT)")
            db.execSQL("INSERT INTO marker_table (marker) VALUES ('$marker')")
            db.close()
            return sourceFile.readBytes()
        } finally {
            sourceFile.delete()
        }
    }
}
