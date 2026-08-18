package io.github.woxakv.koreadercompanion.data.local.saf

import androidx.documentfile.provider.DocumentFile

/**
 * Thin Android-side glue: walks a resolved granted-tree DocumentFile down a
 * list of path segments (from PathSegmentResolver).
 */
object DocumentTreeResolver {

    fun resolve(root: DocumentFile, segments: List<String>): DocumentFile? {
        var current = root
        for (segment in segments) {
            current = current.findFile(segment) ?: return null
        }
        return current
    }
}
