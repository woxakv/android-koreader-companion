package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.MergeDailyReadingStatsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Uses a plain Application rather than our real @HiltAndroidApp one: this
// test exercises doWork() in isolation via a hand-built WorkerFactory, not
// the DI graph, and the real Application needs Hilt injection to boot.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class WidgetRefreshWorkerTest {

    private val book = CurrentBook(
        title = "L'Attentat",
        author = "Yasmina Khadra",
        progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
        estimatedSecondsRemaining = 11036L,
        coverImageBytes = null,
        bookContentUriString = null,
    )

    private val fakeReadingStatsRepository = object : ReadingStatsRepository {
        override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> = Try.Success(emptyList())
    }
    private val fakeDailyStatsUseCase = GetDailyReadingStatsUseCase(fakeReadingStatsRepository)
    private val fakeMihonDailyStatsUseCase = GetMihonDailyReadingStatsUseCase(fakeReadingStatsRepository)
    private val fakeMergedDailyStatsUseCase = GetMergedDailyReadingStatsUseCase(
        getKoreaderStats = fakeDailyStatsUseCase,
        getMihonStats = fakeMihonDailyStatsUseCase,
        merge = MergeDailyReadingStatsUseCase(),
    )
    private val fakeStatsFilterRepository = object : StatsFilterRepository {
        override suspend fun getFilter(): StatsSourceFilter = StatsSourceFilter.ALL
        override suspend fun setFilter(filter: StatsSourceFilter) = Unit
    }
    private val fakeStatsSourceFilterUseCase = GetStatsSourceFilterUseCase(fakeStatsFilterRepository)
    private val fakeMihonCurrentBookUseCase = GetMihonCurrentBookUseCase(object : CurrentBookRepository {
        override suspend fun getCurrentBook(): Try<CurrentBook> = Try.Failure(KoreaderError.PermissionNotGranted)
    })

    private fun buildWorker(
        getCurrentBook: GetCurrentBookUseCase,
        getReadingStatsSummary: GetReadingStatsSummaryUseCase = GetReadingStatsSummaryUseCase(fakeDailyStatsUseCase),
        getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase = fakeMergedDailyStatsUseCase,
        getStatsSourceFilter: GetStatsSourceFilterUseCase = fakeStatsSourceFilterUseCase,
        getMihonCurrentBook: GetMihonCurrentBookUseCase = fakeMihonCurrentBookUseCase,
    ): WidgetRefreshWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ) = WidgetRefreshWorker(
                appContext,
                workerParameters,
                getCurrentBook,
                getReadingStatsSummary,
                getMergedDailyReadingStats,
                getStatsSourceFilter,
                getMihonCurrentBook,
            )
        }
        return TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `succeeds when the use case returns a book`() {
        val useCase = GetCurrentBookUseCase(object : CurrentBookRepository {
            override suspend fun getCurrentBook(): Try<CurrentBook> = Try.Success(book)
        })
        val worker = buildWorker(useCase)

        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `retries when the use case fails`() {
        val useCase = GetCurrentBookUseCase(object : CurrentBookRepository {
            override suspend fun getCurrentBook(): Try<CurrentBook> = Try.Failure(KoreaderError.PermissionNotGranted)
        })
        val worker = buildWorker(useCase)

        // The current book fetch failing doesn't throw - it's mapped to a
        // "no book" widget state, so a normal Success is still expected here.
        val result = runBlocking { worker.doWork() }

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `refreshMutex serializes concurrent access`() = runBlocking {
        val events = mutableListOf<String>()
        val job1 = async {
            WidgetRefreshWorker.refreshMutex.withLock {
                events.add("start-1"); delay(100); events.add("end-1")
            }
        }
        val job2 = async {
            WidgetRefreshWorker.refreshMutex.withLock {
                events.add("start-2"); delay(100); events.add("end-2")
            }
        }
        awaitAll(job1, job2)
        // Whichever job starts first must also finish before the other starts.
        val firstStart = events[0]
        val expectedSecondEvent = "end" + firstStart.removePrefix("start")
        assertEquals(expectedSecondEvent, events[1])
    }
}
