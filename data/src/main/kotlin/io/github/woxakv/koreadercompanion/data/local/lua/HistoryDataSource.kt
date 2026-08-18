package io.github.woxakv.koreadercompanion.data.local.lua

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class HistoryEntryDto(val time: Long, val file: String)

class HistoryDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val luaChunkLoader: LuaChunkLoader,
) {
    // history.lua's entries are already ordered most-recent-first by their
    // Lua array index, so no re-sorting is needed here.
    suspend fun readHistory(documentFile: DocumentFile): List<HistoryEntryDto> {
        val stream = context.contentResolver.openInputStream(documentFile.uri) ?: return emptyList()
        val table = stream.use { luaChunkLoader.loadTable(it, "history.lua") }

        val entries = mutableListOf<HistoryEntryDto>()
        var index = 1
        while (true) {
            val entry = table.get(index)
            if (entry.isnil()) break
            val time = entry.get("time")
            val file = entry.get("file")
            if (!time.isnil() && !file.isnil()) {
                entries.add(HistoryEntryDto(time = time.tolong(), file = file.tojstring()))
            }
            index++
        }
        return entries
    }
}
