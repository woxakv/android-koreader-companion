package io.github.woxakv.koreadercompanion.app.widget

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Per-widget-instance "a manual refresh is in flight" flag, persisted via
 * Glance's default [androidx.glance.appwidget.state.PreferencesGlanceStateDefinition].
 * Set true by [WidgetManualRefreshAction] right before it triggers a
 * refresh; cleared by [WidgetRefreshWorker] once that refresh completes -
 * this also self-heals a stuck true value on the next periodic run if the
 * process dies mid-refresh, since the worker clears it unconditionally
 * every time it updates the combined widget, not just on the tapped one.
 */
val IS_REFRESHING_KEY = booleanPreferencesKey("is_refreshing")
