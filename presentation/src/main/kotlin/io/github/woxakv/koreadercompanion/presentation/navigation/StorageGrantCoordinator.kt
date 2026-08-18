package io.github.woxakv.koreadercompanion.presentation.navigation

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.domain.usecase.GrantBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.GrantMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.HasMihonAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeBooksAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeKoreaderAccessUseCase
import io.github.woxakv.koreadercompanion.domain.usecase.RevokeMihonAccessUseCase

/**
 * The grant-dispatch logic shared by OnboardingViewModel and ConfigViewModel - which
 * Grant/Has use case to call for a given [StorageTarget]. This is the one piece of
 * genuinely identical logic between the two ViewModels; what happens around a grant
 * (navigating away, scheduling periodic widget refresh, etc.) differs per screen and
 * stays in each ViewModel, not here.
 */
class StorageGrantCoordinator(
    private val hasKoreaderAccess: HasKoreaderAccessUseCase,
    private val grantKoreaderAccess: GrantKoreaderAccessUseCase,
    private val revokeKoreaderAccess: RevokeKoreaderAccessUseCase,
    private val hasBooksAccess: HasBooksAccessUseCase,
    private val grantBooksAccess: GrantBooksAccessUseCase,
    private val revokeBooksAccess: RevokeBooksAccessUseCase,
    private val hasMihonAccess: HasMihonAccessUseCase,
    private val grantMihonAccess: GrantMihonAccessUseCase,
    private val revokeMihonAccess: RevokeMihonAccessUseCase,
) {
    suspend fun currentGrants(): Map<StorageTarget, Boolean> = mapOf(
        StorageTarget.KOREADER to hasKoreaderAccess(),
        StorageTarget.BOOKS to hasBooksAccess(),
        StorageTarget.MIHON to hasMihonAccess(),
    )

    suspend fun grant(target: StorageTarget, treeUriString: String): Try<Unit> = when (target) {
        StorageTarget.KOREADER -> grantKoreaderAccess(treeUriString)
        StorageTarget.BOOKS -> grantBooksAccess(treeUriString)
        StorageTarget.MIHON -> grantMihonAccess(treeUriString)
    }

    suspend fun revoke(target: StorageTarget) {
        when (target) {
            StorageTarget.KOREADER -> revokeKoreaderAccess()
            StorageTarget.BOOKS -> revokeBooksAccess()
            StorageTarget.MIHON -> revokeMihonAccess()
        }
    }
}
