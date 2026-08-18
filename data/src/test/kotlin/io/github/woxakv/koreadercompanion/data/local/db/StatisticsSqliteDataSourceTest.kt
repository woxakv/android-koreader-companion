package io.github.woxakv.koreadercompanion.data.local.db

import android.database.sqlite.SQLiteDatabase
import io.github.woxakv.koreadercompanion.core.coroutines.StandardDispatcherProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * A tiny synthetic database is built programmatically here rather than
 * committing a binary fixture, so no real personal reading data ever ends
 * up in the repo.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsSqliteDataSourceTest {

    private lateinit var dbFile: File
    private val dataSource = StatisticsSqliteDataSource(StandardDispatcherProvider())

    @Before
    fun setUp() {
        dbFile = File.createTempFile("statistics_test", ".sqlite3")
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE book (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT, authors TEXT, last_open INTEGER, pages INTEGER,
                total_read_time INTEGER, total_read_pages INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE TABLE page_stat_data (id_book INTEGER, page INTEGER, start_time INTEGER, duration INTEGER)",
        )

        db.execSQL(
            "INSERT INTO book (id, title, authors, last_open, pages, total_read_time, total_read_pages) " +
                "VALUES (1, 'Older Book', 'Some Author', 1000, 200, 500, 50)",
        )
        db.execSQL(
            "INSERT INTO book (id, title, authors, last_open, pages, total_read_time, total_read_pages) " +
                "VALUES (2, 'L''Attentat', 'Yasmina Khadra', 1786395275, 364, 8688, 148)",
        )

        listOf(10, 50, 120, 176).forEach { page ->
            db.execSQL("INSERT INTO page_stat_data (id_book, page, start_time, duration) VALUES (2, $page, 0, 30)")
        }
        listOf(5, 20).forEach { page ->
            db.execSQL("INSERT INTO page_stat_data (id_book, page, start_time, duration) VALUES (1, $page, 0, 30)")
        }

        // Day-bucketing fixtures: two distinct calendar days (noon UTC, well clear of any
        // local-timezone date-boundary shift), plus one row well before DAY_A to exercise the
        // sinceEpochSeconds cutoff. Book id is irrelevant to getDailyStats, which aggregates
        // across all books.
        listOf(30, 45, 60).forEach { duration ->
            db.execSQL(
                "INSERT INTO page_stat_data (id_book, page, start_time, duration) " +
                    "VALUES (3, 1, $DAY_A_EPOCH_SECONDS, $duration)",
            )
        }
        listOf(20, 25).forEach { duration ->
            db.execSQL(
                "INSERT INTO page_stat_data (id_book, page, start_time, duration) " +
                    "VALUES (3, 2, $DAY_B_EPOCH_SECONDS, $duration)",
            )
        }
        db.execSQL(
            "INSERT INTO page_stat_data (id_book, page, start_time, duration) " +
                "VALUES (3, 3, $OLD_EPOCH_SECONDS, 99)",
        )

        db.close()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun `returns recent books ordered by last_open descending`() = runBlocking {
        val stats = dataSource.getRecentBookStats(dbFile)

        assertEquals(2, stats.size)
        assertEquals("L'Attentat", stats[0].title)
        assertEquals(2L, stats[0].id)
        assertEquals(364, stats[0].pages)
        assertEquals("Older Book", stats[1].title)
        assertEquals(1L, stats[1].id)
    }

    @Test
    fun `caps the recent books window at the requested limit`() = runBlocking {
        val stats = dataSource.getRecentBookStats(dbFile, limit = 1)

        assertEquals(1, stats.size)
        assertEquals("L'Attentat", stats.single().title)
    }

    @Test
    fun `returns the max page read for a book`() = runBlocking {
        val maxPage = dataSource.getMaxPageRead(dbFile, bookId = 2L)

        assertEquals(176, maxPage)
    }

    @Test
    fun `returns null max page for a book with no reading history`() = runBlocking {
        val maxPage = dataSource.getMaxPageRead(dbFile, bookId = 999L)

        assertNull(maxPage)
    }

    @Test
    fun `groups page_stat_data rows by calendar day`() = runBlocking {
        val stats = dataSource.getDailyStats(dbFile, sinceEpochSeconds = CUTOFF_AT_DAY_A)

        assertEquals(2, stats.size)

        val dayA = stats.single { it.day == expectedDay(DAY_A_EPOCH_SECONDS) }
        assertEquals(135L, dayA.totalSeconds)
        assertEquals(3, dayA.pagesRead)

        val dayB = stats.single { it.day == expectedDay(DAY_B_EPOCH_SECONDS) }
        assertEquals(45L, dayB.totalSeconds)
        assertEquals(2, dayB.pagesRead)
    }

    @Test
    fun `excludes rows with start_time before the cutoff`() = runBlocking {
        val stats = dataSource.getDailyStats(dbFile, sinceEpochSeconds = DAY_B_EPOCH_SECONDS)

        assertEquals(1, stats.size)
        assertEquals(expectedDay(DAY_B_EPOCH_SECONDS), stats.single().day)
        assertEquals(45L, stats.single().totalSeconds)
        assertEquals(2, stats.single().pagesRead)
        assertTrue(stats.none { it.day == expectedDay(DAY_A_EPOCH_SECONDS) })
    }

    @Test
    fun `returns an empty list when no rows match the cutoff`() = runBlocking {
        val stats = dataSource.getDailyStats(dbFile, sinceEpochSeconds = FAR_FUTURE_EPOCH_SECONDS)

        assertEquals(emptyList<DailyStatRowDto>(), stats)
    }

    private fun expectedDay(epochSeconds: Long): String =
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private companion object {
        // 2024-01-01T00:00:00Z
        const val CUTOFF_AT_DAY_A = 1_704_067_200L

        // 2024-01-01T12:00:00Z - noon UTC keeps the local calendar date stable across any
        // realistic timezone offset.
        const val DAY_A_EPOCH_SECONDS = 1_704_110_400L

        // 2024-01-15T12:00:00Z - two weeks after DAY_A, so it lands on a distinct local date
        // under any timezone offset.
        const val DAY_B_EPOCH_SECONDS = 1_705_320_000L

        // 2023-01-01T00:00:00Z - well before CUTOFF_AT_DAY_A, used to verify exclusion.
        const val OLD_EPOCH_SECONDS = 1_672_531_200L

        // Year 2033 - guaranteed to be after every fixture row's start_time.
        const val FAR_FUTURE_EPOCH_SECONDS = 2_000_000_000L
    }
}
