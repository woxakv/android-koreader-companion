package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

/** The koreader folder grant is required; the Books grant is optional (cover art only). */
class HasKoreaderAccessUseCase(
    private val repository: StorageAccessRepository,
) {
    suspend operator fun invoke(): Boolean = repository.hasAccess(StorageTarget.KOREADER)
}
