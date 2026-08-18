package io.github.woxakv.koreadercompanion.data.repository

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.data.local.saf.StorageAccessLocalDataSource
import io.github.woxakv.koreadercompanion.domain.error.KoreaderError
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import javax.inject.Inject

class StorageAccessRepositoryImpl @Inject constructor(
    private val localDataSource: StorageAccessLocalDataSource,
) : StorageAccessRepository {

    override suspend fun hasAccess(target: StorageTarget): Boolean =
        localDataSource.grantedTreeUriString(target) != null

    override suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit> = runCatching {
        localDataSource.persistGrant(target, treeUriString)
    }.fold(
        onSuccess = { Try.Success(Unit) },
        onFailure = { throwable ->
            if (throwable is SecurityException) {
                Try.Failure(KoreaderError.PermissionNotGranted)
            } else {
                Try.Failure(KoreaderError.Unknown(throwable.message ?: throwable.toString()))
            }
        },
    )

    override suspend fun grantedTreeUriString(target: StorageTarget): String? =
        localDataSource.grantedTreeUriString(target)

    override suspend fun revokeAccess(target: StorageTarget) = localDataSource.clearGrant(target)
}
