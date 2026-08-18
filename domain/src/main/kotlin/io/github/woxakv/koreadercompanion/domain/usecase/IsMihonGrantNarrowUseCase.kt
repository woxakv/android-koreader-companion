package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.repository.MihonGrantRepository

class IsMihonGrantNarrowUseCase(
    private val repository: MihonGrantRepository,
) {
    suspend operator fun invoke(): Boolean = repository.isGrantNarrow()
}
