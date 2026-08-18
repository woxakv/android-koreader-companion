package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

class RevokeBooksAccessUseCase(
    private val repository: StorageAccessRepository,
) {
    suspend operator fun invoke() = repository.revokeAccess(StorageTarget.BOOKS)
}
