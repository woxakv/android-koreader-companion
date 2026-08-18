package io.github.woxakv.koreadercompanion.data.local.db

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StatisticsDatabaseCopier"

/**
 * SQLiteDatabase can't open a content:// SAF URI directly, so this copies
 * statistics.sqlite3 into the app's private cache before it's queried. The
 * file is small and cheap to re-copy on every refresh; there's no need to
 * keep it in sync live.
 *
 * `@Singleton` is required, not incidental: [withCachedDatabase] is called
 * concurrently by multiple widgets' independent Glance `SessionWorker`
 * compositions during a single [WidgetRefreshWorker] run, all writing to the
 * same fixed cache path. The [mutex] below only serializes those writers if
 * every caller shares one instance of this class - which Hilt only
 * guarantees when it's scoped to the `SingletonComponent`.
 *
 * The mutex protects the copy *and* the caller's read together, not just the
 * copy: callers open and query the returned file, and if a second caller's
 * copy overwrote the fixed cache path mid-read, the read could hit a
 * partially-overwritten file and throw a genuine `SQLiteException`. Routing
 * every caller through [withCachedDatabase] - the only way to obtain and use
 * a cache file - keeps the whole copy+read cycle under one lock.
 */
@Singleton
class StatisticsDatabaseCopier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val mutex = Mutex()

    suspend fun <T> withCachedDatabase(
        documentFile: DocumentFile,
        cacheFileName: String = "statistics_cache.sqlite3",
        block: suspend (File) -> T,
    ): T = withContext(dispatcherProvider.io) {
        mutex.withLock {
            val file = copyToCacheLocked(documentFile, cacheFileName)
            block(file)
        }
    }

    private fun copyToCacheLocked(
        documentFile: DocumentFile,
        cacheFileName: String,
    ): File {
        val outFile = File(context.cacheDir, cacheFileName)
        val input = context.contentResolver.openInputStream(documentFile.uri)
            ?: throw FileNotFoundException(documentFile.uri.toString())
        val tempFile = File.createTempFile("statistics_cache", ".tmp", context.cacheDir)
        input.use { inputStream ->
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
        }
        if (!tempFile.renameTo(outFile)) {
            tempFile.copyTo(outFile, overwrite = true)
            tempFile.delete()
        }

        copySidecarIfPresent(documentFile, "-wal", "$cacheFileName-wal")
        copySidecarIfPresent(documentFile, "-shm", "$cacheFileName-shm")

        return outFile
    }

    /**
     * Copies a WAL-mode sidecar file (`-wal`/`-shm`) alongside the main
     * cached database, if it exists on the source side. Sidecars are
     * optional - most of the time KOReader has already checkpointed and
     * there's nothing to copy, which is not an error. If a sidecar exists
     * but its copy fails for some other reason, that must not fail the
     * caller's main file copy, which already succeeded above.
     */
    private fun copySidecarIfPresent(
        documentFile: DocumentFile,
        suffix: String,
        cacheFileName: String,
    ) {
        val sidecar = documentFile.parentFile?.findFile("${documentFile.name}$suffix") ?: return
        try {
            val outFile = File(context.cacheDir, cacheFileName)
            val input = context.contentResolver.openInputStream(sidecar.uri)
                ?: throw FileNotFoundException(sidecar.uri.toString())
            val tempFile = File.createTempFile("statistics_cache_sidecar", ".tmp", context.cacheDir)
            input.use { inputStream ->
                tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            }
            if (!tempFile.renameTo(outFile)) {
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Failed to copy sidecar file ${sidecar.uri}", error)
        }
    }
}
