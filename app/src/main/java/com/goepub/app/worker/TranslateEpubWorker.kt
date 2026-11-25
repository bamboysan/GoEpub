package com.goepub.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.goepub.app.MainActivity
import com.goepub.app.R
import com.goepub.app.data.TranslatedBookRecord
import com.goepub.app.data.TranslationHistoryRepository
import com.goepub.app.domain.epub.EpubErrorKind
import com.goepub.app.domain.epub.EpubException
import com.goepub.app.domain.epub.EpubProcessor
import com.goepub.app.domain.epub.EpubStage
import com.goepub.app.domain.translator.GoogleTranslateManager
import com.goepub.app.ui.i18n.AppLanguage
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Background [CoroutineWorker] that translates EPUB e-books reliably even when the screen is off.
 */
class TranslateEpubWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val appLang = AppLanguage.fromCode(inputData.getString(INPUT_APP_LANGUAGE))
        val isId = appLang == AppLanguage.ID

        ensureNotificationChannel(isId)

        val uriString = inputData.getString(INPUT_URI)
            ?: return failureResult(
                if (isId) "URI file EPUB tidak ditemukan." else "EPUB file URI was not provided.",
                EpubErrorKind.INVALID_STRUCTURE.name
            )

        val sourceUri = Uri.parse(uriString)
        val sourceLanguage = inputData.getString(INPUT_SOURCE_LANGUAGE)?.takeIf { it.isNotBlank() }
        val targetLanguage = inputData.getString(INPUT_TARGET_LANGUAGE)?.takeIf { it.isNotBlank() }
            ?: return failureResult(
                if (isId) "Bahasa tujuan belum dipilih." else "Target language was not selected.",
                EpubErrorKind.INVALID_STRUCTURE.name
            )
        val allowCellular = inputData.getBoolean(INPUT_ALLOW_CELLULAR, true)
        val passedBookTitle = inputData.getString(INPUT_BOOK_TITLE)

        val sourceCacheDir = File(applicationContext.cacheDir, "epub_worker_input").apply { mkdirs() }
        val stableCacheFile = File(sourceCacheDir, "active_input_${id}.epub")

        if (!stableCacheFile.isFile || stableCacheFile.length() == 0L) {
            try {
                copyUriToFile(sourceUri, stableCacheFile)
            } catch (_: Exception) {
                stableCacheFile.delete()
                return failureResult(
                    if (isId) {
                        "Tidak dapat membuka file EPUB yang dipilih. Pastikan file masih tersedia."
                    } else {
                        "Unable to open the selected EPUB file. Please make sure it is still available."
                    },
                    EpubErrorKind.CORRUPTED_ARCHIVE.name
                )
            }
        }

        val historyRepo = TranslationHistoryRepository(applicationContext)
        val engine = GoogleTranslateManager()
        val processor = EpubProcessor(
            cacheRootDir = applicationContext.cacheDir,
            outputRootDir = historyRepo.outputDirectory,
            engine = engine,
            uiLanguageCode = appLang.code
        )

        var shouldDeleteInputCache = true

        return try {
            val bookInfo = processor.inspectEpub(stableCacheFile, passedBookTitle)
            val bookTitle = bookInfo.title

            tryPromoteForeground(
                title = bookTitle,
                status = if (isId) "Menyiapkan proses terjemahan..." else "Preparing translation...",
                progress = 0,
                indeterminate = true,
                isId = isId
            )

            var detectedOrUsedSource = sourceLanguage ?: bookInfo.language ?: "en"

            val outputFile = processor.translateEpub(
                sourceFile = stableCacheFile,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                allowCellularModelDownload = allowCellular,
                desiredOutputBaseName = bookTitle
            ) { stage ->
                when (stage) {
                    is EpubStage.Extracting -> {
                        val status = if (isId) "Mengekstrak struktur buku..." else "Extracting book structure..."
                        emitProgress(STAGE_EXTRACTING, 0, status, bookTitle, 0, bookInfo.chapterCount, "")
                        tryPromoteForeground(bookTitle, status, 0, indeterminate = true, isId = isId)
                    }

                    is EpubStage.DetectingLanguage -> {
                        val status = if (isId) "Mendeteksi bahasa sumber..." else "Detecting source language..."
                        emitProgress(STAGE_DETECTING, 0, status, bookTitle, 0, bookInfo.chapterCount, "")
                        tryPromoteForeground(bookTitle, status, 0, indeterminate = true, isId = isId)
                    }

                    is EpubStage.DownloadingModel -> {
                        detectedOrUsedSource = stage.sourceLanguage
                        val srcLabel = GoogleTranslateManager.displayNameFor(stage.sourceLanguage, appLang)
                        val tgtLabel = GoogleTranslateManager.displayNameFor(stage.targetLanguage, appLang)
                        val pair = "$srcLabel → $tgtLabel"
                        val status = if (isId) {
                            "Mengunduh paket bahasa ($pair)..."
                        } else {
                            "Downloading language pack ($pair)..."
                        }
                        emitProgress(STAGE_DOWNLOADING_MODEL, 0, status, bookTitle, 0, bookInfo.chapterCount, pair)
                        tryPromoteForeground(bookTitle, status, 0, indeterminate = true, isId = isId)
                    }

                    is EpubStage.Translating -> {
                        val status = if (isId) {
                            "Bab ${stage.chapterIndex}/${stage.totalChapters} • ${stage.chapterTitle}"
                        } else {
                            "Chapter ${stage.chapterIndex}/${stage.totalChapters} • ${stage.chapterTitle}"
                        }
                        emitProgress(
                            stage = STAGE_TRANSLATING,
                            percent = stage.percent,
                            status = status,
                            bookTitle = bookTitle,
                            chapterIndex = stage.chapterIndex,
                            totalChapters = stage.totalChapters,
                            chapterTitle = stage.chapterTitle
                        )
                        tryPromoteForeground(bookTitle, status, stage.percent, indeterminate = false, isId = isId)
                    }

                    is EpubStage.Repackaging -> {
                        val status = if (isId) "Mengemas ulang file EPUB..." else "Repackaging EPUB archive..."
                        val sub = if (isId) "Menyelesaikan file EPUB" else "Finalizing EPUB file"
                        emitProgress(
                            stage = STAGE_REPACKAGING,
                            percent = 99,
                            status = status,
                            bookTitle = bookTitle,
                            chapterIndex = bookInfo.chapterCount,
                            totalChapters = bookInfo.chapterCount,
                            chapterTitle = sub
                        )
                        tryPromoteForeground(bookTitle, status, 99, indeterminate = false, isId = isId)
                    }
                }
            }

            val record = TranslatedBookRecord(
                id = UUID.randomUUID().toString(),
                title = bookInfo.title,
                author = bookInfo.author,
                sourceLanguage = GoogleTranslateManager.normalizeLanguageCode(detectedOrUsedSource),
                targetLanguage = GoogleTranslateManager.normalizeLanguageCode(targetLanguage),
                filePath = outputFile.absolutePath,
                fileName = outputFile.name,
                fileSizeBytes = outputFile.length(),
                chapterCount = bookInfo.chapterCount,
                completedAtMillis = System.currentTimeMillis()
            )
            historyRepo.saveRecord(record)
            postCompletionNotification(bookInfo.title, outputFile.name, isId)

            Result.success(
                workDataOf(
                    OUTPUT_FILE_PATH to outputFile.absolutePath,
                    OUTPUT_BOOK_TITLE to bookInfo.title,
                    OUTPUT_BOOK_AUTHOR to (bookInfo.author ?: ""),
                    OUTPUT_CHAPTER_COUNT to bookInfo.chapterCount,
                    OUTPUT_SOURCE_LANG to record.sourceLanguage,
                    OUTPUT_TARGET_LANG to record.targetLanguage
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (ee: EpubException) {
            failureResult(
                message = ee.message ?: if (isId) {
                    "File EPUB tidak valid atau dilindungi DRM."
                } else {
                    "Invalid or DRM-protected EPUB file."
                },
                kind = ee.kind.name
            )
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                shouldDeleteInputCache = false
                Result.retry()
            } else {
                val friendlyMessage = buildFriendlyErrorMessage(e, isId)
                failureResult(friendlyMessage, "TRANSLATION_ERROR")
            }
        } finally {
            engine.close()
            if (shouldDeleteInputCache) {
                stableCacheFile.delete()
            }
        }
    }

    private suspend fun emitProgress(
        stage: String,
        percent: Int,
        status: String,
        bookTitle: String,
        chapterIndex: Int,
        totalChapters: Int,
        chapterTitle: String
    ) {
        setProgress(
            workDataOf(
                PROGRESS_STAGE to stage,
                PROGRESS_PERCENT to percent,
                PROGRESS_STATUS to status,
                PROGRESS_BOOK_TITLE to bookTitle,
                PROGRESS_CHAPTER_INDEX to chapterIndex,
                PROGRESS_TOTAL_CHAPTERS to totalChapters,
                PROGRESS_CHAPTER_TITLE to chapterTitle
            )
        )
    }

    private suspend fun tryPromoteForeground(
        title: String,
        status: String,
        progress: Int,
        indeterminate: Boolean,
        isId: Boolean
    ) {
        runCatching {
            setForeground(createForegroundInfo(title, status, progress, indeterminate, isId))
        }
    }

    private fun createForegroundInfo(
        title: String,
        status: String,
        progress: Int,
        indeterminate: Boolean,
        isId: Boolean
    ): ForegroundInfo {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val defaultTitle = if (isId) "Menerjemahkan EPUB" else "Translating EPUB"
        val cancelLabel = if (isId) "Batalkan" else "Cancel"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(title.ifBlank { defaultTitle })
            .setContentText(status)
            .setSubText(if (indeterminate) null else "$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .addAction(0, cancelLabel, cancelIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    private fun postCompletionNotification(bookTitle: String, outputFileName: String, isId: Boolean) {
        runCatching {
            val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notifTitle = if (isId) "Terjemahan Selesai" else "Translation Complete"
            val notifBody = if (isId) {
                "$bookTitle siap disimpan atau dibagikan ($outputFileName)"
            } else {
                "$bookTitle is ready to save or share ($outputFileName)"
            }

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_translate)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
        }
    }

    private fun ensureNotificationChannel(isId: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = if (isId) "Proses Terjemahan EPUB" else "EPUB Translation Progress"
            val desc = if (isId) {
                "Menampilkan progres penerjemahan buku EPUB di latar belakang"
            } else {
                "Shows background e-book translation progress"
            }
            val channel = NotificationChannel(
                CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = desc
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun copyUriToFile(uri: Uri, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Empty input stream or access denied.")
    }

    private fun buildFriendlyErrorMessage(error: Exception, isId: Boolean): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("network", ignoreCase = true) ||
                raw.contains("download", ignoreCase = true) ||
                raw.contains("Unable to resolve host", ignoreCase = true) ||
                raw.contains("Internal error has occurred", ignoreCase = true) ->
                if (isId) {
                    "Gagal mengunduh model bahasa offline. Periksa koneksi internet Anda lalu coba lagi."
                } else {
                    "Failed to download the offline language model. Please check your internet connection and try again."
                }

            raw.contains("ENOSPC", ignoreCase = true) ||
                raw.contains("No space left", ignoreCase = true) ->
                if (isId) {
                    "Ruang penyimpanan perangkat tidak mencukupi untuk menyimpan model bahasa atau hasil buku."
                } else {
                    "Insufficient device storage space to download the language model or save the translated EPUB."
                }

            else -> raw.ifBlank {
                if (isId) "Terjadi kendala saat memproses terjemahan. Silakan coba kembali."
                else "An unexpected error occurred during translation. Please try again."
            }
        }
    }

    private fun failureResult(message: String, kind: String): Result {
        return Result.failure(
            workDataOf(
                ERROR_KEY to message,
                ERROR_KIND to kind
            )
        )
    }

    companion object {
        const val WORK_NAME = "goepub_translate_unique_work"

        const val CHANNEL_ID = "goepub_translation_channel"
        private const val NOTIFICATION_ID_PROGRESS = 1001
        private const val NOTIFICATION_ID_COMPLETE = 1002

        const val INPUT_URI = "input_uri"
        const val INPUT_SOURCE_LANGUAGE = "input_source_language"
        const val INPUT_TARGET_LANGUAGE = "input_target_language"
        const val INPUT_ALLOW_CELLULAR = "input_allow_cellular"
        const val INPUT_BOOK_TITLE = "input_book_title"
        const val INPUT_APP_LANGUAGE = "input_app_language"

        const val OUTPUT_FILE_PATH = "output_file_path"
        const val OUTPUT_BOOK_TITLE = "output_book_title"
        const val OUTPUT_BOOK_AUTHOR = "output_book_author"
        const val OUTPUT_CHAPTER_COUNT = "output_chapter_count"
        const val OUTPUT_SOURCE_LANG = "output_source_lang"
        const val OUTPUT_TARGET_LANG = "output_target_lang"

        const val ERROR_KEY = "error_message"
        const val ERROR_KIND = "error_kind"

        const val PROGRESS_STAGE = "progress_stage"
        const val PROGRESS_PERCENT = "progress_percent"
        const val PROGRESS_STATUS = "progress_status"
        const val PROGRESS_BOOK_TITLE = "progress_book_title"
        const val PROGRESS_CHAPTER_INDEX = "progress_chapter_index"
        const val PROGRESS_TOTAL_CHAPTERS = "progress_total_chapters"
        const val PROGRESS_CHAPTER_TITLE = "progress_chapter_title"

        const val STAGE_EXTRACTING = "EXTRACTING"
        const val STAGE_DETECTING = "DETECTING_LANGUAGE"
        const val STAGE_DOWNLOADING_MODEL = "DOWNLOADING_MODEL"
        const val STAGE_TRANSLATING = "TRANSLATING"
        const val STAGE_REPACKAGING = "REPACKAGING"

        private const val MAX_RETRIES = 2

        fun resolveDisplayFileName(context: Context, uri: Uri): String {
            val fromCursor = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
                }
            }.getOrNull()

            return fromCursor?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                ?: "book.epub"
        }

        fun enqueue(
            context: Context,
            sourceUri: Uri,
            sourceLanguage: String?,
            targetLanguage: String,
            allowCellular: Boolean = true,
            bookTitle: String? = null,
            appLanguageCode: String = AppLanguage.EN.code
        ) {
            val dataBuilder = Data.Builder()
                .putString(INPUT_URI, sourceUri.toString())
                .putString(INPUT_TARGET_LANGUAGE, targetLanguage)
                .putBoolean(INPUT_ALLOW_CELLULAR, allowCellular)
                .putString(INPUT_APP_LANGUAGE, appLanguageCode)

            if (!sourceLanguage.isNullOrBlank()) {
                dataBuilder.putString(INPUT_SOURCE_LANGUAGE, sourceLanguage)
            }
            if (!bookTitle.isNullOrBlank()) {
                dataBuilder.putString(INPUT_BOOK_TITLE, bookTitle)
            }

            val requestBuilder = OneTimeWorkRequestBuilder<TranslateEpubWorker>()
                .setInputData(dataBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }
    }
}
