package io.github.woxakv.koreadercompanion.data.local.saf

/**
 * Pure path math: turns an absolute device path (as recorded by KOReader's
 * own .lua files) into the segments needed to walk down from a granted SAF
 * tree root via DocumentFile.findFile. No Android imports, so this is
 * testable on plain JVM.
 */
object PathSegmentResolver {

    private const val PRIMARY_AUTHORITY_PREFIX = "primary:"

    fun relativeSegments(
        absolutePath: String,
        treeRootDocumentId: String,
        primaryStorageRoot: String = "/storage/emulated/0/",
    ): List<String>? {
        if (!absolutePath.startsWith(primaryStorageRoot)) return null
        if (!treeRootDocumentId.startsWith(PRIMARY_AUTHORITY_PREFIX)) return null

        val relativeToRoot = absolutePath.removePrefix(primaryStorageRoot)
        val treeRelativePrefix = treeRootDocumentId.removePrefix(PRIMARY_AUTHORITY_PREFIX)

        val relativeToTree = if (treeRelativePrefix.isEmpty()) {
            relativeToRoot
        } else {
            val prefixWithSlash = "$treeRelativePrefix/"
            if (!relativeToRoot.startsWith(prefixWithSlash)) return null
            relativeToRoot.removePrefix(prefixWithSlash)
        }

        val segments = relativeToTree.split("/").filter { it.isNotEmpty() }
        return segments.ifEmpty { null }
    }
}
