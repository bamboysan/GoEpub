package com.goepub.app.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.goepub.app.data.TranslatedBookRecord
import com.goepub.app.data.TranslationHistoryRepository
import com.goepub.app.domain.epub.EpubBookInfo
import com.goepub.app.domain.epub.EpubErrorKind
import com.goepub.app.domain.epub.EpubException
import com.goepub.app.domain.epub.EpubProcessor
import com.goepub.app.domain.translator.GoogleTranslateManager
import com.goepub.app.ui.i18n.AppLanguage
import com.goepub.app.ui.i18n.AppStrings
import com.goepub.app.worker.TranslateEpubWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState

    data class InspectingFile(val fileName: String) : UiState

    data class Configuring(
        val fileName: String,
        val sourceUri: Uri,
        val bookInfo: EpubBookInfo,
        val suggestedSourceLanguage: String?
    ) : UiState

    data class DownloadingModel(
        val bookTitle: String,
        val languagePairLabel: String,
        val statusText: String
    ) : UiState

    data class Translating(
        val bookTitle: String,
        val percent: Int,
        val chapterIndex: Int,
        val totalChapters: Int,
        val chapterTitle: String,
        val statusText: String
    ) : UiState

    data class Success(
        val bookTitle: String,
        val bookAuthor: String?,
        val fileName: String,
        val outputFile: File,
        val shareUri: Uri,
        val fileSizeBytes: Long,
        val chapterCount: Int,
        val sourceLanguage: String,
        val targetLanguage: String
    ) : UiState

    data class Error(
        val kind: String,
        val message: String,
        val canRetry: Boolean
    ) : UiState
}

data class OfflineModelsUiState(
    val downloadedCodes: Set<String> = emptySet(),
    val busyLanguageCode: String? = null
)

