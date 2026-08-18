package io.github.woxakv.koreadercompanion.presentation.util

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KoreaderLauncherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `builds a plain view intent with the URI and MIME type set, no target package`() {
        val intent = openInKoreaderIntent(context, "content://test/Foo.epub")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://test/Foo.epub", intent.data.toString())
        // No content provider is registered for "test" in this test, so the
        // fallback default applies; a real content:// URI from our SAF grant
        // reports its own type, same as a file manager would query.
        assertEquals("application/epub+zip", intent.type)
        assertNull(intent.`package`)
    }

    @Test
    fun `grants read permission to whichever app the system resolves this to`() {
        val intent = openInKoreaderIntent(context, "content://test/Foo.epub")

        val hasGrantFlag = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0
        assertEquals(true, hasGrantFlag)
    }

    @Test
    fun `requests a fresh dispatch rather than resuming an existing instance`() {
        val intent = openInKoreaderIntent(context, "content://test/Foo.epub")

        val hasClearTopFlag = intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0
        assertEquals(true, hasClearTopFlag)
    }
}
