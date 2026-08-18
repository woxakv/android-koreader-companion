package io.github.woxakv.koreadercompanion.data.local.saf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathSegmentResolverTest {

    @Test
    fun `resolves segments when the whole volume was granted`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/storage/emulated/0/Books/Foo.epub",
            treeRootDocumentId = "primary:",
        )

        assertEquals(listOf("Books", "Foo.epub"), segments)
    }

    @Test
    fun `resolves koreader settings path when the whole volume was granted`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/storage/emulated/0/koreader/settings.reader.lua",
            treeRootDocumentId = "primary:",
        )

        assertEquals(listOf("koreader", "settings.reader.lua"), segments)
    }

    @Test
    fun `resolves nested path when only the koreader subfolder was granted`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/storage/emulated/0/koreader/settings/statistics.sqlite3",
            treeRootDocumentId = "primary:koreader",
        )

        assertEquals(listOf("settings", "statistics.sqlite3"), segments)
    }

    @Test
    fun `returns null when the path is outside the granted subfolder`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/storage/emulated/0/Books/Foo.epub",
            treeRootDocumentId = "primary:koreader",
        )

        assertNull(segments)
    }

    @Test
    fun `returns null for a non-primary storage authority`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/storage/emulated/0/Books/Foo.epub",
            treeRootDocumentId = "1234-5678:",
        )

        assertNull(segments)
    }

    @Test
    fun `returns null when the absolute path is outside primary storage entirely`() {
        val segments = PathSegmentResolver.relativeSegments(
            absolutePath = "/sdcard1/Books/Foo.epub",
            treeRootDocumentId = "primary:",
        )

        assertNull(segments)
    }
}
