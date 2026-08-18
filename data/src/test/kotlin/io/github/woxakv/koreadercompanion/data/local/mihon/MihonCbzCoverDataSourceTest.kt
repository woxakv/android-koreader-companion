package io.github.woxakv.koreadercompanion.data.local.mihon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Exercises the folder-scan/chapter-selection/zip-read logic through
 * [MihonCbzCoverDataSource]'s `internal` helpers rather than the public
 * `fetchCoverBytes` entry point: that entry point calls
 * `DocumentFile.fromTreeUri` (a static Android method) and this file's
 * `downsample` (which calls `BitmapFactory`), neither of which is
 * available in a plain JUnit test with no `@RunWith(RobolectricTestRunner::class)`
 * - mirroring how [MihonBackupDataSourceTest] tests `resolveBackupFolder`/
 * `selectLatestBackupFile` directly rather than the top-level
 * `readLatestBackup`. All fixtures are synthetic, in-memory `.cbz`
 * (plain zip) files built with [ZipOutputStream] - no real device file is
 * ever committed here.
 */
class MihonCbzCoverDataSourceTest {

    private val context = mockk<Context>(relaxed = true)

    private val dataSource = MihonCbzCoverDataSource(
        context = context,
        dispatcherProvider = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
    )

    // Mirrors the private orchestration inside fetchCoverBytes, minus the
    // DocumentFile.fromTreeUri resolution and downsample step (both unmockable
    // here - see class KDoc above).
    private fun resolveCoverBytes(
        downloads: DocumentFile,
        mangaTitle: String,
        currentChapterName: String?,
        currentChapterNumber: Float,
    ): ByteArray? {
        val mangaFolder = dataSource.findMangaFolder(downloads, mangaTitle) ?: return null
        val cbzFiles = mangaFolder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".cbz", ignoreCase = true) == true }
        if (cbzFiles.isEmpty()) return null
        val target = dataSource.selectTargetCbz(cbzFiles, currentChapterName, currentChapterNumber)
            ?: return null
        return dataSource.readFirstPageBytes(target)
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    /**
     * Hand-built raw zip bytes for a single STORED entry with a
     * deferred-size data descriptor - `ZipOutputStream` can't produce this
     * shape itself (it requires known sizes upfront for STORED entries), but
     * this is the exact real-device pattern confirmed in a real Mihon
     * `.cbz` file during this plan's execution: already-compressed image
     * entries stored (not re-deflated) with their size deferred to a
     * trailing data descriptor. `java.util.zip.ZipInputStream` refuses this
     * combination outright ("only DEFLATED entries can have EXT
     * descriptor") - this is regression coverage for `readFirstPageBytes`'s
     * `ZipFile`-based fix, so a future accidental revert to
     * `ZipInputStream` fails a unit test instead of only failing silently
     * on a real device.
     */
    private fun buildStoredZipWithDataDescriptor(name: String, content: ByteArray): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(content) }.value.toInt()
        val localHeaderOffset = 0

        val out = ByteArrayOutputStream()

        // Local file header - size/crc fields left at 0, general-purpose
        // bit 3 set to defer them to the data descriptor below.
        out.writeIntLE(0x04034b50)
        out.writeShortLE(20)
        out.writeShortLE(0x0008)
        out.writeShortLE(0) // compression method: STORED
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeIntLE(0)
        out.writeIntLE(0)
        out.writeIntLE(0)
        out.writeShortLE(nameBytes.size)
        out.writeShortLE(0)
        out.write(nameBytes)

        out.write(content) // STORED: literal, uncompressed bytes

        out.writeIntLE(0x08074b50) // data descriptor signature (optional but common)
        out.writeIntLE(crc)
        out.writeIntLE(content.size)
        out.writeIntLE(content.size)

        val centralDirOffset = out.size()

        // Central directory header - the authoritative source of truth for
        // size/crc, which is what ZipFile reads from.
        out.writeIntLE(0x02014b50)
        out.writeShortLE(20)
        out.writeShortLE(20)
        out.writeShortLE(0x0008)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeIntLE(crc)
        out.writeIntLE(content.size)
        out.writeIntLE(content.size)
        out.writeShortLE(nameBytes.size)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeIntLE(0)
        out.writeIntLE(localHeaderOffset)
        out.write(nameBytes)

        val centralDirSize = out.size() - centralDirOffset

        // End of central directory record.
        out.writeIntLE(0x06054b50)
        out.writeShortLE(0)
        out.writeShortLE(0)
        out.writeShortLE(1)
        out.writeShortLE(1)
        out.writeIntLE(centralDirSize)
        out.writeIntLE(centralDirOffset)
        out.writeShortLE(0)

        return out.toByteArray()
    }

    private fun mockFolder(
        name: String,
        children: List<DocumentFile> = emptyList(),
        findFileResults: Map<String, DocumentFile?> = emptyMap(),
    ): DocumentFile {
        val folder = mockk<DocumentFile>(relaxed = true)
        every { folder.name } returns name
        every { folder.isDirectory } returns true
        every { folder.isFile } returns false
        every { folder.listFiles() } returns children.toTypedArray()
        findFileResults.forEach { (childName, result) -> every { folder.findFile(childName) } returns result }
        return folder
    }

    private fun mockCbzFile(name: String, zipBytes: ByteArray, lastModified: Long = 0L): DocumentFile {
        val uri = mockk<Uri>(relaxed = true)
        val file = mockk<DocumentFile>(relaxed = true)
        every { file.name } returns name
        every { file.isFile } returns true
        every { file.isDirectory } returns false
        every { file.lastModified() } returns lastModified
        every { file.uri } returns uri
        every { context.contentResolver.openInputStream(uri) } returns ByteArrayInputStream(zipBytes)
        return file
    }

    private val page1Bytes = byteArrayOf(1, 1, 1)
    private val page2Bytes = byteArrayOf(2, 2, 2)

    private fun chapterCbz(fileName: String, lastModified: Long = 0L): DocumentFile =
        mockCbzFile(
            fileName,
            buildZip("002.webp" to page2Bytes, "001.webp" to page1Bytes),
            lastModified,
        )

    @Test
    fun `finds manga folder across multiple source folders via exact match and returns matching chapter's first page`() {
        val cbz = chapterCbz("Scanlator_Chapter 70_abc123.cbz")
        val mangaFolder = mockFolder("One Piece", children = listOf(cbz))
        val sourceA = mockFolder("Comix (EN)", findFileResults = mapOf("One Piece" to null))
        val sourceB = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to mangaFolder))
        val downloads = mockFolder("downloads", children = listOf(sourceA, sourceB))

        val result = resolveCoverBytes(downloads, "One Piece", currentChapterName = null, currentChapterNumber = 70f)

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `falls back to normalized folder-name match when exact match fails`() {
        // On-disk folder differs from BackupManga.title only by punctuation/casing -
        // BookTitleMatcher.normalize collapses "Attack on Titan!" and "attack on titan"
        // to the same value.
        val cbz = chapterCbz("Scanlator_Chapter 1_abc123.cbz")
        val mangaFolder = mockFolder("Attack on Titan!", children = listOf(cbz))
        val source = mockFolder(
            "MangaDex",
            children = listOf(mangaFolder),
            findFileResults = mapOf("attack on titan" to null),
        )
        val downloads = mockFolder("downloads", children = listOf(source))

        val result = resolveCoverBytes(
            downloads,
            "attack on titan",
            currentChapterName = null,
            currentChapterNumber = 1f,
        )

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `chapter number regex does not cross-match a longer chapter number`() {
        // Real-world collision: "Chapter 7" must not match inside "Chapter 70".
        val chapter7 = chapterCbz("ScanlatorA_Chapter 7_aaaaaa.cbz")
        val chapter70 = chapterCbz("ScanlatorB_Chapter 70_bbbbbb.cbz")
        val mangaFolder = mockFolder("One Piece", children = listOf(chapter70, chapter7))
        val source = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to mangaFolder))
        val downloads = mockFolder("downloads", children = listOf(source))

        val result = resolveCoverBytes(downloads, "One Piece", currentChapterName = null, currentChapterNumber = 7f)

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `prefers the file matching both chapter number and chapter name among same-chapter scanlator duplicates`() {
        val wrongScanlator = mockCbzFile(
            "Unknown_Chapter 70_bbbbbb.cbz",
            buildZip("001.webp" to byteArrayOf(9, 9, 9)),
        )
        val rightScanlator = mockCbzFile(
            "VIZ Media_Chapter 70_ Special Edition_cccccc.cbz",
            buildZip("001.webp" to page1Bytes),
        )
        val mangaFolder = mockFolder("One Piece", children = listOf(wrongScanlator, rightScanlator))
        val source = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to mangaFolder))
        val downloads = mockFolder("downloads", children = listOf(source))

        val result = resolveCoverBytes(
            downloads,
            "One Piece",
            currentChapterName = "Special Edition",
            currentChapterNumber = 70f,
        )

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `falls back to most recently modified cbz when current chapter is not downloaded`() {
        val older = chapterCbz("Scanlator_Chapter 68_aaaaaa.cbz", lastModified = 1_000L)
        val newer = mockCbzFile(
            "Scanlator_Chapter 69_bbbbbb.cbz",
            buildZip("001.webp" to page1Bytes),
            lastModified = 2_000L,
        )
        val mangaFolder = mockFolder("One Piece", children = listOf(older, newer))
        val source = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to mangaFolder))
        val downloads = mockFolder("downloads", children = listOf(source))

        // Chapter 90 was never downloaded - neither file's name matches.
        val result = resolveCoverBytes(downloads, "One Piece", currentChapterName = null, currentChapterNumber = 90f)

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `returns null when no source folder has a matching manga folder`() {
        val source = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to null))
        val downloads = mockFolder("downloads", children = listOf(source))

        val result = resolveCoverBytes(downloads, "One Piece", currentChapterName = null, currentChapterNumber = 1f)

        assertNull(result)
    }

    @Test
    fun `returns null for an empty chapter folder with no cbz files`() {
        val mangaFolder = mockFolder("One Piece", children = emptyList())
        val source = mockFolder("MangaDex", findFileResults = mapOf("One Piece" to mangaFolder))
        val downloads = mockFolder("downloads", children = listOf(source))

        val result = resolveCoverBytes(downloads, "One Piece", currentChapterName = null, currentChapterNumber = 1f)

        assertNull(result)
    }

    @Test
    fun `returns null when the winning cbz has no image entries`() {
        val cbz = mockCbzFile("Scanlator_Chapter 1_abc123.cbz", buildZip("info.txt" to byteArrayOf(0)))

        val result = dataSource.readFirstPageBytes(cbz)

        assertNull(result)
    }

    @Test
    fun `page-name sorting picks 001 over 002 regardless of zip entry order`() {
        // 002.webp is stored before 001.webp in the zip entry stream (confirmed real-device
        // behavior) - the winner must be picked by name, not by stream position.
        val cbz = mockCbzFile(
            "Scanlator_Chapter 1_abc123.cbz",
            buildZip("002.webp" to page2Bytes, "001.webp" to page1Bytes, "010.jpg" to byteArrayOf(3, 3, 3)),
        )

        val result = dataSource.readFirstPageBytes(cbz)

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `reads a STORED entry with a deferred-size data descriptor - the real Mihon cbz shape`() {
        // Confirmed against a real device .cbz: Mihon stores already-compressed
        // image entries as STORED with size deferred to a trailing data
        // descriptor - java.util.zip.ZipInputStream refuses to read this
        // combination at all, which is exactly why readFirstPageBytes uses
        // ZipFile instead. See buildStoredZipWithDataDescriptor's doc comment.
        val zipBytes = buildStoredZipWithDataDescriptor("001.webp", page1Bytes)
        val cbz = mockCbzFile("Scanlator_Chapter 1_abc123.cbz", zipBytes)

        val result = dataSource.readFirstPageBytes(cbz)

        assertArrayEquals(page1Bytes, result)
    }

    @Test
    fun `formatChapterNumber drops trailing zero for whole numbers and keeps decimals`() {
        assertEquals("70", dataSource.formatChapterNumber(70f))
        assertEquals("90.5", dataSource.formatChapterNumber(90.5f))
    }
}
