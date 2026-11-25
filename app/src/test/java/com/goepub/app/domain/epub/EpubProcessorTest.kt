package com.goepub.app.domain.epub

import com.goepub.app.domain.translator.TranslationEngine
import com.goepub.app.ui.i18n.AppLanguage
import com.goepub.app.ui.i18n.AppStrings
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EpubProcessorTest {

    private class FakeTranslationEngine : TranslationEngine {
        val translatedInputs = mutableListOf<String>()

        override suspend fun areModelsReady(sourceLanguage: String, targetLanguage: String): Boolean = true

        override suspend fun ensureModel(
            sourceLanguage: String,
            targetLanguage: String,
            allowCellular: Boolean
        ) = Unit

        override suspend fun translate(
            text: String,
            sourceLanguage: String?,
            targetLanguage: String
        ): String {
            translatedInputs += text
            return "[ID:$text]"
        }

        override suspend fun detectLanguage(text: String): String = "en"
    }

    @Test
    fun `chunkText splits oversized paragraph cleanly at sentence boundaries`() {
        val sentence = "Ini adalah satu kalimat contoh yang cukup panjang untuk pengujian. "
        val longParagraph = sentence.repeat(120) // ~8000 karakter

        val chunks = TextChunker.chunkText(longParagraph, maxChunkSize = 1000)

        assertTrue("Harus dipecah menjadi beberapa chunk", chunks.size >= 8)
        assertTrue("Setiap chunk wajib <= maxChunkSize", chunks.all { it.length <= 1000 })
        assertTrue(
            "Potongan utamanya harus berakhir di tanda titik kalimat",
            chunks.dropLast(1).all { it.endsWith(".") }
        )
    }

    @Test
    fun `translateXhtmlContent preserves HTML tags, CSS attributes, inline spaces, and special entities`() = runTest {
        val tempRoot = Files.createTempDirectory("goepub_xhtml_test").toFile()
        try {
            val engine = FakeTranslationEngine()
            val processor = EpubProcessor(tempRoot, tempRoot, engine)

            val sourceXhtml = """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head>
                    <title>Original Head Title</title>
                    <link rel="stylesheet" type="text/css" href="../Styles/main.css" />
                    <style type="text/css">p.lead { color: #112233; }</style>
                </head>
                <body>
                    <section epub:type="chapter" class="chapter-box">
                        <h1 id="ch1">Chapter One</h1>
                        <p class="lead">Hello &amp; welcome, <b>dear</b> <i>reader</i>&nbsp;2026!</p>
                        <p translate="no">Do Not Translate Proper Noun</p>
                        <img src="../Images/cover.jpg" alt="Cover" />
                        <code>const x = 1 &amp;&amp; 2;</code>
                    </section>
                </body>
                </html>
            """.trimIndent()

            val result = processor.translateXhtmlContent(sourceXhtml, "en", "id")

            // Struktur CSS, head, gambar, atribut epub:type, dan <code> tidak boleh rusak
            assertTrue(result.contains("""href="../Styles/main.css""""))
            assertTrue(result.contains("""epub:type="chapter""""))
            assertTrue(result.contains("""class="chapter-box""""))
            assertTrue(result.contains("""<img src="../Images/cover.jpg" alt="Cover" />"""))
            assertTrue(result.contains("<title>Original Head Title</title>"))
            assertTrue(result.contains("const x = 1 &amp;&amp; 2;"))
            assertTrue(result.contains("""<p translate="no">Do Not Translate Proper Noun</p>"""))

            // Text nodes diterjemahkan dengan entitas &amp; tetap valid XML dan spasi inline terjaga
            assertTrue(result.contains("[ID:Chapter One]"))
            assertTrue(result.contains("[ID:Hello &amp; welcome,] <b>[ID:dear]</b> <i>[ID:reader]</i>"))
            assertTrue(
                "Non-breaking space (&nbsp; -> &#xa0; / \\u00A0) tetap terjaga secara valid di XHTML",
                result.contains("&#xa0;2026!") || result.contains("\u00A02026!")
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `translateEpub repacks valid OCF EPUB with STORED mimetype first and untouched navigation`() = runTest {
        val tempRoot = Files.createTempDirectory("goepub_full_test").toFile()
        try {
            val sampleEpub = File(tempRoot, "sample_book.epub")
            val originalNavXhtml = """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <body>
                    <nav epub:type="toc">
                        <ol><li><a href="ch1.xhtml">The Dawn</a></li></ol>
                    </nav>
                </body>
                </html>
            """.trimIndent()

            val originalTocNcx = """
                <?xml version="1.0" encoding="utf-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                    <navMap>
                        <navPoint id="np1" playOrder="1">
                            <navLabel><text>The Dawn</text></navLabel>
                            <content src="ch1.xhtml"/>
                        </navPoint>
                    </navMap>
                </ncx>
            """.trimIndent()

            buildSyntheticEpub(
                target = sampleEpub,
                entries = linkedMapOf(
                    "META-INF/container.xml" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                            <rootfiles>
                                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                            </rootfiles>
                        </container>
                    """.trimIndent(),
                    "OEBPS/content.opf" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                                <dc:title>Morning Star</dc:title>
                                <dc:creator>Clara Vance</dc:creator>
                                <dc:language>en</dc:language>
                            </metadata>
                            <manifest>
                                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                                <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                            </manifest>
                            <spine toc="ncx">
                                <itemref idref="nav" linear="no"/>
                                <itemref idref="c1"/>
                            </spine>
                        </package>
                    """.trimIndent(),
                    "OEBPS/nav.xhtml" to originalNavXhtml,
                    "OEBPS/toc.ncx" to originalTocNcx,
                    "OEBPS/ch1.xhtml" to """
                        <?xml version="1.0" encoding="utf-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <body><p>Good morning world.</p></body>
                        </html>
                    """.trimIndent()
                )
            )

            val engine = FakeTranslationEngine()
            val outDir = File(tempRoot, "out")
            val processor = EpubProcessor(File(tempRoot, "cache"), outDir, engine)

            val info = processor.inspectEpub(sampleEpub)
            assertEquals("Morning Star", info.title)
            assertEquals("Clara Vance", info.author)
            assertEquals(1, info.chapterCount)

            val reportedChapterTitles = mutableListOf<String>()
            val translatedEpub = processor.translateEpub(
                sourceFile = sampleEpub,
                sourceLanguage = "en",
                targetLanguage = "id"
            ) { stage ->
                if (stage is EpubStage.Translating) {
                    reportedChapterTitles += stage.chapterTitle
                }
            }

            assertTrue(translatedEpub.isFile)
            assertTrue("Judul bab asli dari TOC harus terbaca", reportedChapterTitles.contains("The Dawn"))

            // Verifikasi spesifikasi OCF EPUB pada file zip hasil
            ZipFile(translatedEpub).use { zip ->
                val allEntries = zip.entries().toList()
                val first = allEntries.first()
                assertEquals("mimetype wajib di urutan pertama", "mimetype", first.name)
                assertEquals("mimetype wajib STORED tanpa kompresi", ZipEntry.STORED, first.method)

                // Verifikasi byte offset 30..38 langsung di file biner EPUB
                val rawHeader = translatedEpub.readBytes().sliceArray(30 until 58)
                assertEquals(
                    "mimetypeapplication/epub+zip",
                    String(rawHeader, StandardCharsets.US_ASCII)
                )

                // Verifikasi nav.xhtml & toc.ncx sama sekali TIDAK dirusak/diterjemahkan
                val navAfter = zip.getInputStream(zip.getEntry("OEBPS/nav.xhtml"))
                    .bufferedReader().readText()
                val ncxAfter = zip.getInputStream(zip.getEntry("OEBPS/toc.ncx"))
                    .bufferedReader().readText()
                assertEquals(originalNavXhtml, navAfter)
                assertEquals(originalTocNcx, ncxAfter)

                // Verifikasi isi bab berhasil diterjemahkan
                val ch1After = zip.getInputStream(zip.getEntry("OEBPS/ch1.xhtml"))
                    .bufferedReader().readText()
                assertTrue(ch1After.contains("[ID:Good morning world.]"))
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `inspectEpub detects DRM protected EPUB vs corrupted archive`() = runTest {
        val tempRoot = Files.createTempDirectory("goepub_drm_test").toFile()
        try {
            val processor = EpubProcessor(tempRoot, tempRoot, FakeTranslationEngine())

            // 1. File rusak (bukan ZIP)
            val corruptFile = File(tempRoot, "corrupt.epub").apply {
                writeText("not a valid zip file at all")
            }
            try {
                processor.inspectEpub(corruptFile)
                fail("Seharusnya melempar EpubException untuk file rusak")
            } catch (e: EpubException) {
                assertEquals(EpubErrorKind.CORRUPTED_ARCHIVE, e.kind)
            }

            // 2. File EPUB dengan DRM (encryption.xml mengenkripsi bab XHTML)
            val drmFile = File(tempRoot, "drm_locked.epub")
            buildSyntheticEpub(
                target = drmFile,
                entries = linkedMapOf(
                    "META-INF/container.xml" to """
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                            <rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                        </container>
                    """.trimIndent(),
                    "META-INF/encryption.xml" to """
                        <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                            <EncryptedData xmlns="http://www.w3.org/2001/04/xmlenc#">
                                <EncryptionMethod Algorithm="http://www.w3.org/2001/04/xmlenc#aes128-cbc"/>
                                <CipherData><CipherReference URI="ch1.xhtml"/></CipherData>
                            </EncryptedData>
                        </encryption>
                    """.trimIndent(),
                    "content.opf" to "<package/>"
                )
            )
            try {
                processor.inspectEpub(drmFile)
                fail("Seharusnya mendeteksi proteksi DRM")
            } catch (e: EpubException) {
                assertEquals(EpubErrorKind.DRM_PROTECTED, e.kind)
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `app language defaults to English and toggles cleanly to Indonesian`() {
        assertEquals("English harus menjadi bahasa default saat fresh install", AppLanguage.EN, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.ID, AppLanguage.EN.toggle())
        assertEquals(AppLanguage.EN, AppLanguage.ID.toggle())
        assertEquals("Select EPUB File", AppStrings.forLanguage(AppLanguage.EN).pickEpubButton)
        assertEquals("Pilih File EPUB", AppStrings.forLanguage(AppLanguage.ID).pickEpubButton)
    }

    private fun buildSyntheticEpub(target: File, entries: Map<String, String>) {
        val mimeBytes = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            val crc = CRC32().apply { update(mimeBytes) }.value
            zos.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    this.crc = crc
                }
            )
            zos.write(mimeBytes)
            zos.closeEntry()

            for ((path, content) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
    }
}