class MainViewModel(
    private val appContext: Context,
    private val workManager: WorkManager,
    private val historyRepository: TranslationHistoryRepository
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Default app language on fresh install: English (AppLanguage.EN)
    private val _appLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.EN.code))
    )
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<TranslatedBookRecord>>(emptyList())
    val history: StateFlow<List<TranslatedBookRecord>> = _history.asStateFlow()

    private val _offlineModels = MutableStateFlow(OfflineModelsUiState())
    val offlineModels: StateFlow<OfflineModelsUiState> = _offlineModels.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val languageOptions = GoogleTranslateManager.LANGUAGE_OPTIONS

    private var lastSubmittedConfig: SubmittedConfig? = null

    private data class SubmittedConfig(
        val sourceUri: Uri,
        val sourceLanguage: String?,
        val targetLanguage: String,
        val allowCellular: Boolean,
        val bookTitle: String
    )

    private val currentStrings: AppStrings
        get() = AppStrings.forLanguage(_appLanguage.value)

    init {
        refreshHistory()
        refreshDownloadedModels()
        observeWorkProgress()
    }

    /**
     * Switches app interface language between English (`EN` default) and Indonesian (`ID`).
     */
    fun toggleAppLanguage() {
        val next = _appLanguage.value.toggle()
        _appLanguage.value = next
        prefs.edit().putString(KEY_APP_LANGUAGE, next.code).apply()
    }

    fun onFilePicked(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val displayName = TranslateEpubWorker.resolveDisplayFileName(appContext, uri)
        _uiState.value = UiState.InspectingFile(displayName)

        viewModelScope.launch {
            val lang = _appLanguage.value
            val tempInspectFile = File(
                File(appContext.cacheDir, "epub_preview").apply { mkdirs() },
                "preview_selected.epub"
            )
            try {
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        tempInspectFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw EpubException(
                        if (lang == AppLanguage.ID) {
                            "File tidak dapat dibaca dari penyimpanan perangkat."
                        } else {
                            "Unable to read the selected file from device storage."
                        },
                        EpubErrorKind.CORRUPTED_ARCHIVE
                    )
                }

                val engine = GoogleTranslateManager()
                val bookInfo = try {
                    val processor = EpubProcessor(
                        cacheRootDir = appContext.cacheDir,
                        outputRootDir = historyRepository.outputDirectory,
                        engine = engine,
                        uiLanguageCode = lang.code
                    )
                    processor.inspectEpub(tempInspectFile, displayName)
                } finally {
                    engine.close()
                }

                val normalizedHint = bookInfo.language
                    ?.let { GoogleTranslateManager.normalizeLanguageCode(it) }
                    ?.takeIf { code -> languageOptions.any { it.code == code } }

                refreshDownloadedModels()
                _uiState.value = UiState.Configuring(
                    fileName = displayName,
                    sourceUri = uri,
                    bookInfo = bookInfo,
                    suggestedSourceLanguage = normalizedHint
                )
            } catch (ee: EpubException) {
                _uiState.value = UiState.Error(
                    kind = ee.kind.name,
                    message = ee.message ?: currentStrings.errorTitleInvalid,
                    canRetry = false
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    kind = EpubErrorKind.CORRUPTED_ARCHIVE.name,
                    message = e.message ?: currentStrings.errorTitleCannotOpen,
                    canRetry = false
                )
            } finally {
                tempInspectFile.delete()
            }
        }
    }

    fun startTranslation(
        sourceLanguage: String?,
        targetLanguage: String,
        allowCellularDownload: Boolean
    ) {
        val current = _uiState.value as? UiState.Configuring ?: return
        val effectiveSource = sourceLanguage?.takeIf { it.isNotBlank() }

        if (effectiveSource != null && effectiveSource == targetLanguage) {
            _snackbarMessage.tryEmit(currentStrings.msgSameLanguage)
            return
        }

        val config = SubmittedConfig(
            sourceUri = current.sourceUri,
            sourceLanguage = effectiveSource,
            targetLanguage = targetLanguage,
            allowCellular = allowCellularDownload,
            bookTitle = current.bookInfo.title
        )
        lastSubmittedConfig = config
        prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, true).apply()

        val lang = _appLanguage.value
        TranslateEpubWorker.enqueue(
            context = appContext,
            sourceUri = config.sourceUri,
            sourceLanguage = config.sourceLanguage,
            targetLanguage = config.targetLanguage,
            allowCellular = config.allowCellular,
            bookTitle = config.bookTitle,
            appLanguageCode = lang.code
        )

        val initStatus = if (lang == AppLanguage.ID) {
            "Menyiapkan proses latar belakang..."
        } else {
            "Starting background translation..."
        }
        _uiState.value = UiState.Translating(
            bookTitle = current.bookInfo.title,
            percent = 0,
            chapterIndex = 0,
            totalChapters = current.bookInfo.chapterCount,
            chapterTitle = "",
            statusText = initStatus
        )
    }

    fun retryLastTranslation() {
        val config = lastSubmittedConfig
        if (config == null) {
            reset()
            return
        }
        val lang = _appLanguage.value
        prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, true).apply()
        TranslateEpubWorker.enqueue(
            context = appContext,
            sourceUri = config.sourceUri,
            sourceLanguage = config.sourceLanguage,
            targetLanguage = config.targetLanguage,
            allowCellular = config.allowCellular,
            bookTitle = config.bookTitle,
            appLanguageCode = lang.code
        )
        _uiState.value = UiState.Translating(
            bookTitle = config.bookTitle,
            percent = 0,
            chapterIndex = 0,
            totalChapters = 0,
            chapterTitle = "",
            statusText = if (lang == AppLanguage.ID) "Mengulang proses..." else "Retrying..."
        )
    }

    fun cancelActiveTranslation() {
        prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, false).apply()
        workManager.cancelUniqueWork(TranslateEpubWorker.WORK_NAME)
        workManager.pruneWork()
        _uiState.value = UiState.Idle
        _snackbarMessage.tryEmit(currentStrings.msgCancelled)
    }

    fun saveOutputToUri(sourceFile: File, targetDocumentUri: Uri) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    if (!sourceFile.isFile) error(currentStrings.msgFileMissing)
                    appContext.contentResolver.openOutputStream(targetDocumentUri)?.use { out ->
                        sourceFile.inputStream().buffered().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error(currentStrings.msgSaveFailed)
                }
            }
            if (saved.isSuccess) {
                _snackbarMessage.tryEmit(currentStrings.msgSavedToDevice)
            } else {
                _snackbarMessage.tryEmit(
                    saved.exceptionOrNull()?.message ?: currentStrings.msgSaveFailed
                )
            }
        }
    }

    fun openHistoryRecord(record: TranslatedBookRecord) {
        val file = File(record.filePath)
        if (!file.isFile) {
            refreshHistory()
            _snackbarMessage.tryEmit(currentStrings.msgFileMissing)
            return
        }
        val shareUri = createShareUri(file)
        _uiState.value = UiState.Success(
            bookTitle = record.title,
            bookAuthor = record.author,
            fileName = record.fileName,
            outputFile = file,
            shareUri = shareUri,
            fileSizeBytes = file.length(),
            chapterCount = record.chapterCount,
            sourceLanguage = record.sourceLanguage,
            targetLanguage = record.targetLanguage
        )
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            historyRepository.deleteRecord(id)
            refreshHistory()
            _snackbarMessage.tryEmit(currentStrings.msgHistoryDeleted)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            refreshHistory()
            _snackbarMessage.tryEmit(currentStrings.msgAllHistoryCleared)
        }
    }

    fun reset() {
        prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, false).apply()
        workManager.pruneWork()
        refreshHistory()
        refreshDownloadedModels()
        _uiState.value = UiState.Idle
    }

    fun refreshDownloadedModels() {
        viewModelScope.launch {
            val codes = GoogleTranslateManager.getDownloadedModelCodes()
            _offlineModels.update { it.copy(downloadedCodes = codes) }
        }
    }

    fun downloadOfflineModel(languageCode: String) {
        if (_offlineModels.value.busyLanguageCode != null) return
        viewModelScope.launch {
            _offlineModels.update { it.copy(busyLanguageCode = languageCode) }
            val result = runCatching {
                GoogleTranslateManager.downloadSingleModel(languageCode, allowCellular = true)
            }
            val updated = GoogleTranslateManager.getDownloadedModelCodes()
            _offlineModels.update { it.copy(downloadedCodes = updated, busyLanguageCode = null) }

            val name = GoogleTranslateManager.displayNameFor(languageCode, _appLanguage.value)
            if (result.isSuccess) {
                _snackbarMessage.tryEmit(currentStrings.msgModelReadyFormat(name))
            } else {
                _snackbarMessage.tryEmit(currentStrings.msgModelDownloadFailedFormat(name))
            }
        }
    }

    fun deleteOfflineModel(languageCode: String) {
        if (_offlineModels.value.busyLanguageCode != null) return
        viewModelScope.launch {
            _offlineModels.update { it.copy(busyLanguageCode = languageCode) }
            val result = runCatching {
                GoogleTranslateManager.deleteSingleModel(languageCode)
            }
            val updated = GoogleTranslateManager.getDownloadedModelCodes()
            _offlineModels.update { it.copy(downloadedCodes = updated, busyLanguageCode = null) }

            val name = GoogleTranslateManager.displayNameFor(languageCode, _appLanguage.value)
            if (result.isSuccess) {
                _snackbarMessage.tryEmit(currentStrings.msgModelDeletedFormat(name))
            } else {
                _snackbarMessage.tryEmit(currentStrings.msgModelDeleteFailedFormat(name))
            }
        }
    }

    fun showMessage(message: String) {
        _snackbarMessage.tryEmit(message)
    }

    private fun refreshHistory() {
        viewModelScope.launch {
            _history.value = historyRepository.getHistory()
        }
    }

    private fun observeWorkProgress() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(TranslateEpubWorker.WORK_NAME)
                .collectLatest { workInfos ->
                    val info = workInfos.firstOrNull() ?: return@collectLatest
                    val hasActiveSession = prefs.getBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, false)

                    when (info.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                            prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, true).apply()

                            val stage = info.progress.getString(TranslateEpubWorker.PROGRESS_STAGE).orEmpty()
                            val percent = info.progress.getInt(TranslateEpubWorker.PROGRESS_PERCENT, 0)
                            val status = info.progress.getString(TranslateEpubWorker.PROGRESS_STATUS)
                                ?: if (_appLanguage.value == AppLanguage.ID) "Memproses buku..." else "Processing e-book..."
                            val title = info.progress.getString(TranslateEpubWorker.PROGRESS_BOOK_TITLE)
                                ?.takeIf { it.isNotBlank() }
                                ?: currentBookTitleFallback()
                            val chapterIndex = info.progress.getInt(TranslateEpubWorker.PROGRESS_CHAPTER_INDEX, 0)
                            val totalChapters = info.progress.getInt(TranslateEpubWorker.PROGRESS_TOTAL_CHAPTERS, 0)
                            val chapterTitle = info.progress.getString(TranslateEpubWorker.PROGRESS_CHAPTER_TITLE)
                                .orEmpty()

                            if (stage == TranslateEpubWorker.STAGE_DOWNLOADING_MODEL) {
                                _uiState.value = UiState.DownloadingModel(
                                    bookTitle = title,
                                    languagePairLabel = chapterTitle.ifBlank { currentStrings.languagePackFallbackLabel },
                                    statusText = status
                                )
                            } else {
                                _uiState.value = UiState.Translating(
                                    bookTitle = title,
                                    percent = percent,
                                    chapterIndex = chapterIndex,
                                    totalChapters = totalChapters,
                                    chapterTitle = chapterTitle,
                                    statusText = status
                                )
                            }
                        }

                        WorkInfo.State.SUCCEEDED -> {
                            refreshHistory()
                            refreshDownloadedModels()
                            if (!hasActiveSession) return@collectLatest

                            val outputPath = info.outputData.getString(TranslateEpubWorker.OUTPUT_FILE_PATH)
                                ?: return@collectLatest
                            val outFile = File(outputPath)
                            if (!outFile.isFile) return@collectLatest

                            val bookTitle = info.outputData.getString(TranslateEpubWorker.OUTPUT_BOOK_TITLE)
                                ?.takeIf { it.isNotBlank() }
                                ?: outFile.nameWithoutExtension
                            val author = info.outputData.getString(TranslateEpubWorker.OUTPUT_BOOK_AUTHOR)
                                ?.takeIf { it.isNotBlank() }
                            val chapters = info.outputData.getInt(TranslateEpubWorker.OUTPUT_CHAPTER_COUNT, 0)
                            val srcLang = info.outputData.getString(TranslateEpubWorker.OUTPUT_SOURCE_LANG) ?: "en"
                            val tgtLang = info.outputData.getString(TranslateEpubWorker.OUTPUT_TARGET_LANG) ?: "id"

                            _uiState.value = UiState.Success(
                                bookTitle = bookTitle,
                                bookAuthor = author,
                                fileName = outFile.name,
                                outputFile = outFile,
                                shareUri = createShareUri(outFile),
                                fileSizeBytes = outFile.length(),
                                chapterCount = chapters,
                                sourceLanguage = srcLang,
                                targetLanguage = tgtLang
                            )
                        }

                        WorkInfo.State.FAILED -> {
                            if (!hasActiveSession) return@collectLatest
                            val msg = info.outputData.getString(TranslateEpubWorker.ERROR_KEY)
                                ?: currentStrings.errorTitleStopped
                            val kind = info.outputData.getString(TranslateEpubWorker.ERROR_KIND)
                                ?: "TRANSLATION_ERROR"

                            val isDrmOrCorrupt = kind == EpubErrorKind.DRM_PROTECTED.name ||
                                kind == EpubErrorKind.CORRUPTED_ARCHIVE.name ||
                                kind == EpubErrorKind.INVALID_STRUCTURE.name

                            _uiState.value = UiState.Error(
                                kind = kind,
                                message = msg,
                                canRetry = !isDrmOrCorrupt && lastSubmittedConfig != null
                            )
                        }

                        WorkInfo.State.CANCELLED -> {
                            if (hasActiveSession) {
                                prefs.edit().putBoolean(KEY_HAS_UNSEEN_ACTIVE_SESSION, false).apply()
                                _uiState.value = UiState.Idle
                            }
                        }
                    }
                }
        }
    }

    private fun currentBookTitleFallback(): String {
        return when (val s = _uiState.value) {
            is UiState.Configuring -> s.bookInfo.title
            is UiState.DownloadingModel -> s.bookTitle
            is UiState.Translating -> s.bookTitle
            else -> lastSubmittedConfig?.bookTitle ?: "EPUB"
        }
    }

    private fun createShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    companion object {
        private const val PREFS_NAME = "goepub_session_prefs"
        private const val KEY_HAS_UNSEEN_ACTIVE_SESSION = "has_unseen_active_session"
        private const val KEY_APP_LANGUAGE = "app_ui_language"

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = context.applicationContext
                return MainViewModel(
                    appContext = appCtx,
                    workManager = WorkManager.getInstance(appCtx),
                    historyRepository = TranslationHistoryRepository(appCtx)
                ) as T
            }
        }
    }
}
