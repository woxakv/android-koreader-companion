package io.github.woxakv.koreadercompanion.data.local.epub

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a minimal synthetic epub in-memory rather than committing a binary
 * fixture, so no real book file ever ends up in the repo.
 */
class EpubCoverExtractorTest {

    private fun buildEpub(
        title: String = "Sample Book",
        includeCover: Boolean = true,
        epub3Style: Boolean = true,
        coverBytes: ByteArray = byteArrayOf(1, 2, 3, 4, 5),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            val coverManifestItem = if (includeCover) {
                if (epub3Style) {
                    """<item id="cover-img" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""
                } else {
                    """<item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"/>"""
                }
            } else {
                ""
            }
            val coverMeta = if (includeCover && !epub3Style) {
                """<meta name="cover" content="cover-img"/>"""
            } else {
                ""
            }

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                  <metadata>
                    <dc:title>$title</dc:title>
                    $coverMeta
                  </metadata>
                  <manifest>
                    $coverManifestItem
                    <item id="chapter1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            if (includeCover) {
                zip.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
                zip.write(coverBytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `extracts title from an epub3-style manifest`() {
        val bytes = buildEpub(title = "Sample Book")

        val title = EpubCoverExtractor.extractTitle { ByteArrayInputStream(bytes) }

        assertEquals("Sample Book", title)
    }

    @Test
    fun `extracts cover bytes from an epub3-style cover-image property`() {
        val cover = byteArrayOf(9, 8, 7, 6)
        val bytes = buildEpub(coverBytes = cover, epub3Style = true)

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `extracts cover bytes from an epub2-style meta cover reference`() {
        val cover = byteArrayOf(1, 1, 2, 3, 5)
        val bytes = buildEpub(coverBytes = cover, epub3Style = false)

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `returns null cover gracefully when the epub has none`() {
        val bytes = buildEpub(includeCover = false)

        val title = EpubCoverExtractor.extractTitle { ByteArrayInputStream(bytes) }
        val cover = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertEquals("Sample Book", title)
        assertNull(cover)
    }

    // --- Hardening fixtures below build their own minimal, bespoke epub per
    // case via [buildCustomEpub] rather than reusing [buildEpub], since each
    // one needs a specific OPF path, manifest `<item>`, or zip entry name
    // that the shared fixed-shape builder above doesn't parameterize.

    /**
     * Builds a minimal synthetic epub whose OPF lives at [opfPath] and whose
     * `<manifest>` contains exactly [coverItemXml], plus whatever
     * [extraEntries] (zip entry path -> bytes) the test needs alongside it -
     * typically the "cover image" the href is expected to resolve to.
     */
    private fun buildCustomEpub(
        opfPath: String,
        coverItemXml: String,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(opfPath))
            zip.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0">
                  <metadata>
                    <dc:title>Book</dc:title>
                  </metadata>
                  <manifest>
                    $coverItemXml
                  </manifest>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()

            extraEntries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `matches properties=cover-image case-insensitively`() {
        val cover = byteArrayOf(1, 2, 3)
        val bytes = buildCustomEpub(
            opfPath = "OEBPS/content.opf",
            coverItemXml = """<item id="cover-img" href="images/cover.jpg" media-type="image/jpeg" properties="Cover-Image"/>""",
            extraEntries = mapOf("OEBPS/images/cover.jpg" to cover),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `resolves a percent-encoded href against a zip entry with the decoded literal name`() {
        val cover = byteArrayOf(4, 5, 6)
        val bytes = buildCustomEpub(
            opfPath = "OEBPS/content.opf",
            coverItemXml = """<item id="cover-img" href="images/my%20cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
            // Zip entry uses the decoded literal name (a real space), as a real epub packer would write it.
            extraEntries = mapOf("OEBPS/images/my cover.jpg" to cover),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `does not corrupt a literal plus character in an href that was never percent-encoded`() {
        val cover = byteArrayOf(7, 8, 9)
        val bytes = buildCustomEpub(
            opfPath = "OEBPS/content.opf",
            coverItemXml = """<item id="cover-img" href="images/my+cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
            // Zip entry keeps the literal '+' too - the raw-name-first lookup must find this
            // directly rather than form-decoding the href and turning '+' into a space first.
            extraEntries = mapOf("OEBPS/images/my+cover.jpg" to cover),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `resolves a relative href with a parent-directory segment from a nested OPF`() {
        val cover = byteArrayOf(10, 11, 12)
        val bytes = buildCustomEpub(
            // OPF nested one level deeper than its target image, so opfDir + href
            // literally contains "..".
            opfPath = "OEBPS/package/content.opf",
            coverItemXml = """<item id="cover-img" href="../images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>""",
            extraEntries = mapOf("OEBPS/images/cover.jpg" to cover),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }

    @Test
    fun `rejects a cover-image item whose media-type is not a decodable image type`() {
        val bytes = buildCustomEpub(
            opfPath = "OEBPS/content.opf",
            coverItemXml = """<item id="cover-img" href="text/cover.xhtml" media-type="application/xhtml+xml" properties="cover-image"/>""",
            extraEntries = mapOf("OEBPS/text/cover.xhtml" to "<html/>".toByteArray()),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertNull(extracted)
    }

    @Test
    fun `resolves a cover-image item with no media-type attribute at all`() {
        val cover = byteArrayOf(13, 14, 15)
        val bytes = buildCustomEpub(
            opfPath = "OEBPS/content.opf",
            // No media-type attribute - Element#getAttribute returns "" for this, which must
            // not be mistaken for a rejected type.
            coverItemXml = """<item id="cover-img" href="images/cover.jpg" properties="cover-image"/>""",
            extraEntries = mapOf("OEBPS/images/cover.jpg" to cover),
        )

        val extracted = EpubCoverExtractor.extractCoverBytes { ByteArrayInputStream(bytes) }

        assertArrayEquals(cover, extracted)
    }
}
