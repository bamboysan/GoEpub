package com.goepub.app.domain.translator

import com.goepub.app.domain.epub.TextChunker
import com.goepub.app.ui.i18n.AppLanguage
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Primary [TranslationEngine] implementation powered by **Google ML Kit On-Device Translation**.
 *
 * - 100% free, runs locally on-device (books stay private, zero API rate limits).
 * - Supports automatic source language detection via ML Kit Language Identification.
 * - Wraps Google Play Services Tasks with [suspendCancellableCoroutine] for clean Coroutine cancellation.
 * - Provides offline language pack management (check, download, delete ~30 MB models).
 */
class GoogleTranslateManager : TranslationEngine {

    private val translators = ConcurrentHashMap<String, Translator>()

    private val segmentCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > MAX_SEGMENT_CACHE_ENTRIES
            }
        }
    )

    @Volatile
    private var cachedDetectedSource: String? = null

    fun chunkText(text: String, maxChunkSize: Int = TextChunker.DEFAULT_MAX_CHUNK_SIZE): List<String> {
        return TextChunker.chunkText(text, maxChunkSize)
    }

    override suspend fun areModelsReady(sourceLanguage: String, targetLanguage: String): Boolean {
        val src = normalizeLanguageCode(sourceLanguage)
        val tgt = normalizeLanguageCode(targetLanguage)
        if (src == tgt) return true
        return isLanguageModelDownloaded(src) && isLanguageModelDownloaded(tgt)
    }

    override suspend fun ensureModel(
        sourceLanguage: String,
        targetLanguage: String,
        allowCellular: Boolean
    ) {
        val src = normalizeLanguageCode(sourceLanguage)
        val tgt = normalizeLanguageCode(targetLanguage)
        if (src == tgt) return

        val conditionsBuilder = DownloadConditions.Builder()
        if (!allowCellular) {
            conditionsBuilder.requireWifi()
        }
        val conditions = conditionsBuilder.build()

        val translator = getOrCreateTranslator(src, tgt)
        translator.downloadModelIfNeeded(conditions).awaitCancellable()
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String {
        if (text.isBlank()) return text

        val tgt = normalizeLanguageCode(targetLanguage)
        val rawSource = sourceLanguage
            ?: cachedDetectedSource
            ?: detectLanguage(text)?.also { cachedDetectedSource = it }
            ?: TranslateLanguage.ENGLISH

        val src = normalizeLanguageCode(rawSource)
        if (src == tgt) return text

        val cacheKey = "$src>$tgt|$text"
        segmentCache[cacheKey]?.let { return it }

        val translator = getOrCreateTranslator(src, tgt)
        val chunks = chunkText(text)
        val result = if (chunks.size <= 1) {
            translator.translate(text).awaitCancellable()
        } else {
            chunks.map { chunk ->
                translator.translate(chunk).awaitCancellable()
            }.joinToString(" ")
        }

        if (text.length <= 300) {
            segmentCache[cacheKey] = result
        }
        return result
    }

    override suspend fun detectLanguage(text: String): String? {
        val cleanSample = text.trim().take(800)
        if (cleanSample.isBlank()) return null

        val identifier = LanguageIdentification.getClient()
        return try {
            val tag = identifier.identifyLanguage(cleanSample).awaitCancellable()
            if (tag.isNullOrBlank() || tag == LanguageIdentifier.UNDETERMINED_LANGUAGE_TAG) {
                null
            } else {
                TranslateLanguage.fromLanguageTag(tag)
                    ?: TranslateLanguage.fromLanguageTag(tag.substringBefore('-'))
            }
        } catch (_: Exception) {
            null
        } finally {
            identifier.close()
        }
    }

    override fun close() {
        translators.values.forEach { translator ->
            runCatching { translator.close() }
        }
        translators.clear()
        segmentCache.clear()
    }

    private fun getOrCreateTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "$sourceLang->$targetLang"
        return translators.computeIfAbsent(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    companion object {
        private const val MAX_SEGMENT_CACHE_ENTRIES = 1500

        data class LanguageOption(
            val code: String,
            val englishName: String,
            val indonesianName: String,
            val nativeName: String,
            val badge: String
        ) {
            fun nameFor(appLanguage: AppLanguage): String {
                return if (appLanguage == AppLanguage.ID) indonesianName else englishName
            }
        }

        /** Supported languages for ML Kit On-Device Translation with bilingual labels. */
        val LANGUAGE_OPTIONS: List<LanguageOption> = listOf(
            LanguageOption("en", "English", "Bahasa Inggris", "English", "EN"),
            LanguageOption("id", "Indonesian", "Bahasa Indonesia", "Indonesia", "ID"),
            LanguageOption("ms", "Malay", "Bahasa Melayu", "Melayu", "MS"),
            LanguageOption("ja", "Japanese", "Bahasa Jepang", "日本語", "JA"),
            LanguageOption("ko", "Korean", "Bahasa Korea", "한국어", "KO"),
            LanguageOption("zh", "Chinese", "Bahasa Mandarin", "中文", "ZH"),
            LanguageOption("ar", "Arabic", "Bahasa Arab", "العربية", "AR"),
            LanguageOption("fr", "French", "Bahasa Prancis", "Français", "FR"),
            LanguageOption("de", "German", "Bahasa Jerman", "Deutsch", "DE"),
            LanguageOption("es", "Spanish", "Bahasa Spanyol", "Español", "ES"),
            LanguageOption("pt", "Portuguese", "Bahasa Portugis", "Português", "PT"),
            LanguageOption("nl", "Dutch", "Bahasa Belanda", "Nederlands", "NL"),
            LanguageOption("it", "Italian", "Bahasa Italia", "Italiano", "IT"),
            LanguageOption("ru", "Russian", "Bahasa Rusia", "Русский", "RU"),
            LanguageOption("tr", "Turkish", "Bahasa Turki", "Türkçe", "TR"),
            LanguageOption("hi", "Hindi", "Bahasa Hindi", "हिन्दी", "HI"),
            LanguageOption("th", "Thai", "Bahasa Thai", "ไทย", "TH"),
            LanguageOption("vi", "Vietnamese", "Bahasa Vietnam", "Tiếng Việt", "VI"),
            LanguageOption("pl", "Polish", "Bahasa Polandia", "Polski", "PL"),
            LanguageOption("sv", "Swedish", "Bahasa Swedia", "Svenska", "SV")
        )

        val SUPPORTED_LANGUAGES: Map<String, String> =
            LANGUAGE_OPTIONS.associate { it.code to "${it.englishName} (${it.nativeName})" }

        fun displayNameFor(code: String?, appLanguage: AppLanguage = AppLanguage.EN): String {
            if (code == null) {
                return if (appLanguage == AppLanguage.ID) "Deteksi Otomatis" else "Auto-Detect"
            }
            val normalized = normalizeLanguageCode(code)
            return LANGUAGE_OPTIONS.firstOrNull { it.code == normalized }?.nameFor(appLanguage)
                ?: normalized.uppercase()
        }

        fun normalizeLanguageCode(rawCode: String): String {
            val trimmed = rawCode.trim().lowercase()
            val candidate = when {
                trimmed == "in" || trimmed.startsWith("in-") || trimmed.startsWith("id-") -> "id"
                trimmed.contains('-') -> trimmed.substringBefore('-')
                trimmed.contains('_') -> trimmed.substringBefore('_')
                else -> trimmed
            }
            return TranslateLanguage.fromLanguageTag(candidate) ?: candidate
        }

        suspend fun isLanguageModelDownloaded(languageCode: String): Boolean {
            val normalized = normalizeLanguageCode(languageCode)
            val model = TranslateRemoteModel.Builder(normalized).build()
            return runCatching {
                RemoteModelManager.getInstance().isModelDownloaded(model).awaitCancellable()
            }.getOrDefault(false)
        }

        suspend fun getDownloadedModelCodes(): Set<String> {
            return runCatching {
                RemoteModelManager.getInstance()
                    .getDownloadedModels(TranslateRemoteModel::class.java)
                    .awaitCancellable()
                    .map { it.language }
                    .toSet()
            }.getOrDefault(emptySet())
        }

        suspend fun downloadSingleModel(languageCode: String, allowCellular: Boolean = true) {
            val normalized = normalizeLanguageCode(languageCode)
            val model = TranslateRemoteModel.Builder(normalized).build()
            val conditions = DownloadConditions.Builder().apply {
                if (!allowCellular) requireWifi()
            }.build()
            RemoteModelManager.getInstance().download(model, conditions).awaitCancellable()
        }

        suspend fun deleteSingleModel(languageCode: String) {
            val normalized = normalizeLanguageCode(languageCode)
            val model = TranslateRemoteModel.Builder(normalized).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).awaitCancellable()
        }

        suspend fun <T> Task<T>.awaitCancellable(): T =
            suspendCancellableCoroutine { continuation ->
                addOnCompleteListener { completedTask ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    when {
                        completedTask.isCanceled -> continuation.cancel()
                        completedTask.isSuccessful -> continuation.resume(completedTask.result)
                        else -> {
                            val cause = completedTask.exception
                                ?: IllegalStateException("ML Kit operation failed.")
                            continuation.resumeWithException(cause)
                        }
                    }
                }
            }
    }
}
