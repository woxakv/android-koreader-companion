package io.github.woxakv.koreadercompanion.data.local.db

import android.database.sqlite.SQLiteDatabase
import io.github.woxakv.koreadercompanion.core.coroutines.DispatcherProvider
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class StatisticsSqliteDataSource @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) {
    /**
     * A window of the most recently opened books rather than just the single
     * newest row: the book KOReader actually has open (per `lastfile`) is not
     * always the one with the highest `last_open`, so the caller needs a few
     * candidates to match against instead of being forced to trust the top row.
     */
    suspend fun getRecentBookStats(dbFile: File, limit: Int = 20): List<BookStatsDto> =
        withContext(dispatcherProvider.io) {
            openReadOnly(dbFile).use { db ->
                db.rawQuery(
                    "SELECT id, title, authors, last_open, pages, total_read_time, total_read_pages " +
                        "FROM book ORDER BY last_open DESC LIMIT ?",
                    arrayOf(limit.toString()),
                ).use { cursor ->
                    val rows = mutableListOf<BookStatsDto>()
                    while (cursor.moveToNext()) {
                        rows += BookStatsDto(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                            authors = cursor.getString(cursor.getColumnIndexOrThrow("authors")),
                            lastOpen = cursor.getLong(cursor.getColumnIndexOrThrow("last_open")),
                            pages = cursor.getInt(cursor.getColumnIndexOrThrow("pages")),
                            totalReadTime = cursor.getLong(cursor.getColumnIndexOrThrow("total_read_time")),
                            totalReadPages = cursor.getInt(cursor.getColumnIndexOrThrow("total_read_pages")),
                        )
                    }
                    rows
                }
            }
        }

    suspend fun getMaxPageRead(dbFile: File, bookId: Long): Int? = withContext(dispatcherProvider.io) {
        openReadOnly(dbFile).use { db ->
            db.rawQuery(
                "SELECT MAX(page) FROM page_stat_data WHERE id_book = ?",
                arrayOf(bookId.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) else null
            }
        }
    }

    suspend fun getDailyStats(dbFile: File, sinceEpochSeconds: Long): List<DailyStatRowDto> =
        withContext(dispatcherProvider.io) {
            openReadOnly(dbFile).use { db ->
                db.rawQuery(
                    "SELECT date(start_time, 'unixepoch', 'localtime') AS day, " +
                        "SUM(duration) AS totalSeconds, COUNT(*) AS pagesRead " +
                        "FROM page_stat_data WHERE start_time >= ? GROUP BY day ORDER BY day",
                    arrayOf(sinceEpochSeconds.toString()),
                ).use { cursor ->
                    val rows = mutableListOf<DailyStatRowDto>()
                    while (cursor.moveToNext()) {
                        rows += DailyStatRowDto(
                            day = cursor.getString(cursor.getColumnIndexOrThrow("day")),
                            totalSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("totalSeconds")),
                            pagesRead = cursor.getInt(cursor.getColumnIndexOrThrow("pagesRead")),
                        )
                    }
                    rows
                }
            }
        }

    private fun openReadOnly(dbFile: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
}
