package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeCurrentBookRepository(private val result: Try<CurrentBook>) : CurrentBookRepository {
    override suspend fun getCurrentBook(): Try<CurrentBook> = result
}

class GetCurrentBookUseCaseTest {

    private val book = CurrentBook(
        title = "L'Attentat",
        author = "Yasmina Khadra",
        progress = ReadingProgress(currentPage = 176, totalPages = 364, totalReadTimeSeconds = 8688, totalReadPages = 148),
        estimatedSecondsRemaining = 11036L,
        coverImageBytes = null,
        bookContentUriString = null,
    )

    @Test
    fun `passes through a successful result`() = runTest {
        val useCase = GetCurrentBookUseCase(FakeCurrentBookRepository(Try.Success(book)))

        val result = useCase()

        assertEquals(Try.Success(book), result)
    }

    @Test
    fun `passes through a failure result`() = runTest {
        val failure = Try.Failure(KoreaderError.NoBookFound)
        val useCase = GetCurrentBookUseCase(FakeCurrentBookRepository(failure))

        val result = useCase()

        assertEquals(failure, result)
    }
}
