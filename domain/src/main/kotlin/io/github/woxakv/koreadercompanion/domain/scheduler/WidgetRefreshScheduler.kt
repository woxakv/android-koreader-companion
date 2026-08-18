package io.github.woxakv.koreadercompanion.domain.scheduler

interface WidgetRefreshScheduler {
    fun schedulePeriodicRefresh()
    fun requestImmediateRefresh()
}
