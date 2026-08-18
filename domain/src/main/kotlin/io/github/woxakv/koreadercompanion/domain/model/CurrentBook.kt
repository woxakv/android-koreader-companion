package io.github.woxakv.koreadercompanion.domain.model

data class CurrentBook(
    val title: String,
    val author: String?,
    val progress: ReadingProgress,
    val estimatedSecondsRemaining: Long?,
    val coverImageBytes: ByteArray?,
    /**
     * The book file's content:// URI (as a String - domain stays framework-
     * free), if it could be resolved under the granted Books tree. Present
     * independent of whether the cover/title matched, so "open in KOReader"
     * works even when cover pairing was declined.
     */
    val bookContentUriString: String?,
    /**
     * Set when the book couldn't be reached for a reason worth explaining to
     * the user (e.g. it lives inside another app's private storage, which
     * Android blocks every other app from reading, full stop - no cover or
     * open-in-KOReader is possible for that book, and that's not fixable
     * from here). Null whenever there's nothing unusual to explain.
     */
    val bookAccessNote: String? = null,
    /**
     * Android package name to launch via
     * `PackageManager.getLaunchIntentForPackage` when this book comes from
     * an app that can't be opened to a specific document (e.g. Mihon: a
     * plain app-launch, not a `content://` document-open like
     * [bookContentUriString]). Null for sources - KOReader included - where
     * [bookContentUriString] is the right open mechanism instead. The two
     * open-mechanisms are deliberately kept separate rather than conflated
     * behind one field.
     */
    val launchIntentPackage: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CurrentBook) return false
        return title == other.title &&
            author == other.author &&
            progress == other.progress &&
            estimatedSecondsRemaining == other.estimatedSecondsRemaining &&
            bookContentUriString == other.bookContentUriString &&
            bookAccessNote == other.bookAccessNote &&
            launchIntentPackage == other.launchIntentPackage &&
            (coverImageBytes?.contentEquals(other.coverImageBytes) ?: (other.coverImageBytes == null))
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + progress.hashCode()
        result = 31 * result + (estimatedSecondsRemaining?.hashCode() ?: 0)
        result = 31 * result + (bookContentUriString?.hashCode() ?: 0)
        result = 31 * result + (bookAccessNote?.hashCode() ?: 0)
        result = 31 * result + (launchIntentPackage?.hashCode() ?: 0)
        result = 31 * result + (coverImageBytes?.contentHashCode() ?: 0)
        return result
    }
}
