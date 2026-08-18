package io.github.woxakv.koreadercompanion.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.woxakv.koreadercompanion.data.repository.CurrentBookRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.MihonCurrentBookRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.MihonGrantRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.MihonReadingStatsRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.ReadingStatsRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.StatsFilterRepositoryImpl
import io.github.woxakv.koreadercompanion.data.repository.StorageAccessRepositoryImpl
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import io.github.woxakv.koreadercompanion.domain.repository.MihonGrantRepository
import io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @KoreaderSource
    @Binds
    abstract fun bindCurrentBookRepository(impl: CurrentBookRepositoryImpl): CurrentBookRepository

    @KoreaderSource
    @Binds
    abstract fun bindReadingStatsRepository(impl: ReadingStatsRepositoryImpl): ReadingStatsRepository

    @MihonSource
    @Binds
    abstract fun bindMihonCurrentBookRepository(impl: MihonCurrentBookRepositoryImpl): CurrentBookRepository

    @MihonSource
    @Binds
    abstract fun bindMihonReadingStatsRepository(impl: MihonReadingStatsRepositoryImpl): ReadingStatsRepository

    @Binds
    abstract fun bindStorageAccessRepository(impl: StorageAccessRepositoryImpl): StorageAccessRepository

    @Binds
    abstract fun bindStatsFilterRepository(impl: StatsFilterRepositoryImpl): StatsFilterRepository

    @Binds
    abstract fun bindMihonGrantRepository(impl: MihonGrantRepositoryImpl): MihonGrantRepository
}
