package io.github.woxakv.koreadercompanion.domain.repository

/**
 * KOReader's settings/stats folder and the book library folder are commonly
 * sibling folders directly under the storage root, and Android's Storage
 * Access Framework refuses to grant the literal root itself - so the two
 * need independent grants rather than one covering both.
 */
enum class StorageTarget {
    /** Required: settings.reader.lua, history.lua, settings/statistics.sqlite3. */
    KOREADER,

    /** Optional: only needed to extract cover art from the book's epub. */
    BOOKS,

    /**
     * Optional: Mihon's `mihon/` root folder, covering both `autobackup/`
     * (`.tachibk` auto-backup, used for a second currently-reading card and
     * folded into stats) and the sibling `downloads/` folder (per-manga
     * `.cbz` chapters, used for cover art). A single grant at `mihon/`
     * reaches both, since they're siblings rather than nested.
     */
    MIHON,
}
