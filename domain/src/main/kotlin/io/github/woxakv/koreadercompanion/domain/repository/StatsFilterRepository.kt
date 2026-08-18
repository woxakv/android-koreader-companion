package io.github.woxakv.koreadercompanion.domain.repository

import io.github.woxakv.koreadercompanion.domain.model.StatsSourceFilter

/**
 * Persists the All/KOReader/Mihon stats filter so it's shared between the
 * in-app screen and widgets - widgets have no UI of their own to change it,
 * so they read whatever was last selected in-app.
 */
interface StatsFilterRepository {
    suspend fun getFilter(): StatsSourceFilter
    suspend fun setFilter(filter: StatsSourceFilter)
}
