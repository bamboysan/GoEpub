package com.goepub.app.domain.translator

import java.io.Closeable

/**
 * Kontrak modular untuk mesin penerjemah teks EPUB.
 *
 * Implementasi bawaan menggunakan [GoogleTranslateManager] (Google ML Kit On-Device Translation)
 * yang gratis, cepat, mendukung pemrosesan offline, dan bebas batas kuota (rate limit).
 * Dapat diganti ke [CloudTranslationEngine] (Google Cloud Translation API) kapan saja
 * tanpa mengubah lapisan [com.goepub.app.domain.epub.EpubProcessor] maupun Worker.
 */
interface TranslationEngine : Closeable {

    /**
     * Memeriksa apakah model bahasa sudah tersedia secara lokal di perangkat.
     * Untuk engine berbasis cloud, selalu mengembalikan `true`.
     */
    suspend fun areModelsReady(sourceLanguage: String, targetLanguage: String): Boolean

    /**
     * Memastikan model bahasa sumber dan tujuan tersedia di perangkat.
     * Mengunduh model secara otomatis bila belum tersedia.
     *
     * @param sourceLanguage Kode bahasa sumber ISO-639-1 (mis. `"en"`).
     * @param targetLanguage Kode bahasa tujuan ISO-639-1 (mis. `"id"`).
     * @param allowCellular Izinkan pengunduhan melalui jaringan seluler (bukan hanya Wi-Fi).
     */
    suspend fun ensureModel(
        sourceLanguage: String,
        targetLanguage: String,
        allowCellular: Boolean = true
    )

    /**
     * Menerjemahkan satu segmen teks dari [sourceLanguage] ke [targetLanguage].
     *
     * @param text Teks sumber (sudah melalui proses chunking bila panjang).
     * @param sourceLanguage Kode bahasa sumber, atau `null` untuk deteksi otomatis.
     * @param targetLanguage Kode bahasa tujuan.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String
    ): String

    /**
     * Mendeteksi kode bahasa ISO-639-1 dari cuplikan teks.
     * Mengembalikan `null` bila bahasa tidak dapat dikenali secara meyakinkan.
     */
    suspend fun detectLanguage(text: String): String?

    /**
     * Melepaskan resource native/jaringan yang digunakan oleh mesin penerjemah.
     */
    override fun close() {}
}
