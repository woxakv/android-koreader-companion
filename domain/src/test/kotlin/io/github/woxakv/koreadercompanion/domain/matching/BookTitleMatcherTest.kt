package io.github.woxakv.koreadercompanion.domain.matching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookTitleMatcherTest {

    @Test
    fun `exact title matches`() {
        assertTrue(BookTitleMatcher.matches("L'Attentat", "L'Attentat"))
    }

    @Test
    fun `case and apostrophe-elision differences still match`() {
        assertTrue(BookTitleMatcher.matches("l'attentat", "L'Attentat"))
    }

    @Test
    fun `different titles do not match`() {
        assertFalse(BookTitleMatcher.matches("Wind and Truth", "L'Attentat"))
    }

    @Test
    fun `null epub title fails closed`() {
        assertFalse(BookTitleMatcher.matches(null, "L'Attentat"))
    }

    @Test
    fun `blank epub title fails closed`() {
        assertFalse(BookTitleMatcher.matches("   ", "L'Attentat"))
    }

    @Test
    fun `leading English article is ignored`() {
        assertTrue(BookTitleMatcher.matches("The Wind and Truth", "Wind and Truth"))
    }
}
