package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook
import io.github.woxakv.koreadercompanion.domain.repository.CurrentBookRepository

class GetMihonCurrentBookUseCase(
    private val repository: CurrentBookRepository,
) {
    suspend operator fun invoke(): Try<CurrentBook> = repository.getCurrentBook()
}
