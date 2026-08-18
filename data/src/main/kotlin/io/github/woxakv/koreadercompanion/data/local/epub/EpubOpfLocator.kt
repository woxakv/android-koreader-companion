package io.github.woxakv.koreadercompanion.data.local.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * An epub is a zip archive; META-INF/container.xml -> OPF path is the
 * lookup both title extraction and cover extraction need first. Shared here
 * so that work isn't duplicated between them.
 */
object EpubOpfLocator {

    fun locateOpfPath(zipStreamSupplier: () -> InputStream): String? {
        val containerXml = readZipEntry(zipStreamSupplier, "META-INF/container.xml") ?: return null
        val doc = parseXml(containerXml.decodeToString())
        val rootfiles = doc.getElementsByTagName("rootfile")
        if (rootfiles.length == 0) return null
        val fullPath = (rootfiles.item(0) as Element).getAttribute("full-path")
        return fullPath.ifBlank { null }
    }

    /**
     * Deliberately reads via [ZipFile] (Central Directory, random access),
     * not [java.util.zip.ZipInputStream] (sequential, local-file-headers
     * only) - device-verified real-world epubs exist whose local file
     * headers `ZipInputStream` can't reliably walk past the first entry
     * (confirmed: a Calibre-produced epub where `ZipInputStream` found only
     * the mandatory leading `mimetype` entry and silently reported no more
     * entries, while `unzip`/Python's `zipfile` - both Central-Directory
     * based, like every real EPUB reader including KOReader itself - read
     * the full archive fine). `ZipFile` needs random access, which a SAF
     * `content://` stream doesn't give directly, so the stream is copied to
     * a throwaway temp file first - mirrors the same
     * SAF-stream-to-local-file pattern `StatisticsDatabaseCopier` already
     * uses for the analogous "needs real random access" problem with
     * `statistics.sqlite3`.
     */
    internal fun readZipEntry(zipStreamSupplier: () -> InputStream, entryPath: String): ByteArray? {
        val tempFile = File.createTempFile("epub_entry_", ".tmp")
        return try {
            zipStreamSupplier().use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
            ZipFile(tempFile).use { zipFile ->
                val entry = zipFile.getEntry(entryPath) ?: return null
                zipFile.getInputStream(entry).use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

    internal fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            // Defense in depth against XXE where the parser supports this
            // Xerces-specific feature - Android's built-in XML parser
            // doesn't, and throws rather than just ignoring it, so this
            // can't be a hard requirement.
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (e: ParserConfigurationException) {
                // Not supported on this platform's parser; proceed without it.
            }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }
}
