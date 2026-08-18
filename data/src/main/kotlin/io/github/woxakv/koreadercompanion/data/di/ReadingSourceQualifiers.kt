package io.github.woxakv.koreadercompanion.data.di

import javax.inject.Qualifier

/**
 * Qualifies the KOReader-backed [io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository] /
 * [io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository] bindings, needed now that a
 * second (Mihon) implementation of the same interfaces exists - see [MihonSource].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KoreaderSource

/**
 * Qualifies the Mihon-backed [io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository] /
 * [io.github.woxakv.koreadercompanion.domain.repository.ReadingStatsRepository] bindings - see [KoreaderSource].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MihonSource
