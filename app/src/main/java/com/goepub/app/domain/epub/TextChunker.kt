package com.goepub.app.domain.epub

/**
 * Utilitas pemecah teks panjang (chunking) dan pelestarian spasi inline HTML/XHTML.
 *
 * Dipisahkan sebagai komponen murni (pure Kotlin) agar mudah diuji secara menyeluruh
 * dan dapat digunakan baik oleh [EpubProcessor] maupun implementasi mesin penerjemah.
 */
object TextChunker {

    /** Batas karakter aman di bawah kuota 5.000 karakter per panggilan ML Kit / Cloud API. */
    const val DEFAULT_MAX_CHUNK_SIZE = 3800

    private val SENTENCE_BREAKS = listOf(
        ". ", "! ", "? ", ".\n", "!\n", "?\n",
        "。", "！", "？", "\n\n", "\n"
    )

    private val CLAUSE_BREAKS = listOf(
        "; ", ": ", " — ", " - ", ", ", "；", "：", "、"
    )

    /**
     * Memecah paragraf yang sangat panjang menjadi beberapa bagian (chunks) pada batas
     * kalimat, klausa, atau spasi kata terdekat agar konteks terjemahan tetap utuh.
     */
    fun chunkText(text: String, maxChunkSize: Int = DEFAULT_MAX_CHUNK_SIZE): List<String> {
        require(maxChunkSize > 0) { "maxChunkSize harus lebih besar dari 0" }
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChunkSize) return listOf(normalized)

        val chunks = mutableListOf<String>()
        var remaining = normalized

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxChunkSize) {
                chunks += remaining
                break
            }

            val cutIndex = findBestBreakIndex(remaining, maxChunkSize)
            val piece = remaining.substring(0, cutIndex).trim()
            if (piece.isNotEmpty()) {
                chunks += piece
            }
            remaining = remaining.substring(cutIndex).trimStart()
        }

        return chunks
    }

    private fun findBestBreakIndex(text: String, maxLen: Int): Int {
        val minPreferred = maxLen / 3

        // 1. Utamakan batas akhir kalimat (. ! ? 。 ！ ？ \n)
        var bestSentenceCut = -1
        for (sep in SENTENCE_BREAKS) {
            val idx = text.lastIndexOf(sep, maxLen - sep.length)
            if (idx >= minPreferred) {
                val endIdx = idx + sep.length
                if (endIdx > bestSentenceCut) bestSentenceCut = endIdx
            }
        }
        if (bestSentenceCut > 0) return bestSentenceCut

        // 2. Fallback ke batas klausa (; : , 、)
        var bestClauseCut = -1
        for (sep in CLAUSE_BREAKS) {
            val idx = text.lastIndexOf(sep, maxLen - sep.length)
            if (idx >= minPreferred) {
                val endIdx = idx + sep.length
                if (endIdx > bestClauseCut) bestClauseCut = endIdx
            }
        }
        if (bestClauseCut > 0) return bestClauseCut

        // 3. Fallback ke spasi kata terdekat
        val spaceIdx = text.lastIndexOf(' ', maxLen - 1)
        if (spaceIdx > 0) return spaceIdx + 1

        // 4. Fallback keras (mis. teks CJK panjang tanpa spasi/tanda baca)
        return maxLen
    }

    /**
     * Memisahkan spasi/whitespace pengawal & penutup (termasuk non-breaking space `\u00A0`)
     * dari isi teks node HTML agar elemen inline seperti `<b>`, `<i>`, `<em>`, `<a>`
     * tidak saling menempel setelah diterjemahkan.
     */
    data class PreservedTextSegment(
        val leadingWhitespace: String,
        val coreText: String,
        val trailingWhitespace: String
    ) {
        fun reassemble(translatedCore: String): String {
            val cleaned = translatedCore.trim { it.isWhitespace() && it != '\u00A0' }
            return leadingWhitespace + cleaned + trailingWhitespace
        }
    }

    fun extractSegment(wholeText: String): PreservedTextSegment? {
        if (wholeText.isEmpty()) return null

        val firstNonWs = wholeText.indexOfFirst { !it.isWhitespace() && it != '\u00A0' }
        if (firstNonWs < 0) return null // Hanya berisi spasi/newline/nbsp

        val lastNonWs = wholeText.indexOfLast { !it.isWhitespace() && it != '\u00A0' }
        val rawLeading = wholeText.substring(0, firstNonWs)
        val rawTrailing = wholeText.substring(lastNonWs + 1)
        val rawCore = wholeText.substring(firstNonWs, lastNonWs + 1)

        // Lewati segmen yang hanya berupa angka, simbol, atau tanda baca (tanpa huruf alfabet/aksara)
        if (rawCore.none { it.isLetter() }) return null

        // Normalisasi spasi pengawal/penutup: pertahankan nbsp (\u00A0), atau satu spasi bila ada
        // spasi inline; hindari menggandakan indentasi baris baru dari source HTML.
        val normalizedLeading = normalizeBoundaryWhitespace(rawLeading)
        val normalizedTrailing = normalizeBoundaryWhitespace(rawTrailing)

        // Rapikan newline indentasi di tengah paragraf menjadi spasi tunggal agar kalimat utuh
        val collapsedCore = rawCore
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        if (collapsedCore.isEmpty()) return null

        return PreservedTextSegment(
            leadingWhitespace = normalizedLeading,
            coreText = collapsedCore,
            trailingWhitespace = normalizedTrailing
        )
    }

    private fun normalizeBoundaryWhitespace(ws: String): String {
        if (ws.isEmpty()) return ""
        val nbspCount = ws.count { it == '\u00A0' }
        if (nbspCount > 0) return "\u00A0".repeat(nbspCount)
        return " "
    }
}
