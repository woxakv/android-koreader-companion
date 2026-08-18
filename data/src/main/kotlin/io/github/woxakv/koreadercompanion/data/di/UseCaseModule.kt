package io.github.woxakv.koreadercompanion.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.MihonGrantRepository
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.usecase.BucketDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMihonDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.IsMihonGrantNarrowUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.MergeDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.SetStatsSourceFilterUseCase

/**
 * Domain's UseCases are plain classes with no Hilt annotations (domain stays
 * framework-free), so this bridges construction for them here instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetCurrentBookUseCase(@KoreaderSource repository: CurrentBookRepository): GetCurrentBookUseCase =
        GetCurrentBookUseCase(repository)

    @Provides
    fun provideGetDailyReadingStatsUseCase(
        @KoreaderSource repository: ReadingStatsRepository,
    ): GetDailyReadingStatsUseCase =
        GetDailyReadingStatsUseCase(repository)

    @Provides
    fun provideGetReadingStatsSummaryUseCase(
        getDailyReadingStats: GetDailyReadingStatsUseCase,
    ): GetReadingStatsSummaryUseCase =
        GetReadingStatsSummaryUseCase(getDailyReadingStats)

    @Provides
    fun provideHasKoreaderAccessUseCase(repository: StorageAccessRepository): HasKoreaderAccessUseCase =
        HasKoreaderAccessUseCase(repository)

    @Provides
    fun provideGrantKoreaderAccessUseCase(repository: StorageAccessRepository): GrantKoreaderAccessUseCase =
        GrantKoreaderAccessUseCase(repository)

    @Provides
    fun provideHasBooksAccessUseCase(repository: StorageAccessRepository): HasBooksAccessUseCase =
        HasBooksAccessUseCase(repository)

    @Provides
    fun provideGrantBooksAccessUseCase(repository: StorageAccessRepository): GrantBooksAccessUseCase =
        GrantBooksAccessUseCase(repository)

    @Provides
    fun provideHasMihonAccessUseCase(repository: StorageAccessRepository): HasMihonAccessUseCase =
        HasMihonAccessUseCase(repository)

    @Provides
    fun provideGrantMihonAccessUseCase(repository: StorageAccessRepository): GrantMihonAccessUseCase =
        GrantMihonAccessUseCase(repository)

    @Provides
    fun provideRevokeKoreaderAccessUseCase(repository: StorageAccessRepository): RevokeKoreaderAccessUseCase =
        RevokeKoreaderAccessUseCase(repository)

    @Provides
    fun provideRevokeBooksAccessUseCase(repository: StorageAccessRepository): RevokeBooksAccessUseCase =
        RevokeBooksAccessUseCase(repository)

    @Provides
    fun provideRevokeMihonAccessUseCase(repository: StorageAccessRepository): RevokeMihonAccessUseCase =
        RevokeMihonAccessUseCase(repository)

    @Provides
    fun provideGetMihonCurrentBookUseCase(
        @MihonSource repository: CurrentBookRepository,
    ): GetMihonCurrentBookUseCase =
        GetMihonCurrentBookUseCase(repository)

    @Provides
    fun provideGetMihonDailyReadingStatsUseCase(
        @MihonSource repository: ReadingStatsRepository,
    ): GetMihonDailyReadingStatsUseCase =
        GetMihonDailyReadingStatsUseCase(repository)

    @Provides
    fun provideMergeDailyReadingStatsUseCase(): MergeDailyReadingStatsUseCase =
        MergeDailyReadingStatsUseCase()

    @Provides
    fun provideGetMergedDailyReadingStatsUseCase(
        getDailyReadingStats: GetDailyReadingStatsUseCase,
        getMihonDailyReadingStats: GetMihonDailyReadingStatsUseCase,
        merge: MergeDailyReadingStatsUseCase,
    ): GetMergedDailyReadingStatsUseCase =
        GetMergedDailyReadingStatsUseCase(getDailyReadingStats, getMihonDailyReadingStats, merge)

    @Provides
    fun provideGetStatsSourceFilterUseCase(repository: StatsFilterRepository): GetStatsSourceFilterUseCase =
        GetStatsSourceFilterUseCase(repository)

    @Provides
    fun provideSetStatsSourceFilterUseCase(repository: StatsFilterRepository): SetStatsSourceFilterUseCase =
        SetStatsSourceFilterUseCase(repository)

    @Provides
    fun provideIsMihonGrantNarrowUseCase(repository: MihonGrantRepository): IsMihonGrantNarrowUseCase =
        IsMihonGrantNarrowUseCase(repository)

    @Provides
    fun provideBucketDailyReadingStatsUseCase(): BucketDailyReadingStatsUseCase =
        BucketDailyReadingStatsUseCase()
}
