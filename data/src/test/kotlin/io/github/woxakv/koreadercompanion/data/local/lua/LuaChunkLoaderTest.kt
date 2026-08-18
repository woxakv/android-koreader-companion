package io.github.woxakv.koreadercompanion.data.local.lua

import io.github.woxakv.koreadercompanion.core.coroutines.StandardDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LuaChunkLoaderTest {

    private val loader = LuaChunkLoader(StandardDispatcherProvider())

    // Modeled on (not copied from) the real settings.reader.lua sample.
    private val settingsReaderChunk = """
        return {
            ["lastfile"] = "/storage/emulated/0/Books/Sample -- Author.epub",
        }
    """.trimIndent()

    // Modeled on (not copied from) the real history.lua sample.
    private val historyChunk = """
        return {
            [1] = {
                ["time"] = 1786395275,
                ["file"] = "/storage/emulated/0/Books/Sample -- Author.epub",
            },
            [2] = {
                ["time"] = 1786290859,
                ["file"] = "/storage/emulated/0/Books/Other Book.epub",
            },
        }
    """.trimIndent()

    @Test
    fun `parses lastfile from a settings-reader-shaped chunk`() = runBlocking {
        val table = loader.loadTable(settingsReaderChunk.byteInputStream(), "settings.reader.lua")

        val lastfile = table.get("lastfile")

        assertFalse(lastfile.isnil())
        assertEquals("/storage/emulated/0/Books/Sample -- Author.epub", lastfile.tojstring())
    }

    @Test
    fun `parses a history-shaped array table in order`() = runBlocking {
        val table = loader.loadTable(historyChunk.byteInputStream(), "history.lua")

        val first = table.get(1)
        val second = table.get(2)
        val third = table.get(3)

        assertEquals(1786395275L, first.get("time").tolong())
        assertEquals("/storage/emulated/0/Books/Sample -- Author.epub", first.get("file").tojstring())
        assertEquals(1786290859L, second.get("time").tolong())
        assertTrue(third.isnil())
    }

    @Test
    fun `a long-running chunk is caught by the timeout guard`() {
        // Finite but slow (not a literal infinite loop), so the background
        // worker thread eventually exits on its own after the test's
        // assertion already passed.
        val slowChunk = """
            local x = 0
            for i = 1, 500000000 do
                x = x + i
            end
            return { x = x }
        """.trimIndent()

        assertThrows(LuaParseTimeoutException::class.java) {
            runBlocking {
                loader.loadTable(ByteArrayInputStream(slowChunk.toByteArray()), "slow.lua", timeoutMillis = 10)
            }
        }
    }

    @Test
    fun `a chunk that does not return a table is rejected`() {
        assertThrows(LuaParseException::class.java) {
            runBlocking {
                loader.loadTable("return 42".byteInputStream(), "not-a-table.lua")
            }
        }
    }
}
