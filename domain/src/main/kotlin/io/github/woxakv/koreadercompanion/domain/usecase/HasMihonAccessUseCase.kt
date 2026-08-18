package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

class HasMihonAccessUseCase(
    private val repository: StorageAccessRepository,
) {
    suspend operator fun invoke(): Boolean = repository.hasAccess(StorageTarget.MIHON)
}
