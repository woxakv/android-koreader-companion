package io.github.woxakv.koreadercompanion.presentation.navigation

import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

/**
 * Shared grant-state shape for OnboardingUiState and ConfigUiState (deliberate
 * near-duplicate screens - see plan 015's Context & decisions). Replaces what used
 * to be a separate per-target boolean field (one for koreader, one for books), which
 * doesn't scale past two targets (Mihon is the third).
 */
data class StorageGrantUiState(
    val grants: Map<StorageTarget, Boolean> = emptyMap(),
    val error: String? = null,
    // Only meaningful when grants[MIHON] == true - see ConfigScreen's re-grant hint
    // and IsMihonGrantNarrowUseCase's doc comment.
    val mihonGrantNarrow: Boolean = false,
)
