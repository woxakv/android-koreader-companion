package io.github.woxakv.koreadercompanion.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import javax.inject.Inject

@AndroidEntryPoint
class CurrentlyReadingWidgetReceiver : GlanceAppWidgetReceiver() {

    @Inject
    lateinit var getCurrentBook: GetCurrentBookUseCase

    override val glanceAppWidget: GlanceAppWidget
        get() = CurrentlyReadingGlanceWidget(getCurrentBook)
}
