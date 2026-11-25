package com.goepub.app.domain.epub

/**
 * Metadata ringkas dari sebuah file EPUB untuk ditampilkan sebelum dan sesudah proses terjemahan.
 */
data class EpubBookInfo(
    val title: String,
    val author: String?,
    val language: String?,
    val chapterCount: Int,
    val fileSizeBytes: Long,
    val coverImageBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EpubBookInfo) return false
        return title == other.title &&
            author == other.author &&
            language == other.language &&
            chapterCount == other.chapterCount &&
            fileSizeBytes == other.fileSizeBytes &&
            (coverImageBytes?.contentEquals(other.coverImageBytes)
                ?: (other.coverImageBytes == null))
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + chapterCount
        result = 31 * result + fileSizeBytes.hashCode()
        result = 31 * result + (coverImageBytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Tahapan pemrosesan terjemahan EPUB untuk pelaporan progres secara real-time.
 */
sealed interface EpubStage {
    data object Extracting : EpubStage
    data object DetectingLanguage : EpubStage
    data class DownloadingModel(
        val sourceLanguage: String,
        val targetLanguage: String
    ) : EpubStage

    data class Translating(
        val chapterIndex: Int,
        val totalChapters: Int,
        val chapterTitle: String,
        val percent: Int
    ) : EpubStage

    data object Repackaging : EpubStage
}

/**
 * Kategori error spesifik EPUB agar pesan yang ditampilkan kepada pengguna jelas dan solutif.
 */
enum class EpubErrorKind {
    DRM_PROTECTED,
    CORRUPTED_ARCHIVE,
    INVALID_STRUCTURE,
    EMPTY_CONTENT
}

/**
 * Exception khusus domain EPUB dengan kategori [kind] yang terstruktur.
 */
class EpubException(
    message: String,
    val kind: EpubErrorKind = EpubErrorKind.INVALID_STRUCTURE,
    cause: Throwable? = null
) : Exception(message, cause)
