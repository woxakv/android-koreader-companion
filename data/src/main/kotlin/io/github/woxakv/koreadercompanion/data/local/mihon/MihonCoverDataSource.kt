package io.github.woxakv.koreadercompanion.data.local.mihon

import android.util.Log
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.withContext

private const val TAG = "MihonCoverDataSource"
private const val CONNECT_TIMEOUT_MS = 5_000
private const val READ_TIMEOUT_MS = 5_000

// Manga-source CDNs commonly reject direct, headerless requests (anti-hotlinking) -
// a browser-like User-Agent and a same-origin Referer, mirroring what Mihon's own
// extensions send, are enough to pass most of them without per-source configuration.
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * Fetches a Mihon manga's cover art over the network - unlike KOReader's
 * cover art (a local file inside the Books folder), `BackupManga.thumbnailUrl`
 * is a remote HTTPS URL (e.g. a MangaDex CDN link), so there's no SAF read
 * involved here. Failures (no network, timeout, bad URL, non-2xx response)
 * all collapse to `null` rather than propagating - a missing cover is a
 * normal, already-handled UI state ("No cover"), not an error worth
 * surfacing or retrying.
 */
class MihonCoverDataSource @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun fetchCoverBytes(url: String): ByteArray? = withContext(dispatcherProvider.io) {
        runCatching {
            val parsedUrl = URL(url)
            val connection = parsedUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", "${parsedUrl.protocol}://${parsedUrl.host}/")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Cover fetch got HTTP ${connection.responseCode} for $url")
                    return@runCatching null
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.onFailure { error -> Log.w(TAG, "Cover fetch failed for $url", error) }.getOrNull()
    }
}
