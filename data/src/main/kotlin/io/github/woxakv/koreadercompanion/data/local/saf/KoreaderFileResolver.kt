package io.github.woxakv.koreadercompanion.data.local.saf

import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import javax.inject.Inject

/**
 * Resolves an absolute KOReader-recorded device path (from the .lua files)
 * to a DocumentFile under the granted SAF tree. A single seam shared by both
 * book lookups and koreader-settings lookups, and the mock point for
 * CurrentBookRepositoryImpl's tests.
 */
class KoreaderFileResolver @Inject constructor() {

    fun resolve(root: DocumentFile, absolutePath: String): DocumentFile? {
        val treeRootDocumentId = DocumentsContract.getTreeDocumentId(root.uri)
        val segments = PathSegmentResolver.relativeSegments(absolutePath, treeRootDocumentId) ?: return null
        return DocumentTreeResolver.resolve(root, segments)
    }
}
