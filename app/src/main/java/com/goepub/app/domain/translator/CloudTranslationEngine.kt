package com.goepub.app.domain.translator

import com.goepub.app.domain.epub.TextChunker
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementasi alternatif [TranslationEngine] untuk **Google Cloud Translation API v2**.
 *
 * Disediakan secara modular agar aplikasi dapat beralih ke Cloud API di masa mendatang
 * (misalnya untuk paket langganan cloud atau bahasa yang belum tersedia di ML Kit On-Device)
 * cukup dengan mengganti injeksi [TranslationEngine] tanpa menyentuh kode EPUB atau UI.
 */
class CloudTranslationEngine(
    private val apiKey: String
) : TranslationEngine {

    override suspend fun areModelsReady(sourceLanguage: String, targetLanguage: String): Boolean = true

    override suspend fun ensureModel(
        sourceLanguage: String,
        targetLanguage: String,
        allowCellular: Boolean
    ) {
        require(apiKey.isNotBlank()) { "API Key Google Cloud Translation belum dikonfigurasi." }
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext text

        val chunks = TextChunker.chunkText(text)
        chunks.map { chunk -> requestTranslate(chunk, sourceLanguage, targetLanguage) }
            .joinToString(" ")
    }

    override suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || apiKey.isBlank()) return@withContext null
        val endpoint = URL("https://translation.googleapis.com/language/translate/v2/detect?key=$apiKey")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            val payload = JSONObject().put("q", text.take(500)).toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            if (conn.responseCode !in 200..299) return@withContext null

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val detections = JSONObject(body)
                .getJSONObject("data")
                .getJSONArray("detections")
                .getJSONArray(0)
            if (detections.length() == 0) null
            else detections.getJSONObject(0).optString("language").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun requestTranslate(
        chunk: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String {
        val endpoint = URL("https://translation.googleapis.com/language/translate/v2?key=$apiKey")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            val payload = JSONObject().apply {
                put("q", JSONArray().put(chunk))
                put("target", targetLanguage)
                put("format", "text")
                if (!sourceLanguage.isNullOrBlank()) {
                    put("source", sourceLanguage)
                }
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Cloud Translation gagal (HTTP ${conn.responseCode}): $err")
            }

            val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(responseText)
                .getJSONObject("data")
                .getJSONArray("translations")
                .getJSONObject(0)
                .getString("translatedText")
        } finally {
            conn.disconnect()
        }
    }
}
