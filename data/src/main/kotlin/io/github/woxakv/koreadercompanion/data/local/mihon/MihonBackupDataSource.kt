package io.github.woxakv.koreadercompanion.data.local.mihon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

private const val BACKUP_FILE_SUFFIX = ".tachibk"

/**
 * Locates and decodes the most recent Mihon `.tachibk` autobackup file
 * under a granted SAF tree. Unlike KOReader's statistics database, a
 * `.tachibk` file doesn't need a real `java.io.File` handle - it's read
 * directly as a byte stream, so there's no cache-copy step like
 * [io.github.woxakv.koreadercompanion.data.local.db.StatisticsDatabaseCopier].
 *
 * No caching/memoization here either: this reads fresh on every call. The
 * file is a few MB at most and this isn't a hot path.
 */
class MihonBackupDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    /**
     * Resolves which folder actually holds the `.tachibk` files: the
     * `autobackup` subfolder when [granted] is a wide `mihon/`-rooted grant
     * (current onboarding/Config hint - see MIHON_FOLDER_HINT_URI), or
     * [granted] itself when it's an older, narrower grant made before this
     * app widened the Mihon grant to `mihon/` (see
     * .claude/plans/.../03-widen-mihon-grant.md) - those grants are rooted
     * directly at `autobackup/`, so `findFile("autobackup")` finds nothing
     * there and this falls back to the root unchanged. Keeps already-granted
     * narrow trees working exactly as before, with zero forced migration.
     */
    internal fun resolveBackupFolder(granted: DocumentFile): DocumentFile =
        granted.findFile("autobackup")?.takeIf { it.isDirectory } ?: granted

    internal fun selectLatestBackupFile(granted: DocumentFile): DocumentFile? =
        resolveBackupFolder(granted).listFiles()
            .filter { it.name?.endsWith(BACKUP_FILE_SUFFIX) == true }
            .maxByOrNull { it.lastModified() }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun readLatestBackup(treeUriString: String): Backup? = withContext(dispatcherProvider.io) {
        val granted = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return@withContext null
        val latest = selectLatestBackupFile(granted) ?: return@withContext null

        val input = context.contentResolver.openInputStream(latest.uri)
            ?: throw FileNotFoundException(latest.uri.toString())
        val bytes = GZIPInputStream(input).use { it.readBytes() }
        ProtoBuf.decodeFromByteArray<Backup>(bytes)
    }

    /**
     * True when [treeUriString]'s granted tree has no `autobackup`
     * subfolder - i.e. it's an old, narrow grant rooted directly at
     * `autobackup/` (see [resolveBackupFolder]). Used by ConfigScreen to
     * hint that re-granting the wider `mihon/` folder is needed before CBZ
     * cover art becomes available. Returns false (no hint) if the tree
     * can't be resolved at all - that's a different, unrelated failure mode.
     */
    suspend fun isNarrowGrant(treeUriString: String): Boolean = withContext(dispatcherProvider.io) {
        val granted = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString)) ?: return@withContext false
        resolveBackupFolder(granted) === granted
    }
}
