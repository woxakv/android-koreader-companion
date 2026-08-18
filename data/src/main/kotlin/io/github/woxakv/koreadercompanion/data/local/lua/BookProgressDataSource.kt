package io.github.woxakv.koreadercompanion.data.local.lua

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BookProgressDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val luaChunkLoader: LuaChunkLoader,
) {
    suspend fun readPercentFinished(documentFile: DocumentFile): Double? {
        val stream = context.contentResolver.openInputStream(documentFile.uri) ?: return null
        val table = stream.use { luaChunkLoader.loadTable(it, "metadata.lua") }
        val value = table.get("percent_finished")
        return if (value.isnil()) null else value.todouble()
    }

    /**
     * Unlike `percent_finished`, `status` isn't a flat top-level key - it
     * lives nested inside the sidecar's `summary` sub-table, e.g.
     * `["summary"] = { ["status"] = "reading", ... }`. Both levels need their
     * own nil guard: the sidecar may have no `summary` table at all, or a
     * `summary` table without a `status` key inside it.
     */
    suspend fun readStatus(documentFile: DocumentFile): String? {
        val stream = context.contentResolver.openInputStream(documentFile.uri) ?: return null
        val table = stream.use { luaChunkLoader.loadTable(it, "metadata.lua") }
        val summary = table.get("summary")
        if (summary.isnil()) return null
        val status = summary.get("status")
        return if (status.isnil()) null else status.tojstring()
    }
}
