package com.goepub.app.ui.i18n

/**
 * Supported application interface languages.
 * [EN] (English) is the default language on a fresh install.
 */
enum class AppLanguage(val code: String, val badge: String, val label: String) {
    EN("en", "EN", "English"),
    ID("id", "ID", "Bahasa Indonesia");

    fun toggle(): AppLanguage = if (this == EN) ID else EN

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: EN
        }
    }
}

/**
 * Complete bilingual UI & notification copywriting (English default & Bahasa Indonesia).
 */
data class AppStrings(
    val language: AppLanguage,
    val appTagline: String,
    val switchLangTooltip: String,
    val manageModelsTooltip: String,
    val privacyTooltip: String,

    // Idle
    val heroTitle: String,
    val heroSubtitle: String,
    val pickEpubButton: String,
    val offlinePacksActiveFormat: (Int) -> String,
    val freeUnlimitedTag: String,
    val featurePrivateTitle: String,
    val featurePrivateSubtitle: String,
    val featureLayoutTitle: String,
    val featureLayoutSubtitle: String,
    val featureBackgroundTitle: String,
    val featureBackgroundSubtitle: String,
    val recentTranslationsTitle: String,
    val chaptersWord: String,
    val deleteHistoryDesc: (String) -> String,

    // Inspecting
    val inspectingTitle: String,

    // Configuring
    val readyBadge: String,
    val changeFileButton: String,
    val translationSettingsTitle: String,
    val translationSettingsSubtitle: String,
    val sourceLanguageLabel: String,
    val targetLanguageLabel: String,
    val autoDetectLabel: String,
    val detectedFromMetadataFormat: (String) -> String,
    val autoRecognizedSubtitle: String,
    val readyOfflineSubtitle: String,
    val willDownloadSubtitle: String,
    val swapLanguagesDesc: String,
    val cellularDownloadTitle: String,
    val cellularNeedsDownloadHint: String,
    val cellularAlreadyReadyHint: String,
    val startTranslateButton: String,
    val sameLanguageWarning: String,
    val backToHomeButton: String,

    // Language Picker Sheet
    val pickSourceLanguageTitle: String,
    val pickTargetLanguageTitle: String,
    val searchLanguagePlaceholder: String,
    val recommendedAutoFormat: (String) -> String,
    val autoDetectSheetSubtitle: String,
    val readyOfflineShort: String,

    // Downloading Model
    val preparingLanguagePackTitle: String,
    val languagePackFallbackLabel: String,
    val downloadingModelBodyFormat: (String) -> String,
    val cancelButton: String,

    // Translating
    val chapterProgressFormat: (Int, Int) -> String,
    val currentlyTranslatingFormat: (String) -> String,
    val backgroundSafeHint: String,
    val cancelProcessButton: String,
    val cancelDialogTitle: String,
    val cancelDialogBody: String,
    val confirmCancelButton: String,
    val continueProcessButton: String,

    // Success
    val successTitle: String,
    val successSubtitle: String,
    val saveToDeviceButton: String,
    val shareButton: String,
    val shareChooserTitle: String,
    val openBookButton: String,
    val translateAnotherButton: String,
    val noEpubReaderMessage: String,

    // Error
    val retryButton: String,
    val pickAnotherEpubButton: String,
    val errorTitleDrm: String,
    val errorTitleCorrupt: String,
    val errorTitleInvalid: String,
    val errorTitleEmpty: String,
    val errorTitleStopped: String,
    val errorTitleCannotOpen: String,

    // Offline Models Sheet
    val offlineModelsTitle: String,
    val offlineModelsSubtitle: String,
    val storedOfflineStatus: String,
    val notDownloadedStatus: String,
    val downloadAction: String,
    val deletePackDesc: (String) -> String,

    // Privacy & About Sheet
    val aboutVersionTitle: String,
    val aboutSubtitle: String,
    val policyOnDeviceTitle: String,
    val policyOnDeviceBody: String,
    val policyStorageTitle: String,
    val policyStorageBody: String,
    val policyCopyrightTitle: String,
    val policyCopyrightBody: String,
    val policyNetworkTitle: String,
    val policyNetworkBody: String,
    val clearAllHistoryButton: String,

    // Snackbars
    val msgSameLanguage: String,
    val msgCancelled: String,
    val msgSavedToDevice: String,
    val msgSaveFailed: String,
    val msgFileMissing: String,
    val msgHistoryDeleted: String,
    val msgAllHistoryCleared: String,
    val msgModelReadyFormat: (String) -> String,
    val msgModelDownloadFailedFormat: (String) -> String,
    val msgModelDeletedFormat: (String) -> String,
    val msgModelDeleteFailedFormat: (String) -> String
) {
    companion object {
        fun forLanguage(language: AppLanguage): AppStrings = when (language) {
            AppLanguage.EN -> ENGLISH
            AppLanguage.ID -> INDONESIAN
        }

        val ENGLISH = AppStrings(
            language = AppLanguage.EN,
            appTagline = "Instant EPUB Translator",
            switchLangTooltip = "Switch App Language (EN / ID)",
            manageModelsTooltip = "Manage Offline Language Packs",
            privacyTooltip = "Privacy & App Info",

            heroTitle = "Read Books in Any Language",
            heroSubtitle = "Pick an .epub file from your device. Every chapter is translated automatically while keeping covers, images, and page styling intact.",
            pickEpubButton = "Select EPUB File",
            offlinePacksActiveFormat = { count -> "$count offline language pack(s) ready • Manage" },
            freeUnlimitedTag = "100% free & unlimited with Google ML Kit",
            featurePrivateTitle = "100% Private",
            featurePrivateSubtitle = "On-device AI",
            featureLayoutTitle = "Intact Layout",
            featureLayoutSubtitle = "CSS & images safe",
            featureBackgroundTitle = "Background",
            featureBackgroundSubtitle = "Runs automatically",
            recentTranslationsTitle = "Recent Translations",
            chaptersWord = "chapters",
            deleteHistoryDesc = { title -> "Delete $title from history" },

            inspectingTitle = "Inspecting Book Structure...",

            readyBadge = "READY TO TRANSLATE",
            changeFileButton = "Change File",
            translationSettingsTitle = "Translation Settings",
            translationSettingsSubtitle = "Choose the source language and your target reading language.",
            sourceLanguageLabel = "Source Language",
            targetLanguageLabel = "Target Language",
            autoDetectLabel = "Auto-Detect",
            detectedFromMetadataFormat = { lang -> "Detected from metadata: $lang" },
            autoRecognizedSubtitle = "Automatically recognized by ML Kit",
            readyOfflineSubtitle = "Ready for offline translation",
            willDownloadSubtitle = "Will download automatically (~30 MB)",
            swapLanguagesDesc = "Swap languages",
            cellularDownloadTitle = "Allow language download over mobile data",
            cellularNeedsDownloadHint = "A ~30 MB language pack will be downloaded once automatically.",
            cellularAlreadyReadyHint = "Language packs are already on your device (ready offline).",
            startTranslateButton = "Start Translation",
            sameLanguageWarning = "Please select a target language different from the source language.",
            backToHomeButton = "Back to Home",

            pickSourceLanguageTitle = "Select Source Language",
            pickTargetLanguageTitle = "Select Target Language",
            searchLanguagePlaceholder = "Search language...",
            recommendedAutoFormat = { lang -> "Recommended ($lang)" },
            autoDetectSheetSubtitle = "Automatically detect the book's language",
            readyOfflineShort = "Offline Ready",

            preparingLanguagePackTitle = "Preparing Language Pack",
            languagePackFallbackLabel = "Offline Language Pack",
            downloadingModelBodyFormat = { title ->
                "Language packs (~30 MB) are downloaded only once. Translation for \"$title\" will start automatically right after."
            },
            cancelButton = "Cancel",

            chapterProgressFormat = { cur, total -> "Chapter $cur of $total" },
            currentlyTranslatingFormat = { ch -> "Translating: $ch" },
            backgroundSafeHint = "Safe to turn off your screen or switch to another app.",
            cancelProcessButton = "Cancel Translation",
            cancelDialogTitle = "Cancel Translation?",
            cancelDialogBody = "Current translation progress will be stopped and discarded.",
            confirmCancelButton = "Yes, Cancel",
            continueProcessButton = "Continue",

            successTitle = "Book Ready to Read!",
            successSubtitle = "All chapters have been translated with original formatting preserved.",
            saveToDeviceButton = "Save to Device",
            shareButton = "Share",
            shareChooserTitle = "Share translated EPUB",
            openBookButton = "Open Book",
            translateAnotherButton = "Translate Another Book",
            noEpubReaderMessage = "No EPUB reader app (such as Google Play Books) was found on this device.",

            retryButton = "Try Again",
            pickAnotherEpubButton = "Select Another EPUB File",
            errorTitleDrm = "DRM-Protected Book",
            errorTitleCorrupt = "Corrupted EPUB File",
            errorTitleInvalid = "Invalid EPUB Structure",
            errorTitleEmpty = "No Translatable Chapters",
            errorTitleStopped = "Translation Interrupted",
            errorTitleCannotOpen = "Unable to Open File",

            offlineModelsTitle = "Offline Language Packs",
            offlineModelsSubtitle = "Pre-download over Wi-Fi or remove unused packs (~30 MB per language).",
            storedOfflineStatus = "Stored offline",
            notDownloadedStatus = "Not downloaded",
            downloadAction = "Download",
            deletePackDesc = { lang -> "Delete $lang pack" },

            aboutVersionTitle = "GoEpub • Version 1.0.0",
            aboutSubtitle = "Privacy, Data Security & Terms of Use",
            policyOnDeviceTitle = "100% On-Device Processing",
            policyOnDeviceBody = "Your entire EPUB e-book is processed and translated locally on your device using Google ML Kit On-Device Translation. Your book content is never uploaded to any external server.",
            policyStorageTitle = "Scoped Storage Access",
            policyStorageBody = "GoEpub never requests broad file access permissions. It only reads the single .epub file you explicitly choose via Android's system document picker.",
            policyCopyrightTitle = "Copyright & Fair Use",
            policyCopyrightBody = "Please use GoEpub for personal documents, public-domain works, or DRM-free e-books you lawfully own. GoEpub does not bypass or remove DRM protection.",
            policyNetworkTitle = "Network Usage & Open Source",
            policyNetworkBody = "Internet access is used solely to download official Google ML Kit language packs. Built with Android Jetpack, Kotlin Coroutines, and Jsoup (MIT License).",
            clearAllHistoryButton = "Clear All Translated Book History",

            msgSameLanguage = "Source and target languages are already the same.",
            msgCancelled = "Translation cancelled.",
            msgSavedToDevice = "EPUB saved to your device.",
            msgSaveFailed = "Failed to save EPUB file.",
            msgFileMissing = "File is no longer available in storage.",
            msgHistoryDeleted = "Removed from recent translations.",
            msgAllHistoryCleared = "All translated book history cleared.",
            msgModelReadyFormat = { lang -> "$lang pack is ready for offline use." },
            msgModelDownloadFailedFormat = { lang -> "Failed to download $lang pack. Check your internet connection." },
            msgModelDeletedFormat = { lang -> "$lang pack removed from device storage." },
            msgModelDeleteFailedFormat = { lang -> "Unable to delete $lang pack." }
        )

        val INDONESIAN = AppStrings(
            language = AppLanguage.ID,
            appTagline = "Penerjemah Buku EPUB Instan",
            switchLangTooltip = "Ganti Bahasa Aplikasi (EN / ID)",
            manageModelsTooltip = "Kelola Paket Bahasa Offline",
            privacyTooltip = "Privasi & Tentang Aplikasi",

            heroTitle = "Baca Buku Bahasa Apa Saja",
            heroSubtitle = "Pilih satu file .epub dari perangkat Anda. Seluruh bab diterjemahkan otomatis tanpa merusak sampul, gambar, atau tata letak halaman.",
            pickEpubButton = "Pilih File EPUB",
            offlinePacksActiveFormat = { count -> "$count paket bahasa offline aktif • Kelola" },
            freeUnlimitedTag = "Gratis & tanpa batas kuota dengan Google ML Kit",
            featurePrivateTitle = "100% Privat",
            featurePrivateSubtitle = "Diproses di HP",
            featureLayoutTitle = "Tata Letak Utuh",
            featureLayoutSubtitle = "CSS & gambar aman",
            featureBackgroundTitle = "Latar Belakang",
            featureBackgroundSubtitle = "Jalan otomatis",
            recentTranslationsTitle = "Terjemahan Terakhir",
            chaptersWord = "bab",
            deleteHistoryDesc = { title -> "Hapus riwayat $title" },

            inspectingTitle = "Memeriksa Struktur Buku...",

            readyBadge = "EPUB SIAP DITERJEMAHKAN",
            changeFileButton = "Ganti File",
            translationSettingsTitle = "Pengaturan Terjemahan",
            translationSettingsSubtitle = "Pilih bahasa asal dan bahasa tujuan terjemahan buku.",
            sourceLanguageLabel = "Bahasa Sumber",
            targetLanguageLabel = "Bahasa Tujuan",
            autoDetectLabel = "Deteksi Otomatis",
            detectedFromMetadataFormat = { lang -> "Terdeteksi dari metadata: $lang" },
            autoRecognizedSubtitle = "Dikenali otomatis oleh ML Kit",
            readyOfflineSubtitle = "Siap digunakan offline",
            willDownloadSubtitle = "Akan diunduh otomatis (~30 MB)",
            swapLanguagesDesc = "Tukar bahasa",
            cellularDownloadTitle = "Unduh paket bahasa via data seluler",
            cellularNeedsDownloadHint = "Paket bahasa (~30 MB) akan diunduh otomatis satu kali.",
            cellularAlreadyReadyHint = "Paket bahasa sudah tersedia di perangkat (siap offline).",
            startTranslateButton = "Mulai Terjemahkan",
            sameLanguageWarning = "Pilih bahasa tujuan yang berbeda dari bahasa sumber.",
            backToHomeButton = "Kembali ke Beranda",

            pickSourceLanguageTitle = "Pilih Bahasa Sumber",
            pickTargetLanguageTitle = "Pilih Bahasa Tujuan",
            searchLanguagePlaceholder = "Cari bahasa...",
            recommendedAutoFormat = { lang -> "Disarankan ($lang)" },
            autoDetectSheetSubtitle = "Kenali bahasa buku secara otomatis",
            readyOfflineShort = "Siap Offline",

            preparingLanguagePackTitle = "Menyiapkan Paket Bahasa",
            languagePackFallbackLabel = "Paket Bahasa Offline",
            downloadingModelBodyFormat = { title ->
                "Paket bahasa (~30 MB) hanya perlu diunduh satu kali. Setelah selesai, proses terjemahan \"$title\" akan langsung dimulai otomatis."
            },
            cancelButton = "Batalkan",

            chapterProgressFormat = { cur, total -> "Bab $cur dari $total" },
            currentlyTranslatingFormat = { ch -> "Sedang menerjemahkan: $ch" },
            backgroundSafeHint = "Aman mematikan layar atau berpindah ke aplikasi lain.",
            cancelProcessButton = "Batalkan Proses",
            cancelDialogTitle = "Batalkan Terjemahan?",
            cancelDialogBody = "Progres bab yang sedang berjalan akan dihentikan dan tidak disimpan.",
            confirmCancelButton = "Ya, Batalkan",
            continueProcessButton = "Lanjutkan",

            successTitle = "Buku Siap Dibaca!",
            successSubtitle = "Seluruh bab telah diterjemahkan dengan struktur asli yang tetap rapi.",
            saveToDeviceButton = "Simpan ke Perangkat",
            shareButton = "Bagikan",
            shareChooserTitle = "Bagikan buku EPUB",
            openBookButton = "Buka Buku",
            translateAnotherButton = "Terjemahkan Buku Lain",
            noEpubReaderMessage = "Belum ada aplikasi pembaca EPUB (seperti Google Play Buku) di perangkat ini.",

            retryButton = "Coba Lagi",
            pickAnotherEpubButton = "Pilih File EPUB Lain",
            errorTitleDrm = "Buku Dilindungi DRM",
            errorTitleCorrupt = "File EPUB Rusak",
            errorTitleInvalid = "Format EPUB Tidak Valid",
            errorTitleEmpty = "Tidak Ada Teks Bab",
            errorTitleStopped = "Proses Terjemahan Terhenti",
            errorTitleCannotOpen = "File Tidak Dapat Dibuka",

            offlineModelsTitle = "Paket Bahasa Offline",
            offlineModelsSubtitle = "Unduh lebih awal melalui Wi-Fi atau hapus paket yang jarang dipakai (~30 MB per bahasa).",
            storedOfflineStatus = "Tersimpan offline",
            notDownloadedStatus = "Belum diunduh",
            downloadAction = "Unduh",
            deletePackDesc = { lang -> "Hapus paket $lang" },

            aboutVersionTitle = "GoEpub • Versi 1.0.0",
            aboutSubtitle = "Privasi, Keamanan Data & Ketentuan Layanan",
            policyOnDeviceTitle = "100% Pemrosesan di Perangkat (On-Device)",
            policyOnDeviceBody = "Seluruh isi buku EPUB Anda diproses dan diterjemahkan langsung di perangkat menggunakan Google ML Kit On-Device Translation. Kami tidak pernah mengunggah naskah buku Anda ke server eksternal mana pun.",
            policyStorageTitle = "Akses Penyimpanan Terbatas (Scoped Storage)",
            policyStorageBody = "GoEpub tidak meminta izin akses seluruh penyimpanan perangkat. Aplikasi hanya membuka satu file .epub yang Anda pilih secara eksplisit.",
            policyCopyrightTitle = "Hak Cipta & Ketentuan Penggunaan",
            policyCopyrightBody = "Gunakan GoEpub untuk dokumen pribadi, buku domain publik, atau buku bebas DRM yang Anda miliki secara sah. Aplikasi tidak mengakali proteksi DRM.",
            policyNetworkTitle = "Penggunaan Internet & Komponen Terbuka",
            policyNetworkBody = "Koneksi internet hanya digunakan saat mengunduh paket model bahasa resmi dari Google ML Kit. Dibangun menggunakan Android Jetpack, Kotlin Coroutines, dan Jsoup (Lisensi MIT).",
            clearAllHistoryButton = "Hapus Seluruh Riwayat File Terjemahan",

            msgSameLanguage = "Bahasa sumber dan bahasa tujuan sudah sama.",
            msgCancelled = "Proses terjemahan dibatalkan.",
            msgSavedToDevice = "Buku berhasil disimpan ke perangkat Anda.",
            msgSaveFailed = "Gagal menyimpan file EPUB.",
            msgFileMissing = "File sudah tidak tersedia di penyimpanan.",
            msgHistoryDeleted = "Riwayat buku dihapus.",
            msgAllHistoryCleared = "Seluruh file riwayat terjemahan berhasil dibersihkan.",
            msgModelReadyFormat = { lang -> "Paket $lang siap digunakan secara offline." },
            msgModelDownloadFailedFormat = { lang -> "Gagal mengunduh paket $lang. Periksa koneksi internet." },
            msgModelDeletedFormat = { lang -> "Paket $lang dihapus dari penyimpanan perangkat." },
            msgModelDeleteFailedFormat = { lang -> "Gagal menghapus paket $lang." }
        )
    }
}
