package com.goepub.app.domain.epub

import com.goepub.app.domain.translator.TranslationEngine
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

/**
 * Core EPUB 2 & EPUB 3 processing pipeline:
 * 1. Safely extracts `.epub` archives (Zip-Slip protected) and validates DRM / corruption.
 * 2. Reads OPF metadata, `<spine>` reading order, and chapter titles from `toc.ncx` / `nav.xhtml`
 *    without modifying navigation documents.
 * 3. Parses XHTML chapters with Jsoup XML mode (`prettyPrint(false)` + `EscapeMode.xhtml`),
 *    translating only readable text nodes while keeping HTML tags, CSS, images, and entities intact.
 * 4. Repackages valid OCF 3.3 `.epub` files (`mimetype` stored first via [ZipEntry.STORED]).
 */
class EpubProcessor(
    private val cacheRootDir: File,
    private val outputRootDir: File,
    private val engine: TranslationEngine,
    private val uiLanguageCode: String = "en"
) {

    private val isIndonesian: Boolean
        get() = uiLanguageCode.equals("id", ignoreCase = true)

    suspend fun inspectEpub(sourceFile: File, fallbackTitle: String? = null): EpubBookInfo =
        withContext(Dispatchers.IO) {
            validateSourceArchive(sourceFile)

            ZipFile(sourceFile).use { zip ->
                checkDrmInZip(zip)

                val containerEntry = zip.getEntry("META-INF/container.xml")
                    ?: throw EpubException(
                        if (isIndonesian) {
                            "Struktur EPUB tidak valid: META-INF/container.xml tidak ditemukan."
                        } else {
                            "Invalid EPUB structure: META-INF/container.xml was not found."
                        },
                        EpubErrorKind.INVALID_STRUCTURE
                    )

                val containerXml = zip.getInputStream(containerEntry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                val opfPath = parseContainerXmlText(containerXml)

                val opfEntry = findZipEntryCompat(zip, opfPath)
                    ?: throw EpubException(
                        if (isIndonesian) {
                            "File paket utama EPUB ($opfPath) tidak ditemukan di dalam arsip."
                        } else {
                            "Primary EPUB package file ($opfPath) was not found inside the archive."
                        },
                        EpubErrorKind.INVALID_STRUCTURE
                    )

                val opfXml = zip.getInputStream(opfEntry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                checkOpfEncryptionHint(opfXml)

                val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())
                val opfParentPath = opfPath.substringBeforeLast('/', "")

                val title = opfDoc.selectFirst("metadata > dc|title, dc|title, title")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: sourceFile.nameWithoutExtension

                val author = opfDoc.selectFirst("metadata > dc|creator, dc|creator, creator")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val language = opfDoc.selectFirst("metadata > dc|language, dc|language, language")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val spineItems = parseTranslatableSpineItems(opfDoc, opfParentPath, emptyMap())
                if (spineItems.isEmpty()) {
                    throw EpubException(
                        if (isIndonesian) {
                            "Buku EPUB ini tidak memiliki bab teks yang dapat diterjemahkan."
                        } else {
                            "This EPUB does not contain any translatable XHTML/HTML text chapters."
                        },
                        EpubErrorKind.EMPTY_CONTENT
                    )
                }

                val coverBytes = extractCoverBytesFromZip(zip, opfDoc, opfParentPath)

                EpubBookInfo(
                    title = title,
                    author = author,
                    language = language,
                    chapterCount = spineItems.size,
                    fileSizeBytes = sourceFile.length(),
                    coverImageBytes = coverBytes
                )
            }
        }

    suspend fun translateEpub(
        sourceFile: File,
        sourceLanguage: String?,
        targetLanguage: String,
        allowCellularModelDownload: Boolean = true,
        desiredOutputBaseName: String? = null,
        onStageChange: suspend (EpubStage) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val sessionWorkDir = File(cacheRootDir, "epub_work_${UUID.randomUUID()}").apply {
            mkdirs()
        }

        try {
            onStageChange(EpubStage.Extracting)
            val extractedRoot = extractEpub(sourceFile, sessionWorkDir)
            val epubStructure = readEpubStructure(extractedRoot)

            if (epubStructure.chapters.isEmpty()) {
                throw EpubException(
                    if (isIndonesian) {
                        "EPUB tidak memiliki bab XHTML/HTML yang valid untuk diterjemahkan."
                    } else {
                        "This EPUB has no valid XHTML/HTML chapters to translate."
                    },
                    EpubErrorKind.EMPTY_CONTENT
                )
            }

            val resolvedSourceLanguage = if (!sourceLanguage.isNullOrBlank()) {
                sourceLanguage
            } else {
                onStageChange(EpubStage.DetectingLanguage)
                val sampleText = extractSampleText(extractedRoot, epubStructure.chapters)
                engine.detectLanguage(sampleText)
                    ?: epubStructure.declaredLanguage
                    ?: "en"
            }

            val modelsAlreadyReady = engine.areModelsReady(resolvedSourceLanguage, targetLanguage)
            if (!modelsAlreadyReady) {
                onStageChange(
                    EpubStage.DownloadingModel(
                        sourceLanguage = resolvedSourceLanguage,
                        targetLanguage = targetLanguage
                    )
                )
            }
            engine.ensureModel(
                sourceLanguage = resolvedSourceLanguage,
                targetLanguage = targetLanguage,
                allowCellular = allowCellularModelDownload
            )

            val chapters = epubStructure.chapters
            val total = chapters.size

            chapters.forEachIndexed { zeroIndex, chapter ->
                ensureActive()
                val currentNumber = zeroIndex + 1
                val startPercent = ((zeroIndex * 100f) / total).toInt().coerceIn(0, 99)

                onStageChange(
                    EpubStage.Translating(
                        chapterIndex = currentNumber,
                        totalChapters = total,
                        chapterTitle = chapter.displayTitle,
                        percent = startPercent
                    )
                )

                val xhtmlFile = resolveChapterFile(extractedRoot, chapter.relativePath)
                if (xhtmlFile != null && xhtmlFile.isFile) {
                    translateXhtmlFile(
                        file = xhtmlFile,
                        sourceLanguage = resolvedSourceLanguage,
                        targetLanguage = targetLanguage
                    )
                }

                val donePercent = ((currentNumber * 100f) / total).toInt().coerceIn(1, 99)
                onStageChange(
                    EpubStage.Translating(
                        chapterIndex = currentNumber,
                        totalChapters = total,
                        chapterTitle = chapter.displayTitle,
                        percent = donePercent
                    )
                )
            }

            updateOpfLanguageMetadata(epubStructure.opfFile, targetLanguage)

            onStageChange(EpubStage.Repackaging)
            outputRootDir.mkdirs()

            val cleanBase = sanitizeFileName(
                desiredOutputBaseName?.takeIf { it.isNotBlank() }
                    ?: epubStructure.bookTitle.takeIf { it.isNotBlank() }
                    ?: sourceFile.nameWithoutExtension.ifBlank { "ebook" }
            )
            val outputFile = File(outputRootDir, "${cleanBase}_${targetLanguage.lowercase()}.epub")
            repackEpub(extractedRoot, outputFile)

            outputFile
        } finally {
            sessionWorkDir.deleteRecursively()
        }
    }

    private fun validateSourceArchive(sourceFile: File) {
        if (!sourceFile.isFile || sourceFile.length() == 0L) {
            throw EpubException(
                if (isIndonesian) "File EPUB tidak ditemukan atau kosong."
                else "EPUB file was not found or is empty.",
                EpubErrorKind.CORRUPTED_ARCHIVE
            )
        }
        try {
            ZipFile(sourceFile).use { zip ->
                if (!zip.entries().hasMoreElements()) {
                    throw EpubException(
                        if (isIndonesian) "Arsip EPUB kosong dan tidak dapat dibuka."
                        else "The EPUB archive is empty and cannot be opened.",
                        EpubErrorKind.CORRUPTED_ARCHIVE
                    )
                }
            }
        } catch (e: EpubException) {
            throw e
        } catch (e: ZipException) {
            throw EpubException(
                if (isIndonesian) {
                    "File yang dipilih rusak atau bukan format EPUB (.epub) yang valid."
                } else {
                    "The selected file is corrupted or is not a valid .epub archive."
                },
                EpubErrorKind.CORRUPTED_ARCHIVE,
                e
            )
        } catch (e: Exception) {
            throw EpubException(
                if (isIndonesian) "Gagal membuka arsip EPUB: ${e.message ?: "format tidak dikenali"}."
                else "Failed to open EPUB archive: ${e.message ?: "unrecognized format"}.",
                EpubErrorKind.CORRUPTED_ARCHIVE,
                e
            )
        }
    }

    internal fun extractEpub(sourceFile: File, targetDir: File): File {
        validateSourceArchive(sourceFile)

        val root = File(targetDir, "extracted").apply {
            deleteRecursively()
            mkdirs()
        }
        val canonicalRootPrefix = root.canonicalPath + File.separator

        try {
            ZipFile(sourceFile).use { zip ->
                checkDrmInZip(zip)

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val outFile = File(root, entry.name)
                    val canonicalOut = outFile.canonicalPath

                    if (canonicalOut != root.canonicalPath && !canonicalOut.startsWith(canonicalRootPrefix)) {
                        throw EpubException(
                            if (isIndonesian) "Arsip EPUB mengandung jalur file tidak aman: ${entry.name}"
                            else "EPUB archive contains an unsafe file path (Zip-Slip): ${entry.name}",
                            EpubErrorKind.INVALID_STRUCTURE
                        )
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: EpubException) {
            throw e
        } catch (e: ZipException) {
            throw EpubException(
                if (isIndonesian) {
                    "Arsip EPUB rusak saat diekstrak. Pastikan file buku sudah terunduh sempurna."
                } else {
                    "The EPUB archive failed to extract cleanly. Please check that the file is complete."
                },
                EpubErrorKind.CORRUPTED_ARCHIVE,
                e
            )
        }

        if (!File(root, "META-INF/container.xml").isFile) {
            throw EpubException(
                if (isIndonesian) "File EPUB tidak valid: META-INF/container.xml tidak ditemukan."
                else "Invalid EPUB file: META-INF/container.xml is missing.",
                EpubErrorKind.INVALID_STRUCTURE
            )
        }

        return root
    }

    private fun checkDrmInZip(zip: ZipFile) {
        if (zip.getEntry("META-INF/rights.xml") != null || zip.getEntry("META-INF/license.lcpl") != null) {
            throw EpubException(
                if (isIndonesian) {
                    "Buku EPUB ini dilindungi DRM. Gunakan file EPUB bebas DRM agar dapat diterjemahkan."
                } else {
                    "This EPUB e-book is protected by DRM. Please use a DRM-free EPUB file."
                },
                EpubErrorKind.DRM_PROTECTED
            )
        }

        val encryptionEntry = zip.getEntry("META-INF/encryption.xml") ?: return
        val encXml = zip.getInputStream(encryptionEntry)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }

        val doc = Jsoup.parse(encXml, "", Parser.xmlParser())
        val encryptedBlocks = doc.select("EncryptedData")
        if (encryptedBlocks.isEmpty()) return

        for (block in encryptedBlocks) {
            val algorithm = block.selectFirst("EncryptionMethod")?.attr("Algorithm").orEmpty()
            val cipherUri = block.selectFirst("CipherReference")?.attr("URI").orEmpty().lowercase()

            val isFontObfuscation = algorithm in FONT_OBFUSCATION_ALGORITHMS ||
                cipherUri.endsWith(".otf") ||
                cipherUri.endsWith(".ttf") ||
                cipherUri.endsWith(".woff") ||
                cipherUri.endsWith(".woff2")

            if (!isFontObfuscation) {
                throw EpubException(
                    if (isIndonesian) {
                        "Isi bab pada EPUB ini terenkripsi DRM dan tidak dapat dibaca. Gunakan versi bebas DRM."
                    } else {
                        "Chapters inside this EPUB are encrypted with DRM and cannot be translated. Please use a DRM-free edition."
                    },
                    EpubErrorKind.DRM_PROTECTED
                )
            }
        }
    }

    private fun checkOpfEncryptionHint(opfText: String) {
        if (opfText.contains("EncryptedData", ignoreCase = true) &&
            opfText.contains("CipherData", ignoreCase = true)
        ) {
            throw EpubException(
                if (isIndonesian) "Metadata OPF menunjukkan buku ini dilindungi DRM."
                else "OPF metadata indicates this book is DRM-protected.",
                EpubErrorKind.DRM_PROTECTED
            )
        }
    }

    private data class EpubStructure(
        val opfFile: File,
        val bookTitle: String,
        val declaredLanguage: String?,
        val chapters: List<SpineChapter>
    )

    private data class SpineChapter(
        val relativePath: String,
        val displayTitle: String
    )

    private fun readEpubStructure(root: File): EpubStructure {
        val containerFile = File(root, "META-INF/container.xml")
        val opfRelPath = parseContainerXmlText(containerFile.readText(StandardCharsets.UTF_8))

        val opfFile = resolveChapterFile(root, opfRelPath)
            ?: throw EpubException(
                if (isIndonesian) "File paket OPF ($opfRelPath) tidak ditemukan di dalam EPUB."
                else "OPF package file ($opfRelPath) was not found inside the EPUB.",
                EpubErrorKind.INVALID_STRUCTURE
            )

        val opfXml = opfFile.readText(StandardCharsets.UTF_8)
        checkOpfEncryptionHint(opfXml)

        val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())
        val opfParentRel = opfFile.parentFile
            ?.relativeTo(root)
            ?.path
            ?.replace(File.separatorChar, '/')
            ?.takeIf { it != "." }
            .orEmpty()

        val bookTitle = opfDoc.selectFirst("metadata > dc|title, dc|title, title")
            ?.text()
            ?.trim()
            .orEmpty()

        val declaredLanguage = opfDoc.selectFirst("metadata > dc|language, dc|language, language")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val tocTitlesByPath = readNavigationTitles(root, opfDoc, opfParentRel)
        val chapters = parseTranslatableSpineItems(opfDoc, opfParentRel, tocTitlesByPath)

        return EpubStructure(
            opfFile = opfFile,
            bookTitle = bookTitle,
            declaredLanguage = declaredLanguage,
            chapters = chapters
        )
    }

    private fun parseContainerXmlText(containerXml: String): String {
        val doc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val rootfile = doc.selectFirst("rootfile")
            ?: throw EpubException(
                if (isIndonesian) "Elemen <rootfile> tidak ditemukan di META-INF/container.xml."
                else "<rootfile> element is missing in META-INF/container.xml.",
                EpubErrorKind.INVALID_STRUCTURE
            )
        val fullPath = rootfile.attr("full-path").trim()
        if (fullPath.isEmpty()) {
            throw EpubException(
                if (isIndonesian) "Atribut full-path kosong di META-INF/container.xml."
                else "Empty full-path attribute in META-INF/container.xml.",
                EpubErrorKind.INVALID_STRUCTURE
            )
        }
        return fullPath
    }

    private fun parseTranslatableSpineItems(
        opfDoc: Document,
        opfParentRel: String,
        tocTitlesByPath: Map<String, String>
    ): List<SpineChapter> {
        val spineEl = opfDoc.selectFirst("spine")
            ?: throw EpubException(
                if (isIndonesian) "Elemen <spine> tidak ditemukan pada file OPF."
                else "<spine> element is missing in the OPF package file.",
                EpubErrorKind.INVALID_STRUCTURE
            )

        val manifestById = opfDoc.select("manifest > item, item")
            .filter { it.hasAttr("id") }
            .associateBy { it.attr("id") }

        val chapters = mutableListOf<SpineChapter>()
        var chapterCounter = 0

        for (itemref in spineEl.select("itemref")) {
            val idref = itemref.attr("idref").trim()
            if (idref.isEmpty()) continue

            val item = manifestById[idref] ?: continue
            val rawHref = item.attr("href").trim().substringBefore('#')
            if (rawHref.isEmpty()) continue

            val properties = item.attr("properties")
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .toSet()

            val mediaType = item.attr("media-type").lowercase().trim()

            // Never touch navigation metadata documents (nav.xhtml / toc.ncx)
            if ("nav" in properties) continue
            if (mediaType == "application/x-dtbncx+xml") continue
            val lowerHref = rawHref.lowercase()
            if (lowerHref.endsWith(".ncx") || lowerHref.substringAfterLast('/') == "nav.xhtml") continue

            val isHtmlDocument = mediaType in HTML_MEDIA_TYPES ||
                lowerHref.endsWith(".xhtml") ||
                lowerHref.endsWith(".html") ||
                lowerHref.endsWith(".htm")

            if (!isHtmlDocument) continue

            val normalizedPath = normalizeRelativeZipPath(opfParentRel, rawHref)
            val decodedPath = decodeUrlPath(normalizedPath)

            chapterCounter++
            val tocTitle = tocTitlesByPath[decodedPath]
                ?: tocTitlesByPath[normalizedPath]
                ?: prettifyChapterFileName(decodedPath, chapterCounter)

            chapters += SpineChapter(
                relativePath = normalizedPath,
                displayTitle = tocTitle
            )
        }

        return chapters.distinctBy { decodeUrlPath(it.relativePath) }
    }

    private fun readNavigationTitles(
        root: File,
        opfDoc: Document,
        opfParentRel: String
    ): Map<String, String> {
        val titles = mutableMapOf<String, String>()

        val navItem = opfDoc.select("manifest > item, item").firstOrNull { item ->
            item.attr("properties").lowercase().split(Regex("\\s+")).contains("nav")
        }
        if (navItem != null) {
            val navRel = normalizeRelativeZipPath(opfParentRel, navItem.attr("href").substringBefore('#'))
            val navFile = resolveChapterFile(root, navRel)
            if (navFile != null && navFile.isFile) {
                runCatching {
                    val navDoc = Jsoup.parse(navFile.readText(StandardCharsets.UTF_8), "", Parser.xmlParser())
                    val navParent = navFile.parentFile?.relativeTo(root)?.path
                        ?.replace(File.separatorChar, '/')
                        ?.takeIf { it != "." }
                        .orEmpty()
                    for (link in navDoc.select("nav a[href], a[href]")) {
                        val href = link.attr("href").substringBefore('#').trim()
                        val label = link.text().trim()
                        if (href.isNotEmpty() && label.isNotEmpty()) {
                            val targetPath = decodeUrlPath(normalizeRelativeZipPath(navParent, href))
                            titles.putIfAbsent(targetPath, label)
                        }
                    }
                }
            }
        }

        val ncxItem = opfDoc.select("manifest > item, item").firstOrNull { item ->
            item.attr("media-type").equals("application/x-dtbncx+xml", ignoreCase = true) ||
                item.attr("href").lowercase().endsWith(".ncx")
        }
        if (ncxItem != null) {
            val ncxRel = normalizeRelativeZipPath(opfParentRel, ncxItem.attr("href").substringBefore('#'))
            val ncxFile = resolveChapterFile(root, ncxRel)
            if (ncxFile != null && ncxFile.isFile) {
                runCatching {
                    val ncxDoc = Jsoup.parse(ncxFile.readText(StandardCharsets.UTF_8), "", Parser.xmlParser())
                    val ncxParent = ncxFile.parentFile?.relativeTo(root)?.path
                        ?.replace(File.separatorChar, '/')
                        ?.takeIf { it != "." }
                        .orEmpty()
                    for (navPoint in ncxDoc.select("navPoint")) {
                        val label = navPoint.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                        val src = navPoint.selectFirst("content")?.attr("src")?.substringBefore('#')?.trim().orEmpty()
                        if (label.isNotEmpty() && src.isNotEmpty()) {
                            val targetPath = decodeUrlPath(normalizeRelativeZipPath(ncxParent, src))
                            titles.putIfAbsent(targetPath, label)
                        }
                    }
                }
            }
        }

        return titles
    }

    internal suspend fun translateXhtmlFile(
        file: File,
        sourceLanguage: String?,
        targetLanguage: String
    ) {
        val rawContent = file.readText(StandardCharsets.UTF_8)
        val translatedContent = translateXhtmlContent(rawContent, sourceLanguage, targetLanguage)
        file.writeText(translatedContent, StandardCharsets.UTF_8)
    }

    internal suspend fun translateXhtmlContent(
        rawXhtml: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String {
        val doc: Document = Jsoup.parse(rawXhtml, "", Parser.xmlParser())

        doc.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .escapeMode(Entities.EscapeMode.xhtml)
            .charset(StandardCharsets.UTF_8)
            .prettyPrint(false)

        for (element in doc.allElements) {
            if (shouldSkipElement(element)) continue

            val directTextNodes = element.childNodes().filterIsInstance<TextNode>()
            for (textNode in directTextNodes) {
                val segment = TextChunker.extractSegment(textNode.wholeText) ?: continue
                val translatedCore = translateWithChunking(
                    text = segment.coreText,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage
                )
                textNode.text(segment.reassemble(translatedCore))
            }
        }

        return doc.outerHtml()
    }

    private fun shouldSkipElement(element: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            val tag = current.tagName().lowercase().substringAfterLast(':')
            if (tag in SKIP_TAGS) return true
            if (current.attr("translate").equals("no", ignoreCase = true)) return true
            current = current.parent()
        }
        return false
    }

    private suspend fun translateWithChunking(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String {
        val chunks = TextChunker.chunkText(text)
        if (chunks.isEmpty()) return text
        if (chunks.size == 1) {
            return engine.translate(chunks[0], sourceLanguage, targetLanguage)
        }
        return chunks.map { chunk ->
            engine.translate(chunk, sourceLanguage, targetLanguage)
        }.joinToString(" ")
    }

    private fun extractSampleText(root: File, chapters: List<SpineChapter>): String {
        val builder = StringBuilder()
        for (chapter in chapters.take(4)) {
            val file = resolveChapterFile(root, chapter.relativePath) ?: continue
            if (!file.isFile) continue
            val doc = Jsoup.parse(file.readText(StandardCharsets.UTF_8), "", Parser.xmlParser())
            val bodyText = doc.selectFirst("body")?.text()?.trim().orEmpty()
            if (bodyText.isNotEmpty()) {
                builder.append(bodyText).append(' ')
            }
            if (builder.length >= 600) break
        }
        return builder.toString().take(800)
    }

    private fun updateOpfLanguageMetadata(opfFile: File, targetLanguage: String) {
        runCatching {
            val rawOpf = opfFile.readText(StandardCharsets.UTF_8)
            val opfDoc = Jsoup.parse(rawOpf, "", Parser.xmlParser())
            opfDoc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset(StandardCharsets.UTF_8)
                .prettyPrint(false)

            val langEl = opfDoc.selectFirst("metadata > dc|language, dc|language")
            if (langEl != null) {
                langEl.text(targetLanguage.lowercase())
                opfFile.writeText(opfDoc.outerHtml(), StandardCharsets.UTF_8)
            }
        }
    }

    internal fun repackEpub(sourceRoot: File, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val mimetypeFile = File(sourceRoot, "mimetype")
        val mimetypeBytes = if (mimetypeFile.isFile) {
            mimetypeFile.readText(StandardCharsets.US_ASCII).trim().ifEmpty { EPUB_MIMETYPE }
                .toByteArray(StandardCharsets.US_ASCII)
        } else {
            EPUB_MIMETYPE.toByteArray(StandardCharsets.US_ASCII)
        }

        ZipOutputStream(outputFile.outputStream().buffered()).use { zos ->
            val crc = CRC32().apply { update(mimetypeBytes) }.value
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                this.crc = crc
                extra = null
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimetypeBytes)
            zos.closeEntry()

            val otherFiles = sourceRoot.walkTopDown()
                .filter { it.isFile && it.relativeTo(sourceRoot).path.replace(File.separatorChar, '/') != "mimetype" }
                .sortedWith(
                    compareBy<File> {
                        val rel = it.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
                        if (rel == "META-INF/container.xml") 0 else 1
                    }.thenBy {
                        it.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
                    }
                )

            for (file in otherFiles) {
                val entryName = file.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
                val entry = ZipEntry(entryName).apply {
                    method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(entry)
                file.inputStream().buffered().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun extractCoverBytesFromZip(
        zip: ZipFile,
        opfDoc: Document,
        opfParentPath: String
    ): ByteArray? {
        val manifestItems = opfDoc.select("manifest > item, item")

        var coverItem = manifestItems.firstOrNull { item ->
            item.attr("properties").lowercase().split(Regex("\\s+")).contains("cover-image")
        }

        if (coverItem == null) {
            val coverId = opfDoc.select("metadata > meta, meta")
                .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
                ?.attr("content")
                ?.trim()
            if (!coverId.isNullOrEmpty()) {
                coverItem = manifestItems.firstOrNull { it.attr("id") == coverId }
            }
        }

        if (coverItem == null) {
            coverItem = manifestItems.firstOrNull { item ->
                val media = item.attr("media-type").lowercase()
                val id = item.attr("id").lowercase()
                val href = item.attr("href").lowercase()
                media.startsWith("image/") && (id.contains("cover") || href.contains("cover"))
            }
        }

        val href = coverItem?.attr("href")?.substringBefore('#')?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null

        val coverPath = normalizeRelativeZipPath(opfParentPath, href)
        val coverEntry = findZipEntryCompat(zip, coverPath) ?: return null

        if (coverEntry.size > 4L * 1024L * 1024L) return null

        return runCatching {
            zip.getInputStream(coverEntry).use { it.readBytes() }
        }.getOrNull()
    }

    private fun findZipEntryCompat(zip: ZipFile, rawPath: String): ZipEntry? {
        zip.getEntry(rawPath)?.let { return it }
        val decoded = decodeUrlPath(rawPath)
        zip.getEntry(decoded)?.let { return it }
        return null
    }

    private fun resolveChapterFile(root: File, relativePath: String): File? {
        val direct = File(root, relativePath)
        if (direct.isFile) return direct
        val decoded = File(root, decodeUrlPath(relativePath))
        if (decoded.isFile) return decoded
        return null
    }

    private fun normalizeRelativeZipPath(parentDir: String, href: String): String {
        val combined = if (parentDir.isBlank()) href else "$parentDir/$href"
        val segments = mutableListOf<String>()
        for (part in combined.replace('\\', '/').split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += part
            }
        }
        return segments.joinToString("/")
    }

    private fun decodeUrlPath(path: String): String {
        return runCatching {
            URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(path)
    }

    private fun prettifyChapterFileName(relativePath: String, fallbackIndex: Int): String {
        val base = relativePath.substringAfterLast('/')
            .replace(Regex("\\.(x?html?|xml)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\-]+"), " ")
            .trim()
        return if (base.isBlank() || base.matches(Regex("^(part|split|sec|ch)?\\d+$", RegexOption.IGNORE_CASE))) {
            if (isIndonesian) "Bab $fallbackIndex" else "Chapter $fallbackIndex"
        } else {
            base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(80)
            .ifBlank { "ebook" }
    }

    companion object {
        const val EPUB_MIMETYPE = "application/epub+zip"

        private val HTML_MEDIA_TYPES = setOf(
            "application/xhtml+xml",
            "text/html",
            "application/x-dtbook+xml"
        )

        private val FONT_OBFUSCATION_ALGORITHMS = setOf(
            "http://www.idpf.org/2008/embedding",
            "http://ns.adobe.com/pdf/enc#RC"
        )

        private val SKIP_TAGS = setOf(
            "head",
            "script",
            "style",
            "pre",
            "code",
            "kbd",
            "samp",
            "svg",
            "math",
            "meta",
            "link"
        )
    }
}
