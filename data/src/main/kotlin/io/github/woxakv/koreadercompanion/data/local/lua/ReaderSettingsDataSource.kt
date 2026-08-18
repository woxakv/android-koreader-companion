package io.github.woxakv.koreadercompanion.data.local.lua

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ReaderSettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val luaChunkLoader: LuaChunkLoader,
) {
    suspend fun readLastFile(documentFile: DocumentFile): String? {
        val stream = context.contentResolver.openInputStream(documentFile.uri) ?: return null
        val table = stream.use { luaChunkLoader.loadTable(it, "settings.reader.lua") }
        val value = table.get("lastfile")
        return if (value.isnil()) null else value.tojstring()
    }
}
