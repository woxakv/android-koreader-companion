package io.github.woxakv.koreadercompanion.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reuses the same DataStore<Preferences> singleton instance as
 * StorageAccessLocalDataSource (see data/local/saf) - just a different key
 * on it, not a new DataStore.
 */
@Singleton
class StatsFilterLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringPreferencesKey("stats_source_filter")

    suspend fun getFilter(): StatsSourceFilter {
        val stored = dataStore.data.first()[key] ?: return StatsSourceFilter.ALL
        // Falls back to ALL rather than crashing on a corrupt/old value.
        return runCatching { StatsSourceFilter.valueOf(stored) }.getOrDefault(StatsSourceFilter.ALL)
    }

    suspend fun setFilter(filter: StatsSourceFilter) {
        dataStore.edit { it[key] = filter.name }
    }
}
