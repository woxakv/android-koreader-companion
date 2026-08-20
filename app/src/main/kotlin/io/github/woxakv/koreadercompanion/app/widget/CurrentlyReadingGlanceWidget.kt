package io.github.woxakv.koreadercompanion.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import io.github.woxakv.koreadercompanion.app.MainActivity
import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.error.isSetupIssue
import io.github.woxakv.koreadercompanion.domain.usecase.GetCurrentBookUseCase
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.CurrentBookUi
import io.github.woxakv.koreadercompanion.presentation.currentlyreading.toUi
import io.github.woxakv.koreadercompanion.presentation.util.openInKoreaderIntent
import io.github.woxakv.koreadercompanion.presentation.widget.CurrentlyReadingWidgetContent

private const val SETUP_MESSAGE = "Open KOReader Companion to set up"

/**
 * The concrete GlanceAppWidget lives in :app (not :presentation) because it
 * needs actionStartActivity<MainActivity>(), and MainActivity lives here.
 */
class CurrentlyReadingGlanceWidget(
    private val getCurrentBook: GetCurrentBookUseCase,
) : GlanceAppWidget() {

    // Single (the default) composes once for one nominal size and lets the
    // host stretch the result, which left dead whitespace on launchers that
    // grant more room than the nominal size. Exact recomposes for the real
    // current size, so LocalSize (read in CurrentlyReadingWidgetContent to
    // scale the cover) reflects reality.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // getCurrentBook() already converts its own failures to Try.Failure,
        // but this stays defensive against anything unexpected (e.g. cover
        // bitmap decoding) so a bug here surfaces as the widget's normal
        // "not set up" fallback rather than leaving it frozen on stale data.
        val result = runCatching { getCurrentBook() }.getOrNull()
        val book = (result as? Try.Success)?.value?.toUi()
        // Only a genuinely never-configured error keeps the generic setup
        // prompt; every other failure surfaces its own message instead of
        // collapsing to that same misleading text (see KoreaderError.kt).
        val emptyStateMessage = (result as? Try.Failure)?.error
            ?.takeUnless { it.isSetupIssue() }
            ?.message
            ?: SETUP_MESSAGE

        provideContent {
            CurrentlyReadingWidgetContent(
                book = book,
                onClick = actionStartActivity(tapIntent(context, book)),
                emptyStateMessage = emptyStateMessage,
            )
        }
    }

    /** Tapping the widget opens the book directly in KOReader when possible, else opens the app. */
    private fun tapIntent(context: Context, book: CurrentBookUi?): Intent =
        book?.openInKoreaderUri?.let { openInKoreaderIntent(context, it) }
            ?: Intent(context, MainActivity::class.java)
}
