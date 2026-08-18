package io.github.woxakv.koreadercompanion.presentation.currentlyreading

sealed interface CurrentlyReadingUiState {
    data object Loading : CurrentlyReadingUiState
    data class Content(val book: CurrentBookUi) : CurrentlyReadingUiState
    data class PermissionRequired(val reason: String) : CurrentlyReadingUiState
    data class Error(val message: String, val retryable: Boolean) : CurrentlyReadingUiState
}
