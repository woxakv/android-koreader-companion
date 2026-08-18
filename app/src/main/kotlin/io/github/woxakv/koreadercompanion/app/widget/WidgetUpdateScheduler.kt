package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.domain.scheduler.WidgetRefreshScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 15-minute refresh interval - Android's hard floor for periodic background
 * work (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`), used to keep
 * the widget as current as possible without visible e-ink ghosting or
 * excessive battery cost on the Palma 2.
 */
class WidgetUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefreshScheduler {
    override fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(ONUPDATE_HEADSTART_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * The periodic job above is at the mercy of the OS's battery/Doze
     * throttling, which can be aggressive on e-ink devices - a stale widget
     * would otherwise only recover whenever the OS next lets the periodic
     * job run, which can be a long, unpredictable wait. Opening the app is
     * a reliable moment to force a refresh regardless of that throttling.
     */
    override fun requestImmediateRefresh() {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(ONUPDATE_HEADSTART_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "widget_refresh"
        const val IMMEDIATE_WORK_NAME = "widget_refresh_immediate"

        /**
         * When a widget is freshly placed on the home screen, Android's own
         * `onUpdate()` callback starts a Glance session for it under the key
         * "appWidget-<id>" and enqueues that session's work via
         * `WorkManager.enqueueUniqueWork(sessionKey, ExistingWorkPolicy.REPLACE, ...)`.
         * If this scheduler's own refresh reaches that same widget ID within
         * the same few seconds, its `.update()` call legitimately cancels the
         * onUpdate()-triggered session mid-establishment via that same
         * per-widget-ID REPLACE policy, and it never recovers. Delaying this
         * app's own refresh gives onUpdate()'s session a head start so the two
         * don't race. 5 seconds is a starting estimate, not a verified-safe
         * number - tune here if on-device testing shows it isn't enough.
         */
        const val ONUPDATE_HEADSTART_DELAY_SECONDS = 5L
    }
}
