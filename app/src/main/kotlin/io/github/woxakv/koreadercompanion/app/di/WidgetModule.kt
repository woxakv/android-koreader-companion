package io.github.woxakv.koreadercompanion.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.woxakv.koreadercompanion.app.widget.WidgetUpdateScheduler
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    abstract fun bindWidgetRefreshScheduler(impl: WidgetUpdateScheduler): WidgetRefreshScheduler
}
