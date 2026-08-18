package io.github.woxakv.koreadercompanion.data.local.epub

import org.w3c.dom.Element
import java.io.InputStream
import java.net.URLDecoder

/**
 * Pure zip/XML logic: extracts a book's title and cover image bytes from its
 * epub file. Takes a stream *supplier* rather than a single stream because
 * ZipInputStream is forward-only and each lookup needs its own pass over the
 * archive. Fails gracefully (returns null) at any stage rather than
 * throwing, since a malformed or cover-less epub isn't an error condition.
 */
object EpubCoverExtractor {

    fun extractTitle(zipStreamSupplier: () -> InputStream): String? {
        val opfPath = EpubOpfLocator.locateOpfPath(zipStreamSupplier) ?: return null
        val opfXml = EpubOpfLocator.readZipEntry(zipStreamSupplier, opfPath)?.decodeToString() ?: return null
        val doc = EpubOpfLocator.parseXml(opfXml)
        val titles = doc.getElementsByTagName("dc:title")
        if (titles.length == 0) return null
        return titles.item(0).textContent?.trim()?.ifBlank { null }
    }

    fun extractCoverBytes(zipStreamSupplier: () -> InputStream): ByteArray? {
        val opfPath = EpubOpfLocator.locateOpfPath(zipStreamSupplier) ?: return null
        val opfXml = EpubOpfLocator.readZipEntry(zipStreamSupplier, opfPath)?.decodeToString() ?: return null
        val coverHref = findCoverHref(EpubOpfLocator.parseXml(opfXml)) ?: return null

        val opfDir = opfPath.substringBeforeLast("/", missingDelimiterValue = "")
        // `../`/`./` segments are legal in an OPF href (e.g. a cover
        // referenced from an OPF nested under "OEBPS/package/" as
        // "../images/cover.jpg") but zip entries are always flat, absolute
        // paths - normalize before using this as a lookup key.
        val coverEntryPath = normalizeZipPath(if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref")

        // OPF hrefs are sometimes percent-encoded (e.g. a space as "%20"),
        // but the zip entry itself is stored under its literal, undecoded
        // name. Try the raw name first: `URLDecoder.decode` is *form*
        // decoding, not URI decoding, so it also turns a literal '+' into a
        // space - decoding unconditionally would corrupt a filename that
        // genuinely contains '+' and was never percent-encoded. Only fall
        // back to the decoded form if the raw lookup misses.
        EpubOpfLocator.readZipEntry(zipStreamSupplier, coverEntryPath)?.let { return it }
        val decodedEntryPath = percentDecode(coverEntryPath)
        if (decodedEntryPath == coverEntryPath) return null
        return EpubOpfLocator.readZipEntry(zipStreamSupplier, decodedEntryPath)
    }

    /**
     * Collapses `.`/`..` path segments (e.g. "OEBPS/package/../images/x.jpg"
     * -> "OEBPS/images/x.jpg") so a relative href resolves to the same flat
     * name the zip entry is actually stored under. Implemented as manual
     * segment-stack normalization rather than `java.nio.file.Paths` so the
     * result always uses '/' regardless of the host OS's path separator -
     * zip entries are '/'-separated by spec.
     */
    private fun normalizeZipPath(path: String): String {
        val segments = mutableListOf<String>()
        for (segment in path.split("/")) {
            when (segment) {
                "", "." -> Unit // skip empty (from "//") and current-dir segments
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments.add(segment)
            }
        }
        return segments.joinToString("/")
    }

    /** Form-decodes `value`, falling back to the original string on malformed input. */
    private fun percentDecode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun findCoverHref(doc: org.w3c.dom.Document): String? {
        val items = doc.getElementsByTagName("item")

        // EPUB3: <item ... properties="cover-image" href="...">
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val properties = item.getAttribute("properties").lowercase().split(" ")
            if (properties.contains("cover-image") && isUsableCoverMediaType(item)) {
                return item.getAttribute("href").ifBlank { null }
            }
        }

        // EPUB2: <meta name="cover" content="ITEM_ID"/> -> <item id="ITEM_ID" href="...">
        val metas = doc.getElementsByTagName("meta")
        var coverItemId: String? = null
        for (i in 0 until metas.length) {
            val meta = metas.item(i) as Element
            if (meta.getAttribute("name").lowercase() == "cover") {
                coverItemId = meta.getAttribute("content")
                break
            }
        }
        if (coverItemId.isNullOrBlank()) return null

        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            if (item.getAttribute("id") == coverItemId && isUsableCoverMediaType(item)) {
                return item.getAttribute("href").ifBlank { null }
            }
        }
        return null
    }

    /**
     * `media-type` is optional on an OPF manifest `<item>`; `Element.getAttribute`
     * returns "" (not null) when the attribute is absent, so a blank value
     * must be let through unchanged - many valid, working OPFs omit it, and
     * rejecting on absence would be a silent regression for covers that
     * resolve fine today. When it *is* present, only reject media types
     * `BitmapFactory` can never decode into a displayable bitmap: anything
     * outside the "image" top-level type, plus SVG specifically (an
     * XML/text image format `BitmapFactory.decodeByteArray` returns null
     * for, with no error surfaced downstream).
     */
    private fun isUsableCoverMediaType(item: Element): Boolean {
        val mediaType = item.getAttribute("media-type")
        if (mediaType.isBlank()) return true
        return mediaType.startsWith("image/") && mediaType != "image/svg+xml"
    }
}
