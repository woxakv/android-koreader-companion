package io.github.woxakv.koreadercompanion.presentation.stats

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.DailyReadingStat
import io.github.woxakv.koreadercompanion.domain.model.StatsGranularity
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.domain.usecase.BucketDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class StatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Counts invocations so tests can assert on how many times the merged
     * fetch (which calls this repository once per source, per merged fetch)
     * ran, standing in for a call count on
     * [GetMergedDailyReadingStatsUseCase] itself since that class has no
     * mockable seam of its own - matches this file's fake-over-mock style.
     */
    private class CountingReadingStatsRepository(
        private val result: Try<List<DailyReadingStat>> = Try.Success(emptyList()),
    ) : ReadingStatsRepository {
        var callCount = 0
            private set

        override suspend fun getDailyStats(days: Int): Try<List<DailyReadingStat>> {
            callCount++
            return result
        }
    }

    private fun hasAccessRepository(koreaderGranted: Boolean, mihonGranted: Boolean) = object : StorageAccessRepository {
        override suspend fun hasAccess(target: StorageTarget): Boolean =
            if (target == StorageTarget.KOREADER) koreaderGranted else mihonGranted

        override suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit> = Try.Success(Unit)
        override suspend fun grantedTreeUriString(target: StorageTarget): String? = null
        override suspend fun revokeAccess(target: StorageTarget) = Unit
    }

    private class InMemoryStatsFilterRepository(initial: StatsSourceFilter) : StatsFilterRepository {
        var filter = initial
        override suspend fun getFilter(): StatsSourceFilter = filter
        override suspend fun setFilter(filter: StatsSourceFilter) {
            this.filter = filter
        }
    }

    private fun buildViewModel(
        koreaderRepository: CountingReadingStatsRepository = CountingReadingStatsRepository(),
        mihonRepository: CountingReadingStatsRepository = CountingReadingStatsRepository(),
        persistedFilter: StatsSourceFilter = StatsSourceFilter.ALL,
        koreaderGranted: Boolean = true,
        mihonGranted: Boolean = true,
    ): Triple<StatsViewModel, CountingReadingStatsRepository, InMemoryStatsFilterRepository> {
        val filterRepository = InMemoryStatsFilterRepository(persistedFilter)
        val viewModel = StatsViewModel(
            getReadingStatsSummary = GetReadingStatsSummaryUseCase(GetDailyReadingStatsUseCase(koreaderRepository)),
            getMergedDailyReadingStats = GetMergedDailyReadingStatsUseCase(
                getKoreaderStats = GetDailyReadingStatsUseCase(koreaderRepository),
                getMihonStats = GetMihonDailyReadingStatsUseCase(mihonRepository),
                merge = MergeDailyReadingStatsUseCase(),
            ),
            bucketDailyReadingStats = BucketDailyReadingStatsUseCase(),
            getStatsSourceFilter = GetStatsSourceFilterUseCase(filterRepository),
            setStatsSourceFilter = SetStatsSourceFilterUseCase(filterRepository),
            hasKoreaderAccess = HasKoreaderAccessUseCase(hasAccessRepository(koreaderGranted, mihonGranted)),
            hasMihonAccess = HasMihonAccessUseCase(hasAccessRepository(koreaderGranted, mihonGranted)),
        )
        return Triple(viewModel, koreaderRepository, filterRepository)
    }

    @Test
    fun `initial load reads the persisted filter before its first summary and buckets emission`() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val koreaderRepository = CountingReadingStatsRepository(
            Try.Success(listOf(DailyReadingStat(date = today, pagesRead = 10, minutesRead = 20))),
        )
        val (viewModel, _, _) = buildViewModel(
            koreaderRepository = koreaderRepository,
            persistedFilter = StatsSourceFilter.KOREADER,
        )

        // Before advancing, the persisted filter hasn't been read yet - default state.
        assertEquals(StatsSourceFilter.ALL, viewModel.filter.value)
        assertEquals(null, viewModel.summary.value)

        advanceUntilIdle()

        assertEquals(StatsSourceFilter.KOREADER, viewModel.filter.value)
        assertEquals(20, viewModel.summary.value?.minutesToday)
        assertEquals(10, viewModel.summary.value?.pagesToday)
        assertTrue(viewModel.buckets.value.isNotEmpty())
    }

    @Test
    fun `onGranularitySelected re-buckets without invoking getMergedDailyReadingStats again`() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val koreaderRepository = CountingReadingStatsRepository(
            Try.Success(listOf(DailyReadingStat(date = today, pagesRead = 10, minutesRead = 20))),
        )
        val (viewModel, repository, _) = buildViewModel(koreaderRepository = koreaderRepository)
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        viewModel.onGranularitySelected(StatsGranularity.YEAR)
        advanceUntilIdle()

        assertEquals(StatsGranularity.YEAR, viewModel.granularity.value)
        assertEquals(1, repository.callCount)
        assertTrue(viewModel.buckets.value.isNotEmpty())
    }

    @Test
    fun `onHeatmapMetricSelected re-renders the heatmap without invoking getMergedDailyReadingStats again`() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val koreaderRepository = CountingReadingStatsRepository(
            Try.Success(listOf(DailyReadingStat(date = today, pagesRead = 10, minutesRead = 20))),
        )
        val (viewModel, repository, _) = buildViewModel(koreaderRepository = koreaderRepository)
        advanceUntilIdle()
        assertEquals(1, repository.callCount)
        val bitmapAfterInitialLoad = viewModel.heatmapBitmap.value
        assertTrue(bitmapAfterInitialLoad != null)

        viewModel.onHeatmapMetricSelected(HeatmapMetric.TIME)
        advanceUntilIdle()

        assertEquals(HeatmapMetric.TIME, viewModel.heatmapMetric.value)
        assertEquals(1, repository.callCount)
        assertTrue(viewModel.heatmapBitmap.value != null)
    }

    @Test
    fun `onFilterSelected triggers a new getMergedDailyReadingStats call`() = runTest(testDispatcher) {
        val (viewModel, repository, filterRepository) = buildViewModel()
        advanceUntilIdle()
        assertEquals(1, repository.callCount)

        viewModel.onFilterSelected(StatsSourceFilter.MIHON)
        advanceUntilIdle()

        assertEquals(StatsSourceFilter.MIHON, viewModel.filter.value)
        assertEquals(StatsSourceFilter.MIHON, filterRepository.filter)
        assertEquals(2, repository.callCount)
    }

    @Test
    fun `showFilter follows the both-granted gate and snaps a stale non-ALL filter back to ALL`() = runTest(testDispatcher) {
        val (viewModel, _, filterRepository) = buildViewModel(
            persistedFilter = StatsSourceFilter.MIHON,
            koreaderGranted = true,
            mihonGranted = false,
        )

        advanceUntilIdle()

        assertFalse(viewModel.showFilter.value)
        assertEquals(StatsSourceFilter.ALL, viewModel.filter.value)
        assertEquals(StatsSourceFilter.ALL, filterRepository.filter)
    }

    @Test
    fun `showFilter is true when both sources are granted`() = runTest(testDispatcher) {
        val (viewModel, _, _) = buildViewModel(koreaderGranted = true, mihonGranted = true)

        advanceUntilIdle()

        assertTrue(viewModel.showFilter.value)
    }
}
