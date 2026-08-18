package io.github.woxakv.koreadercompanion.domain.error

import io.github.woxakv.koreadercompanion.core.error.AppError

sealed interface KoreaderError : AppError {

    data object PermissionNotGranted : KoreaderError {
        override val message = "Storage access has not been granted."
    }

    data class DataFolderNotFound(val path: String) : KoreaderError {
        override val message = "Could not find KOReader data at: $path"
    }

    data object StatisticsUnavailable : KoreaderError {
        override val message = "KOReader's statistics database could not be read."
    }

    data object NoBookFound : KoreaderError {
        override val message = "No book currently open."
    }

    data object ParseTimeout : KoreaderError {
        override val message = "Timed out parsing a KOReader data file."
    }

    data class Unknown(val cause: String) : KoreaderError {
        override val message = "Unexpected error: $cause"
    }
}

/**
 * True only for errors that mean KOReader access has never been configured
 * (permission never granted, or the configured folder no longer exists) -
 * the sole cases where "Open KOReader Companion to set up" is accurate.
 * Every other [KoreaderError] (stats unavailable, no current book, parse
 * timeout, unknown) is a configured-but-something-went-wrong state and
 * should surface its own [AppError.message] instead.
 */
fun AppError.isSetupIssue(): Boolean =
    this is KoreaderError.PermissionNotGranted || this is KoreaderError.DataFolderNotFound
