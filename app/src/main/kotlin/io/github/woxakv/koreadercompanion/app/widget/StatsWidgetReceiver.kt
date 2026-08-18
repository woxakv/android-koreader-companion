package io.github.woxakv.koreadercompanion.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase
import javax.inject.Inject

@AndroidEntryPoint
class StatsWidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var getReadingStatsSummary: GetReadingStatsSummaryUseCase
    @Inject
    lateinit var getMergedDailyReadingStats: GetMergedDailyReadingStatsUseCase
    @Inject
    lateinit var getStatsSourceFilter: GetStatsSourceFilterUseCase

    override val glanceAppWidget: GlanceAppWidget
        get() = StatsGlanceWidget(getReadingStatsSummary, getMergedDailyReadingStats, getStatsSourceFilter)
}
