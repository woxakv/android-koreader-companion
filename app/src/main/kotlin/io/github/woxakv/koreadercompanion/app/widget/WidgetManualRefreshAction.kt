package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetMergedDailyReadingStatsUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetReadingStatsSummaryUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GetStatsSourceFilterUseCase

/**
 * Glance constructs an [ActionCallback] itself with a no-arg constructor, so
 * dependencies can't be constructor-injected the way [WidgetRefreshWorker]'s
 * are. This entry point reaches already-bound instances instead - it adds
 * no new binding. The use cases are only needed to construct a
 * [CombinedGlanceWidget] to force an immediate "loading" re-render; they're
 * the same dependencies [CombinedWidgetReceiver] already injects.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetManualRefreshEntryPoint {
    fun widgetRefreshScheduler(): WidgetRefreshScheduler
    fun getCurrentBook(): GetCurrentBookUseCase
    fun getReadingStatsSummary(): GetReadingStatsSummaryUseCase
    fun getMergedDailyReadingStats(): GetMergedDailyReadingStatsUseCase
    fun getStatsSourceFilter(): GetStatsSourceFilterUseCase
}

/**
 * Manual "refresh now" tap target for the widgets. It deliberately goes
 * through [WidgetRefreshScheduler.requestImmediateRefresh] rather than
 * enqueueing its own immediate work: that method's initial delay is what
 * stops a refresh from cancelling a freshly-placed widget's own
 * onUpdate()-triggered Glance session.
 *
 * Before requesting that refresh, it flips [IS_REFRESHING_KEY] on for the
 * tapped widget instance and forces one extra re-render so the button
 * reads "Loading..." immediately rather than only after the delayed
 * refresh eventually completes - [WidgetRefreshWorker] clears the flag
 * once the real refresh finishes.
 */
class WidgetManualRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetManualRefreshEntryPoint::class.java)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[IS_REFRESHING_KEY] = true
        }
        CombinedGlanceWidget(
            entryPoint.getCurrentBook(),
            entryPoint.getReadingStatsSummary(),
            entryPoint.getMergedDailyReadingStats(),
            entryPoint.getStatsSourceFilter(),
        ).update(context, glanceId)

        entryPoint.widgetRefreshScheduler().requestImmediateRefresh()
    }
}
