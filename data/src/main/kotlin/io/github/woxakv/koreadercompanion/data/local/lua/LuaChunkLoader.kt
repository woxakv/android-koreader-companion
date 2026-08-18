package io.github.woxakv.koreadercompanion.data.local.lua

import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.InputStream
import javax.inject.Inject

class LuaParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LuaParseTimeoutException(message: String) : Exception(message)

/**
 * Loads and executes a KOReader `return {...}` Lua chunk, sandboxed (io/os/
 * package/luajava stripped, since the file comes from shared storage not
 * fully trust-isolated from other apps) and timeout-guarded against a
 * malformed or maliciously huge file. The timeout is cooperative, not a true
 * preemptive kill: a genuinely runaway chunk keeps its worker thread running
 * in the background after we bail out, which is an acceptable trade-off for
 * guarding small local settings files.
 */
class LuaChunkLoader @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun loadTable(inputStream: InputStream, chunkName: String, timeoutMillis: Long = 5_000): LuaTable {
        val script = inputStream.bufferedReader(Charsets.UTF_8).readText()
        return try {
            withTimeout(timeoutMillis) {
                withContext(dispatcherProvider.default) {
                    val globals = sandboxedGlobals()
                    val chunk = globals.load(script, chunkName)
                    when (val result = chunk.call()) {
                        is LuaTable -> result
                        else -> throw LuaParseException("Chunk '$chunkName' did not return a table")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw LuaParseTimeoutException("Timed out parsing '$chunkName'")
        }
    }

    private fun sandboxedGlobals() = JsePlatform.standardGlobals().apply {
        set("io", LuaValue.NIL)
        set("os", LuaValue.NIL)
        set("package", LuaValue.NIL)
        set("luajava", LuaValue.NIL)
    }
}
