package io.github.woxakv.koreadercompanion.data.local.mihon

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Decode-correctness check for the hand-maintained `@ProtoNumber` mapping
 * in [Backup]/[BackupManga]/[BackupChapter]/[BackupHistory]. Builds a
 * synthetic `Backup` in-memory (never a real `.tachibk` from a device -
 * that would be a real person's manga reading history and doesn't belong
 * in version control), encodes it exactly like a real Mihon backup
 * (Protobuf, then gzip), decodes it back through the same
 * [ProtoBuf.decodeFromByteArray] call [MihonBackupDataSource] uses, and
 * asserts every field survives the round trip. This catches a wrong
 * `@ProtoNumber` or field-type mismatch immediately, rather than only
 * discovering it via on-device testing several steps later.
 */
@OptIn(ExperimentalSerializationApi::class)
class MihonBackupDataSourceTest {

    @Test
    fun `synthetic backup survives protobuf encode, gzip, and decode round trip`() {
        val original = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 2499283573021220255L,
                    url = "/manga/one-piece",
                    title = "One Piece",
                    favorite = true,
                    lastModifiedAt = 1_755_000_000_000L,
                    chapters = listOf(
                        BackupChapter(
                            url = "/manga/one-piece/chapter-1100",
                            name = "Chapter 1100",
                            read = true,
                            lastPageRead = 17,
                            chapterNumber = 1100F,
                        ),
                        BackupChapter(
                            url = "/manga/one-piece/chapter-1101",
                            name = "Chapter 1101",
                            read = false,
                            lastPageRead = 3,
                            chapterNumber = 1101F,
                        ),
                    ),
                    history = listOf(
                        BackupHistory(
                            url = "/manga/one-piece/chapter-1100",
                            lastRead = 1_755_100_000_000L,
                            readDuration = 720_000L,
                        ),
                    ),
                ),
                BackupManga(
                    source = 1998944621602463790L,
                    url = "/manga/frieren",
                    title = "Frieren: Beyond Journey's End",
                    favorite = false,
                    lastModifiedAt = 1_754_000_000_000L,
                    chapters = listOf(
                        BackupChapter(
                            url = "/manga/frieren/chapter-60",
                            name = "Chapter 60",
                            read = true,
                            lastPageRead = 22,
                            chapterNumber = 60F,
                        ),
                    ),
                    history = listOf(
                        BackupHistory(
                            url = "/manga/frieren/chapter-60",
                            lastRead = 1_754_200_000_000L,
                            readDuration = 540_000L,
                        ),
                    ),
                ),
            ),
        )

        val protoBytes = ProtoBuf.encodeToByteArray(original)
        val gzipped = ByteArrayOutputStream().use { byteStream ->
            GZIPOutputStream(byteStream).use { it.write(protoBytes) }
            byteStream.toByteArray()
        }

        val ungzipped = GZIPInputStream(gzipped.inputStream()).use { it.readBytes() }
        val decoded = ProtoBuf.decodeFromByteArray<Backup>(ungzipped)

        assertEquals(original, decoded)
        assertEquals(2, decoded.backupManga.size)

        val onePiece = decoded.backupManga[0]
        assertEquals("One Piece", onePiece.title)
        assertEquals(2499283573021220255L, onePiece.source)
        assertEquals(true, onePiece.favorite)
        assertEquals(1_755_000_000_000L, onePiece.lastModifiedAt)
        assertEquals(2, onePiece.chapters.size)
        assertEquals(1100F, onePiece.chapters[0].chapterNumber)
        assertEquals(17L, onePiece.chapters[0].lastPageRead)
        assertEquals(1, onePiece.history.size)
        assertEquals(720_000L, onePiece.history[0].readDuration)
        assertEquals(1_755_100_000_000L, onePiece.history[0].lastRead)
        assertEquals("/manga/one-piece/chapter-1100", onePiece.history[0].url)

        val frieren = decoded.backupManga[1]
        assertEquals(false, frieren.favorite)
        assertEquals(540_000L, frieren.history[0].readDuration)
    }

    @Test
    fun `empty backup manga list round trips to an empty list`() {
        val original = Backup(backupManga = emptyList())

        val protoBytes = ProtoBuf.encodeToByteArray(original)
        val decoded = ProtoBuf.decodeFromByteArray<Backup>(protoBytes)

        assertEquals(emptyList<BackupManga>(), decoded.backupManga)
    }

    // Fixture for the plan 03-widen-mihon-grant.md dual-shape resolution tests below: a
    // real Context/DispatcherProvider is never exercised by resolveBackupFolder/
    // selectLatestBackupFile (they're plain synchronous DocumentFile lookups), so these
    // are just inert stand-ins to satisfy the constructor.
    private val dataSource = MihonBackupDataSource(
        context = mockk<Context>(relaxed = true),
        dispatcherProvider = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        },
    )

    @Test
    fun `resolveBackupFolder descends into autobackup when the wide mihon grant has it`() {
        val autobackupFolder = mockk<DocumentFile>(relaxed = true) { every { isDirectory } returns true }
        val granted = mockk<DocumentFile>(relaxed = true) {
            every { findFile("autobackup") } returns autobackupFolder
        }

        val resolved = dataSource.resolveBackupFolder(granted)

        assertSame(autobackupFolder, resolved)
    }

    @Test
    fun `resolveBackupFolder falls back to the granted root for an old narrow autobackup-rooted grant`() {
        // Old grants are rooted directly at autobackup/, so findFile("autobackup") finds
        // nothing there - this is the exact signal ConfigScreen's re-grant hint reuses.
        val granted = mockk<DocumentFile>(relaxed = true) {
            every { findFile("autobackup") } returns null
        }

        val resolved = dataSource.resolveBackupFolder(granted)

        assertSame(granted, resolved)
    }

    @Test
    fun `resolveBackupFolder falls back to the granted root when autobackup exists but isn't a directory`() {
        val notADirectory = mockk<DocumentFile>(relaxed = true) { every { isDirectory } returns false }
        val granted = mockk<DocumentFile>(relaxed = true) {
            every { findFile("autobackup") } returns notADirectory
        }

        val resolved = dataSource.resolveBackupFolder(granted)

        assertSame(granted, resolved)
    }

    @Test
    fun `selectLatestBackupFile finds tachibk files directly at the root for an old narrow grant`() {
        val older = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "older.tachibk"
            every { lastModified() } returns 1_000L
        }
        val newer = mockk<DocumentFile>(relaxed = true) {
            every { name } returns "newer.tachibk"
            every { lastModified() } returns 2_000L
        }
        val unrelated = mockk<DocumentFile>(relaxed = true) { every { name } returns "notes.txt" }
        val granted = mockk<DocumentFile>(relaxed = true) {
            every { findFile("autobackup") } returns null
            every { listFiles() } returns arrayOf(older, unrelated, newer)
        }

        val latest = dataSource.selectLatestBackupFile(granted)

        assertSame(newer, latest)
    }

    @Test
    fun `selectLatestBackupFile returns null when the resolved folder has no tachibk files`() {
        val granted = mockk<DocumentFile>(relaxed = true) {
            every { findFile("autobackup") } returns null
            every { listFiles() } returns emptyArray()
        }

        val latest = dataSource.selectLatestBackupFile(granted)

        assertNull(latest)
    }
}
