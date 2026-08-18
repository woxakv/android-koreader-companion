package io.github.woxakv.koreadercompanion.data.repository

import io.github.woxakv.koreadercompanion.data.local.datastore.StatsFilterLocalDataSource
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import io.github.woxakv.koreadercompanion.domain.repository.StatsFilterRepository
import javax.inject.Inject

class StatsFilterRepositoryImpl @Inject constructor(
    private val localDataSource: StatsFilterLocalDataSource,
) : StatsFilterRepository {

    override suspend fun getFilter(): StatsSourceFilter = localDataSource.getFilter()

    override suspend fun setFilter(filter: StatsSourceFilter) = localDataSource.setFilter(filter)
}
