package io.github.woxakv.koreadercompanion.presentation.currentlyreading

import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentBookUiMapperTest {

    @Test
    fun `maps all fields including the open-in-KOReader URI`() {
        val book = CurrentBook(
            title = "L'Attentat",
            author = "Yasmina Khadra",
            progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
            estimatedSecondsRemaining = 11036L,
            coverImageBytes = null,
            bookContentUriString = "content://test/Foo.epub",
        )

        val ui = book.toUi()

        assertEquals("L'Attentat", ui.title)
        assertEquals("Yasmina Khadra", ui.author)
        assertEquals("48% Read", ui.percentLabel)
        assertEquals(176, ui.currentPage)
        assertEquals(364, ui.totalPages)
        assertEquals("3h 3m remaining", ui.timeRemainingLabel)
        assertEquals("content://test/Foo.epub", ui.openInKoreaderUri)
    }

    @Test
    fun `maps a missing content URI and estimate through as null`() {
        val book = CurrentBook(
            title = "L'Attentat",
            author = null,
            progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
            estimatedSecondsRemaining = null,
            coverImageBytes = null,
            bookContentUriString = null,
        )

        val ui = book.toUi()

        assertNull(ui.author)
        assertEquals(176, ui.currentPage)
        assertEquals(364, ui.totalPages)
        assertNull(ui.openInKoreaderUri)
        assertNull(ui.timeRemainingLabel)
    }

    @Test
    fun `maps an access note through when present`() {
        val book = CurrentBook(
            title = "L'Attentat",
            author = "Yasmina Khadra",
            progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
            estimatedSecondsRemaining = 11036L,
            coverImageBytes = null,
            bookContentUriString = null,
            bookAccessNote = "This book is stored inside KOReader's own private app folder.",
        )

        val ui = book.toUi()

        assertEquals(176, ui.currentPage)
        assertEquals(364, ui.totalPages)
        assertEquals("This book is stored inside KOReader's own private app folder.", ui.accessNote)
    }
}
