package io.github.woxakv.koreadercompanion.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds the same kind of intent a file manager sends when you tap a file:
 * a plain ACTION_VIEW with both the URI and its actual MIME type set, and
 * no explicit target package. Explicitly targeting a guessed KOReader
 * package name (it differs by distribution: org.koreader.launcher vs
 * org.koreader.launcher.fdroid) turned out to be counter-productive - once
 * the user sets a default app for the file type at the OS level, letting
 * Android's normal implicit-intent resolution do its job is both simpler
 * and matches exactly what already works from a file manager.
 *
 * Known limitation, confirmed by direct device testing: KOReader opens to
 * its own home dashboard rather than the reading view when given this
 * content:// URI, even though its manifest declares support for the
 * content scheme and this intent matches that declaration exactly. A
 * side-by-side test with the identical file delivered as a file:// URI
 * opened straight into the book at the correct saved position, so the gap
 * is in KOReader's own content:// resolution, not this intent. We can't
 * send file:// here - Android throws FileUriExposedException for any app
 * targeting API 24+ that puts a file:// URI in a cross-app intent, and
 * there's no other KOReader-side mechanism (checked its manifest: no
 * custom action, deep link, or shortcut exists beyond these two ACTION_VIEW
 * filters). CLEAR_TOP is kept as a harmless best-effort nudge in case a
 * future KOReader release resolves content:// URIs correctly.
 */
fun openInKoreaderIntent(context: Context, bookContentUriString: String): Intent {
    val uri = Uri.parse(bookContentUriString)
    val mimeType = context.contentResolver.getType(uri) ?: "application/epub+zip"

    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
