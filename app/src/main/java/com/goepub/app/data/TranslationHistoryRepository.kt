package com.goepub.app.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data riwayat buku EPUB hasil terjemahan yang tersimpan secara lokal di perangkat.
 */
data class TranslatedBookRecord(
    val id: String,
    val title: String,
    val author: String?,
    val sourceLanguage: String,
    val targetLanguage: String,
    val filePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val chapterCount: Int,
    val completedAtMillis: Long
)

/**
 * Repositori lokal ringan untuk menyimpan daftar EPUB hasil terjemahan di `filesDir/translated`
 * sehingga file hasil tidak hilang saat sistem Android membersihkan cache sementara.
 */
class TranslationHistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()

    val outputDirectory: File
        get() = File(appContext.filesDir, "translated").apply { mkdirs() }

    private val metadataFile: File
        get() = File(outputDirectory, "history_v1.json")

    suspend fun getHistory(): List<TranslatedBookRecord> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readRecordsInternal()
                .filter { File(it.filePath).isFile }
                .sortedByDescending { it.completedAtMillis }
        }
    }

    suspend fun saveRecord(record: TranslatedBookRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = readRecordsInternal()
                .filter { it.id != record.id && it.filePath != record.filePath && File(it.filePath).isFile }
                .toMutableList()

            existing.add(0, record)

            // Batasi maksimal 20 buku riwayat agar penyimpanan tetap ramping
            if (existing.size > MAX_HISTORY_ITEMS) {
                val overflow = existing.subList(MAX_HISTORY_ITEMS, existing.size)
                overflow.forEach { old -> runCatching { File(old.filePath).delete() } }
            }

            val trimmed = existing.take(MAX_HISTORY_ITEMS)
            writeRecordsInternal(trimmed)
        }
    }

    suspend fun deleteRecord(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readRecordsInternal()
            val target = current.firstOrNull { it.id == id }
            if (target != null) {
                runCatching { File(target.filePath).delete() }
            }
            writeRecordsInternal(current.filterNot { it.id == id })
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        mutex.withLock {
            outputDirectory.listFiles()?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
        }
    }

    private fun readRecordsInternal(): List<TranslatedBookRecord> {
        if (!metadataFile.isFile) return emptyList()
        return runCatching {
            val raw = metadataFile.readText(Charsets.UTF_8)
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        TranslatedBookRecord(
                            id = obj.optString("id"),
                            title = obj.optString("title", "Buku Terjemahan"),
                            author = obj.optString("author").takeIf { it.isNotBlank() },
                            sourceLanguage = obj.optString("sourceLanguage", "en"),
                            targetLanguage = obj.optString("targetLanguage", "id"),
                            filePath = obj.optString("filePath"),
                            fileName = obj.optString("fileName"),
                            fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                            chapterCount = obj.optInt("chapterCount", 0),
                            completedAtMillis = obj.optLong("completedAtMillis", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecordsInternal(records: List<TranslatedBookRecord>) {
        val array = JSONArray()
        for (item in records) {
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("author", item.author.orEmpty())
                    put("sourceLanguage", item.sourceLanguage)
                    put("targetLanguage", item.targetLanguage)
                    put("filePath", item.filePath)
                    put("fileName", item.fileName)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("chapterCount", item.chapterCount)
                    put("completedAtMillis", item.completedAtMillis)
                }
            )
        }
        outputDirectory.mkdirs()
        metadataFile.writeText(array.toString(), Charsets.UTF_8)
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 20
    }
}
