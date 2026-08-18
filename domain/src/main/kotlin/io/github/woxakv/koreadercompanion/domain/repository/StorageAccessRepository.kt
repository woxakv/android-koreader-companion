package io.github.woxakv.koreadercompanion.domain.repository

import io.github.woxakv.koreadercompanion.core.result.Try

/**
 * Uses plain String for the granted tree identifier (never android.net.Uri)
 * so this contract stays framework-free - the data layer converts at its
 * edge. Koreader and Books grants are tracked independently (see
 * StorageTarget) since they're typically sibling folders that can't share a
 * single grant.
 */
interface StorageAccessRepository {
    suspend fun hasAccess(target: StorageTarget): Boolean
    suspend fun grantAccess(target: StorageTarget, treeUriString: String): Try<Unit>
    suspend fun grantedTreeUriString(target: StorageTarget): String?
    suspend fun revokeAccess(target: StorageTarget)
}
