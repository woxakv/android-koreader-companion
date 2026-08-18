package io.github.woxakv.koreadercompanion.presentation.onboarding

import android.net.Uri
import android.provider.DocumentsContract

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * Hints the koreader-folder picker at KOReader's own fixed data
 * directory, matching the same "primary:koreader" path this app already
 * hardcodes elsewhere (e.g. CurrentBookRepositoryImpl's
 * KOREADER_STATISTICS_PATH) — safe to hardcode since KOReader itself
 * always uses this exact location, not something discovered at runtime.
 */
internal val KOREADER_FOLDER_HINT_URI: Uri =
    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:koreader")

/**
 * Hints the Books-folder picker at the generic storage root rather than a
 * guessed folder name — unlike the koreader folder, the book library's
 * real location is only discovered later from KOReader's own history
 * data (which needs koreader access already granted), so it can't be
 * reliably guessed here. Still fixes the reported cross-contamination
 * symptom, since neither picker inherits the other's navigation state.
 */
internal val STORAGE_ROOT_HINT_URI: Uri =
    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:")

/**
 * Hints the Mihon-folder picker at Mihon's own fixed root directory,
 * matching the same "primary:mihon" path convention as the koreader hint
 * above - safe to hardcode since Mihon always uses this exact location.
 * Points at `mihon/` itself (not `mihon/autobackup`) so the single grant
 * covers both the `autobackup/` subfolder (`.tachibk` files) and the
 * sibling `downloads/` subfolder (per-manga `.cbz` chapters, used for
 * cover art) - see StorageTarget.MIHON's doc comment.
 */
internal val MIHON_FOLDER_HINT_URI: Uri =
    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:mihon")
