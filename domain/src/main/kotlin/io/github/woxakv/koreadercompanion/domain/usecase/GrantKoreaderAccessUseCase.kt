package io.github.woxakv.koreadercompanion.domain.usecase

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

class GrantKoreaderAccessUseCase(
    private val repository: StorageAccessRepository,
) {
    suspend operator fun invoke(treeUriString: String): Try<Unit> =
        repository.grantAccess(StorageTarget.KOREADER, treeUriString)
}
