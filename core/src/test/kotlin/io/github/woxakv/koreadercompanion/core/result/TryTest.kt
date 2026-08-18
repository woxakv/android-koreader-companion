package io.github.woxakv.koreadercompanion.core.result

import io.github.woxakv.koreadercompanion.core.error.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private data class TestError(override val message: String) : AppError

class TryTest {

    @Test
    fun `map transforms success value`() {
        val result: Try<Int> = Try.Success(2)

        val mapped = result.map { it * 10 }

        assertEquals(Try.Success(20), mapped)
    }

    @Test
    fun `map passes through failure unchanged`() {
        val error = TestError("boom")
        val result: Try<Int> = Try.Failure(error)

        val mapped = result.map { it * 10 }

        assertEquals(Try.Failure(error), mapped)
    }

    @Test
    fun `fold calls onSuccess for success`() {
        val result: Try<Int> = Try.Success(5)

        val folded = result.fold(onSuccess = { it.toString() }, onFailure = { "error" })

        assertEquals("5", folded)
    }

    @Test
    fun `fold calls onFailure for failure`() {
        val result: Try<Int> = Try.Failure(TestError("boom"))

        val folded = result.fold(onSuccess = { it.toString() }, onFailure = { it.message })

        assertEquals("boom", folded)
    }

    @Test
    fun `getOrNull returns value for success`() {
        val result: Try<Int> = Try.Success(7)

        assertEquals(7, result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for failure`() {
        val result: Try<Int> = Try.Failure(TestError("boom"))

        assertNull(result.getOrNull())
    }

    @Test
    fun `onSuccess runs action only for success`() {
        var invoked = false
        val result: Try<Int> = Try.Success(1)

        result.onSuccess { invoked = true }

        assertEquals(true, invoked)
    }

    @Test
    fun `onFailure runs action only for failure`() {
        var invoked = false
        val result: Try<Int> = Try.Failure(TestError("boom"))

        result.onFailure { invoked = true }

        assertEquals(true, invoked)
    }
}
