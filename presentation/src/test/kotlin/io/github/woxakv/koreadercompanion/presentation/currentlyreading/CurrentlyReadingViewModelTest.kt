package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import app.cash.turbine.test
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.MergeDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.SetStatsSourceFilterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CurrentlyReadingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val book = CurrentBook(
        title = "L'Attentat",
        author = "Yasmina Khadra",
        progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
        estimatedSecondsRemaining = 11036L,
        coverImageBytes = null,
        bookContentUriString = null,
    )

    private fun currentBookUseCase(result: Try<CurrentBook>) = GetCurrentBookUseCase(
        object : CurrentBookRepository {
            override suspend fun getCurrentBook(): Try<CurrentBook> = result
        },
    )

    /**
     * None of the tests in this file assert on the Mihon card, so it
     * defaults to an always-failing fake (mapped to "no Mihon card" by the
     * ViewModel, same as an ungranted/missing-backup Mihon source would be)
     * - just enough to keep construction compiling.
     */
    private fun mihonCurrentBookUseCase(
        result: Try<CurrentBook> = Try.Failure(KoreaderError.NoBookFound),
    ) = GetMihonCurrentBookUseCase(
        object : CurrentBookRepository {
            override suspend fun getCurrentBook(): Try<CurrentBook> = result
        },
    )

    private fun grantUseCase() = GrantKoreaderAccessUseCase(
        object : StorageAccessRepository {
            override suspend fun hasAccess(target: StorageTarget): Boolean = true
            override suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit> = Try.Success(Unit)
            override suspend fun grantedTreeUriString(target: StorageTarget): String? = null
            override suspend fun revokeAccess(target: StorageTarget) = Unit
        },
    )

    /**
     * Both default to granted, matching the pre-existing behavior these
     * tests were written against (filter row visible, no auto-reset of the
     * selected filter) - none of the tests in this file assert on
     * showFilter/grant state.
     */
    private fun hasAccessRepository(granted: Boolean) = object : StorageAccessRepository {
        override suspend fun hasAccess(target: StorageTarget): Boolean = granted
        override suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit> = Try.Success(Unit)
        override suspend fun grantedTreeUriString(target: StorageTarget): String? = null
        override suspend fun revokeAccess(target: StorageTarget) = Unit
    }

    private fun readingStatsRepository(result: Try<List<DailyReadingStat>> = Try.Success(emptyList())) =
        object : ReadingStatsRepository {
            override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> = result
        }

    /**
     * In-memory fake, defaulting to ALL like the real DataStore-backed
     * implementation does when nothing has been persisted yet - just enough
     * to keep construction compiling now that the ViewModel depends on it.
     */
    private fun statsFilterRepository() = object : StatsFilterRepository {
        private var filter = StatsSourceFilter.ALL
        override suspend fun getFilter(): StatsSourceFilter = filter
        override suspend fun setFilter(filter: StatsSourceFilter) {
            this.filter = filter
        }
    }

    /** No-op fake - none of the tests in this file assert on widget-refresh scheduling. */
    private fun widgetRefreshScheduler() = object : WidgetRefreshScheduler {
        override fun schedulePeriodicRefresh() = Unit
        override fun requestImmediateRefresh() = Unit
    }

    /**
     * None of the tests in this file assert on stats/heatmap content, so the
     * reading-stats stack defaults to an empty, always-successful fake
     * repository wrapped in the real use cases (worth exercising for real
     * rather than mocking out, per plan 003 step 5) — just enough to keep
     * construction compiling now that the ViewModel depends on it. Mihon's
     * side of the merge defaults to an empty, always-successful fake too, so
     * the merged (ALL-filter) result is identical to the KOReader-only stats
     * these tests were written against.
     */
    private fun buildViewModel(
        getCurrentBook: GetCurrentBookUseCase,
        grantKoreaderAccess: GrantKoreaderAccessUseCase,
        readingStatsRepository: ReadingStatsRepository = readingStatsRepository(),
        getMihonCurrentBook: GetMihonCurrentBookUseCase = mihonCurrentBookUseCase(),
    ): CurrentlyReadingViewModel {
        val getDailyReadingStats = GetDailyReadingStatsUseCase(readingStatsRepository)
        val getMihonDailyReadingStats = GetMihonDailyReadingStatsUseCase(readingStatsRepository())
        val filterRepository = statsFilterRepository()
        return CurrentlyReadingViewModel(
            getCurrentBook = getCurrentBook,
            getMihonCurrentBook = getMihonCurrentBook,
            grantKoreaderAccess = grantKoreaderAccess,
            hasKoreaderAccess = HasKoreaderAccessUseCase(hasAccessRepository(true)),
            hasMihonAccess = HasMihonAccessUseCase(hasAccessRepository(true)),
            getReadingStatsSummary = GetReadingStatsSummaryUseCase(getDailyReadingStats),
            getMergedDailyReadingStats = GetMergedDailyReadingStatsUseCase(
                getKoreaderStats = getDailyReadingStats,
                getMihonStats = getMihonDailyReadingStats,
                merge = MergeDailyReadingStatsUseCase(),
            ),
            getStatsSourceFilter = GetStatsSourceFilterUseCase(filterRepository),
            setStatsSourceFilter = SetStatsSourceFilterUseCase(filterRepository),
            widgetRefreshScheduler = widgetRefreshScheduler(),
        )
    }

    @Test
    fun `emits loading then content on a successful load`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(currentBookUseCase(Try.Success(book)), grantUseCase())

        viewModel.state.test {
            assertEquals(CurrentlyReadingUiState.Loading, awaitItem())
            assertEquals(CurrentlyReadingUiState.Content(book.toUi()), awaitItem())
        }
    }

    @Test
    fun `permission-not-granted failure maps to PermissionRequired`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            currentBookUseCase(Try.Failure(KoreaderError.PermissionNotGranted)),
            grantUseCase(),
        )

        viewModel.state.test {
            awaitItem() // Loading
            val content = awaitItem()
            assert(content is CurrentlyReadingUiState.PermissionRequired)
        }
    }

    @Test
    fun `data-folder-not-found failure maps to PermissionRequired`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            currentBookUseCase(Try.Failure(KoreaderError.DataFolderNotFound("/storage/emulated/0/koreader"))),
            grantUseCase(),
        )

        viewModel.state.test {
            awaitItem() // Loading
            val content = awaitItem()
            assert(content is CurrentlyReadingUiState.PermissionRequired)
        }
    }

    @Test
    fun `other failures map to a retryable Error state`() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            currentBookUseCase(Try.Failure(KoreaderError.NoBookFound)),
            grantUseCase(),
        )

        viewModel.state.test {
            awaitItem() // Loading
            val content = awaitItem()
            assertEquals(CurrentlyReadingUiState.Error(KoreaderError.NoBookFound.message, retryable = true), content)
        }
    }

    @Test
    fun `granting access after a permission failure recovers to Content`() = runTest(testDispatcher) {
        var granted = false
        val bookRepository = object : CurrentBookRepository {
            override suspend fun getCurrentBook(): Try<CurrentBook> =
                if (granted) Try.Success(book) else Try.Failure(KoreaderError.PermissionNotGranted)
        }
        val storageRepository = object : StorageAccessRepository {
            override suspend fun hasAccess(target: StorageTarget): Boolean = granted
            override suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit> {
                granted = true
                return Try.Success(Unit)
            }
            override suspend fun grantedTreeUriString(target: StorageTarget): String? =
                if (granted) "content://tree/primary:koreader" else null
            override suspend fun revokeAccess(target: StorageTarget) {
                granted = false
            }
        }

        val viewModel = buildViewModel(
            GetCurrentBookUseCase(bookRepository),
            GrantKoreaderAccessUseCase(storageRepository),
        )

        viewModel.state.test {
            awaitItem() // Loading
            val permissionRequired = awaitItem()
            assert(permissionRequired is CurrentlyReadingUiState.PermissionRequired)

            viewModel.onTreeSelected("content://tree/primary:koreader")

            assertEquals(CurrentlyReadingUiState.Loading, awaitItem())
            assertEquals(CurrentlyReadingUiState.Content(book.toUi()), awaitItem())
        }
    }

    @Test
    fun `refresh re-populates statsSummary and heatmapBitmap with new data`() = runTest(testDispatcher) {
        val today = LocalDate.now()
        var statsResult: Try<List<DailyReadingStat>> =
            Try.Success(listOf(DailyReadingStat(date = today, pagesRead = 10, minutesRead = 20)))
        val repository = object : ReadingStatsRepository {
            override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> = statsResult
        }

        val viewModel = buildViewModel(
            currentBookUseCase(Try.Success(book)),
            grantUseCase(),
            readingStatsRepository = repository,
        )
        advanceUntilIdle()

        val initialSummary = viewModel.statsSummary.value
        val initialBitmap = viewModel.heatmapBitmap.value
        assertEquals(20, initialSummary?.minutesToday)
        assertEquals(10, initialSummary?.pagesToday)

        statsResult = Try.Success(listOf(DailyReadingStat(date = today, pagesRead = 40, minutesRead = 90)))
        viewModel.refresh()
        advanceUntilIdle()

        val refreshedSummary = viewModel.statsSummary.value
        val refreshedBitmap = viewModel.heatmapBitmap.value
        assertEquals(90, refreshedSummary?.minutesToday)
        assertEquals(40, refreshedSummary?.pagesToday)
        assertNotEquals(initialSummary, refreshedSummary)
        assertNotEquals(initialBitmap, refreshedBitmap)
    }
}
