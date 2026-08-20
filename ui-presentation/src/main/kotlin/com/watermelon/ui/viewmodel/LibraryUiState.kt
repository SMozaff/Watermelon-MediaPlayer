package com.watermelon.ui.viewmodel

/**
 * The user-visible state of a library collection. This must not be inferred from an empty
 * list alone: a collection can be loading, genuinely empty, unavailable after a failed refresh,
 * or ready to render content.
 */
sealed interface LibraryUiState {
    /** Initial indexing is in progress and no previously indexed content is available. */
    data object Loading : LibraryUiState

    /** Indexing completed successfully but the current library context contains no media. */
    data object Empty : LibraryUiState

    /** The current library context contains one or more media items. */
    data object Content : LibraryUiState

    /** Refresh/indexing failed and the user needs a recovery action. */
    data class Error(val message: String) : LibraryUiState
}

internal sealed interface LibraryRefreshState {
    data object Loading : LibraryRefreshState
    data object Complete : LibraryRefreshState
    data class Failed(val message: String) : LibraryRefreshState
}
