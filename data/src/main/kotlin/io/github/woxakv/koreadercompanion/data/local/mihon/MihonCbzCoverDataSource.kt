package io.github.woxakv.koreadercompanion.data.local.mihon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import io.github.woxakv.koreadercompanion.domain.matching.BookTitleMatcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlinx.coroutines.withContext

private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

/**
 * Extracts a manga's cover art directly from a Mihon `.cbz` chapter
 * download, for the case where [MihonCoverDataSource]'s remote
 * `thumbnailUrl` fetch either isn't available or isn't wanted. Unlike the
 * `.tachibk` backup (a single well-known file), there's no source-name
 * mapping from `BackupManga.source` (a numeric id) to the on-disk source
 * folder name (e.g. "Comix (EN)") - so finding the right manga folder is a
 * scan: every source folder under `mihon/downloads/` is checked for a
 * subfolder matching the manga's title, first by exact name, then by
 * [BookTitleMatcher]-normalized name as a fallback for titles that differ
 * only by punctuation/casing on disk.
 *
 * No caching/memoization here, matching [MihonBackupDataSource]'s
 * convention - this reads fresh on every call.
 */
class MihonCbzCoverDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun fetchCoverBytes(
        mihonTreeUriString: String,
        mangaTitle: String,
        currentChapterName: String?,
        currentChapterNumber: Float,
    ): ByteArray? = withContext(dispatcherProvider.io) {
        runCatching {
            val granted = DocumentFile.fromTreeUri(context, Uri.parse(mihonTreeUriString))
                ?: return@runCatching null
            val downloads = granted.findFile("downloads")?.takeIf { it.isDirectory }
                ?: return@runCatching null

            val mangaFolder = findMangaFolder(downloads, mangaTitle) ?: return@runCatching null
            val cbzFiles = mangaFolder.listFiles()
                .filter { it.isFile && it.name?.endsWith(".cbz", ignoreCase = true) == true }
            if (cbzFiles.isEmpty()) return@runCatching null

            val target = selectTargetCbz(cbzFiles, currentChapterName, currentChapterNumber)
                ?: return@runCatching null

            val rawBytes = readFirstPageBytes(target) ?: return@runCatching null
            downsample(rawBytes)
        }.getOrNull()
    }

    internal fun findMangaFolder(downloads: DocumentFile, mangaTitle: String): DocumentFile? {
        val sourceFolders = downloads.listFiles().filter { it.isDirectory }

        sourceFolders.firstNotNullOfOrNull { sourceFolder -> sourceFolder.findFile(mangaTitle) }
            ?.let { return it }

        val normalizedTarget = BookTitleMatcher.normalize(mangaTitle)
        for (sourceFolder in sourceFolders) {
            val match = sourceFolder.listFiles().firstOrNull {
                it.isDirectory && BookTitleMatcher.normalize(it.name.orEmpty()) == normalizedTarget
            }
            if (match != null) return match
        }
        return null
    }

    internal fun selectTargetCbz(
        cbzFiles: List<DocumentFile>,
        currentChapterName: String?,
        currentChapterNumber: Float,
    ): DocumentFile? {
        val formattedNumber = formatChapterNumber(currentChapterNumber)
        val chapterRegex = Regex("(?<![0-9.])Chapter ${Regex.escape(formattedNumber)}(?![0-9.])")

        val chapterMatches = cbzFiles.filter { chapterRegex.containsMatchIn(it.name.orEmpty()) }

        if (!currentChapterName.isNullOrBlank()) {
            chapterMatches.firstOrNull { it.name.orEmpty().contains(currentChapterName) }
                ?.let { return it }
        }

        chapterMatches.firstOrNull()?.let { return it }

        return cbzFiles.maxByOrNull { it.lastModified() }
    }

    internal fun formatChapterNumber(chapterNumber: Float): String =
        if (chapterNumber == chapterNumber.toLong().toFloat()) {
            chapterNumber.toLong().toString()
        } else {
            chapterNumber.toString()
        }

    /**
     * Deliberately reads via [ZipFile] (Central Directory, random access),
     * not [java.util.zip.ZipInputStream] (sequential, local-file-headers
     * only) - device-verified: real Mihon `.cbz` files store their
     * already-compressed image entries as STORED with a deferred-size data
     * descriptor (a common pattern for content whose exact size wasn't
     * known until the download finished), a combination `ZipInputStream`
     * refuses outright (`ZipException: only DEFLATED entries can have EXT
     * descriptor`, confirmed against a real device `.cbz` - it isn't a
     * corrupt-file edge case, it's the normal shape). This is the exact
     * same class of bug already found and fixed for EPUB parsing in
     * `EpubOpfLocator.readZipEntry` (see this plan's Step 1) - same fix
     * applies here: `ZipFile` needs real random access, which a SAF
     * `content://` stream doesn't give directly, so the stream is copied to
     * a throwaway temp file first, mirroring `StatisticsDatabaseCopier`'s
     * established SAF-stream-to-local-file pattern. This does mean the
     * whole `.cbz` (up to ~70MB) is copied locally rather than only the
     * winning entry's bytes - an unavoidable trade-off once `ZipInputStream`
     * is off the table, not a design choice made lightly.
     */
    internal fun readFirstPageBytes(cbzFile: DocumentFile): ByteArray? {
        val input = context.contentResolver.openInputStream(cbzFile.uri)
            ?: throw FileNotFoundException(cbzFile.uri.toString())
        val tempFile = File.createTempFile("mihon_cbz_", ".tmp")
        try {
            input.use { inputStream -> tempFile.outputStream().use { output -> inputStream.copyTo(output) } }
            ZipFile(tempFile).use { zipFile ->
                val bestEntry = zipFile.entries().asSequence()
                    .filter { entry -> !entry.isDirectory && IMAGE_EXTENSIONS.any { entry.name.endsWith(it, ignoreCase = true) } }
                    .minByOrNull { it.name }
                    ?: return null
                return zipFile.getInputStream(bestEntry).use { it.readBytes() }
            }
        } finally {
            tempFile.delete()
        }
    }

    // Duplicated from EpubCoverDataSource.downsample rather than shared across the
    // unrelated epub/mihon packages, matching this module's established convention
    // of duplicating small helpers (see CombinedWidgetContent.kt's "Duplicated
    // per-file" comment).
    private fun downsample(rawBytes: ByteArray, maxDimensionPx: Int = 480): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return rawBytes

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxDimensionPx || bounds.outHeight / (sampleSize * 2) >= maxDimensionPx) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return rawBytes

        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            output.toByteArray()
        }
    }
}
