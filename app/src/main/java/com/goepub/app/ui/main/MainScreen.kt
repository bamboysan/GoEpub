package com.goepub.app.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goepub.app.data.TranslatedBookRecord
import com.goepub.app.domain.epub.EpubErrorKind
import com.goepub.app.domain.translator.GoogleTranslateManager
import com.goepub.app.ui.components.CleanSurfaceCard
import com.goepub.app.ui.components.GoEpubBrandLogo
import com.goepub.app.ui.components.HeroBookIllustration
import com.goepub.app.ui.components.OfflineModelsBottomSheet
import com.goepub.app.ui.components.PrivacyAndAboutBottomSheet
import com.goepub.app.ui.components.SupportDeveloperBannerCard
import com.goepub.app.ui.components.SupportDeveloperBottomSheet
import com.goepub.app.ui.i18n.AppLanguage
import com.goepub.app.ui.i18n.AppStrings
import com.goepub.app.ui.theme.GoEpubTheme
import com.goepub.app.ui.theme.SuccessGreen
import com.goepub.app.ui.theme.SuccessGreenContainer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onPickFile: () -> Unit,
    onRequestNotificationPermissionIfNeeded: () -> Unit = {}
) {
    val appLanguage by viewModel.appLanguage.collectAsState()
    val strings = remember(appLanguage) { AppStrings.forLanguage(appLanguage) }

    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val offlineModels by viewModel.offlineModels.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showOfflineModelsSheet by rememberSaveable { mutableStateOf(false) }
    var showPrivacySheet by rememberSaveable { mutableStateOf(false) }
    var showSupportSheet by rememberSaveable { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/epub+zip")
    ) { targetUri ->
        val fileToSave = pendingSaveFile
        pendingSaveFile = null
        if (targetUri != null && fileToSave != null) {
            viewModel.saveOutputToUri(fileToSave, targetUri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    GoEpubTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GoEpubBrandLogo(size = 34.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "GoEpub",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.appTagline,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // Bilingual App Language Toggle (EN default | ID)
                        Surface(
                            onClick = viewModel::toggleAppLanguage,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .semantics { contentDescription = strings.switchLangTooltip }
                        ) {
                            Text(
                                text = if (appLanguage == AppLanguage.EN) "EN · id" else "en · ID",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        // Support / Donate Developer Button
                        IconButton(onClick = { showSupportSheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = strings.supportTooltip,
                                tint = Color(0xFFE11D48)
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.refreshDownloadedModels()
                                showOfflineModelsSheet = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = strings.manageModelsTooltip,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showPrivacySheet = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = strings.privacyTooltip,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
                },
                contentKey = { it::class.simpleName },
                label = "main_state_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { state ->
                when (state) {
                    is UiState.Idle -> IdleContent(
                        strings = strings,
                        history = history,
                        downloadedCount = offlineModels.downloadedCodes.size,
                        onPickFile = onPickFile,
                        onOpenManageModels = {
                            viewModel.refreshDownloadedModels()
                            showOfflineModelsSheet = true
                        },
                        onOpenSupport = { showSupportSheet = true },
                        onSelectHistory = viewModel::openHistoryRecord,
                        onDeleteHistory = viewModel::deleteHistoryItem
                    )

                    is UiState.InspectingFile -> InspectingFileContent(
                        strings = strings,
                        fileName = state.fileName
                    )

                    is UiState.Configuring -> ConfiguringContent(
                        strings = strings,
                        state = state,
                        languageOptions = viewModel.languageOptions,
                        downloadedCodes = offlineModels.downloadedCodes,
                        onChangeFile = onPickFile,
                        onCancel = viewModel::reset,
                        onStartTranslation = { src, tgt, allowCellular ->
                            onRequestNotificationPermissionIfNeeded()
                            viewModel.startTranslation(src, tgt, allowCellular)
                        }
                    )

                    is UiState.DownloadingModel -> DownloadingModelContent(
                        strings = strings,
                        state = state,
                        onCancel = viewModel::cancelActiveTranslation
                    )

                    is UiState.Translating -> TranslatingContent(
                        strings = strings,
                        state = state,
                        onCancel = viewModel::cancelActiveTranslation
                    )

                    is UiState.Success -> SuccessContent(
                        strings = strings,
                        state = state,
                        onSaveToDevice = {
                            pendingSaveFile = state.outputFile
                            saveDocumentLauncher.launch(state.fileName)
                        },
                        onTranslateAnother = viewModel::reset,
                        onShowMessage = viewModel::showMessage
                    )

                    is UiState.Error -> ErrorContent(
                        strings = strings,
                        state = state,
                        onRetry = viewModel::retryLastTranslation,
                        onPickAnotherFile = {
                            viewModel.reset()
                            onPickFile()
                        },
                        onBackHome = viewModel::reset
                    )
                }
            }
        }

        if (showOfflineModelsSheet) {
            OfflineModelsBottomSheet(
                strings = strings,
                languageOptions = viewModel.languageOptions,
                modelsState = offlineModels,
                onDownload = viewModel::downloadOfflineModel,
                onDelete = viewModel::deleteOfflineModel,
                onDismiss = { showOfflineModelsSheet = false }
            )
        }

        if (showPrivacySheet) {
            PrivacyAndAboutBottomSheet(
                strings = strings,
                hasHistory = history.isNotEmpty(),
                onOpenSupport = { showSupportSheet = true },
                onClearHistory = viewModel::clearAllHistory,
                onDismiss = { showPrivacySheet = false }
            )
        }

        if (showSupportSheet) {
            SupportDeveloperBottomSheet(
                strings = strings,
                onBrowserError = { viewModel.showMessage(strings.noBrowserMessage) },
                onDismiss = { showSupportSheet = false }
            )
        }
    }
}

@Composable
private fun IdleContent(
    strings: AppStrings,
    history: List<TranslatedBookRecord>,
    downloadedCount: Int,
    onPickFile: () -> Unit,
    onOpenManageModels: () -> Unit,
    onOpenSupport: () -> Unit,
    onSelectHistory: (TranslatedBookRecord) -> Unit,
    onDeleteHistory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CleanSurfaceCard(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeroBookIllustration()
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.heroTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.heroSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.pickEpubButton,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    onClick = onOpenManageModels,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OfflineBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (downloadedCount > 0) {
                                strings.offlinePacksActiveFormat(downloadedCount)
                            } else {
                                strings.freeUnlimitedTag
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.GppGood,
                title = strings.featurePrivateTitle,
                subtitle = strings.featurePrivateSubtitle
            )
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = strings.featureLayoutTitle,
                subtitle = strings.featureLayoutSubtitle
            )
            FeatureHighlightCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.AutoAwesome,
                title = strings.featureBackgroundTitle,
                subtitle = strings.featureBackgroundSubtitle
            )
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.recentTranslationsTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            history.forEach { item ->
                HistoryItemCard(
                    strings = strings,
                    record = item,
                    onClick = { onSelectHistory(item) },
                    onDelete = { onDeleteHistory(item.id) }
                )
            }
        }

        SupportDeveloperBannerCard(
            strings = strings,
            onClick = onOpenSupport
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FeatureHighlightCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    strings: AppStrings,
    record: TranslatedBookRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.sourceLanguage.uppercase()} → ${record.targetLanguage.uppercase()} • ${record.chapterCount} ${strings.chaptersWord} • ${formatFileSize(record.fileSizeBytes, strings.language)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatShortDate(record.completedAtMillis, strings.language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = strings.deleteHistoryDesc(record.title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InspectingFileContent(
    strings: AppStrings,
    fileName: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        CleanSurfaceCard(
            contentPadding = PaddingValues(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.5.dp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = strings.inspectingTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConfiguringContent(
    strings: AppStrings,
    state: UiState.Configuring,
    languageOptions: List<GoogleTranslateManager.Companion.LanguageOption>,
    downloadedCodes: Set<String>,
    onChangeFile: () -> Unit,
    onCancel: () -> Unit,
    onStartTranslation: (sourceLang: String?, targetLang: String, allowCellular: Boolean) -> Unit
) {
    var sourceLanguage by rememberSaveable(state.sourceUri.toString()) {
        mutableStateOf<String?>(null) // Clean default: Auto-Detect
    }
    var targetLanguage by rememberSaveable(state.sourceUri.toString()) {
        val defaultTarget = when {
            state.suggestedSourceLanguage == "en" -> "id"
            state.suggestedSourceLanguage == "id" -> "en"
            strings.language == AppLanguage.ID -> "id"
            else -> "en"
        }
        mutableStateOf(defaultTarget)
    }
    var allowCellular by rememberSaveable { mutableStateOf(true) }
    var activePickerTarget by remember { mutableStateOf<PickerRole?>(null) }

    val coverBitmap by produceState<ImageBitmap?>(initialValue = null, state.bookInfo.coverImageBytes) {
        val bytes = state.bookInfo.coverImageBytes ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val effectiveSourceHint = sourceLanguage ?: state.suggestedSourceLanguage
    val needsModelDownload = remember(effectiveSourceHint, targetLanguage, downloadedCodes) {
        val targetMissing = targetLanguage !in downloadedCodes
        val sourceMissing = effectiveSourceHint != null && effectiveSourceHint !in downloadedCodes
        targetMissing || sourceMissing
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CleanSurfaceCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverThumbnail(coverBitmap = coverBitmap, title = state.bookInfo.title)
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = strings.readyBadge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.bookInfo.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!state.bookInfo.author.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.bookInfo.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.bookInfo.chapterCount} ${strings.chaptersWord} • ${formatFileSize(state.bookInfo.fileSizeBytes, strings.language)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onChangeFile) {
                    Text(strings.changeFileButton)
                }
            }
        }

        CleanSurfaceCard(contentPadding = PaddingValues(20.dp)) {
            Text(
                text = strings.translationSettingsTitle,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.translationSettingsSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LanguageSelectorField(
                strings = strings,
                label = strings.sourceLanguageLabel,
                selectedCode = sourceLanguage,
                suggestedHintCode = state.suggestedSourceLanguage,
                downloadedCodes = downloadedCodes,
                onClick = { activePickerTarget = PickerRole.SOURCE }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val canSwap = sourceLanguage != null
                Surface(
                    onClick = {
                        val currentSrc = sourceLanguage
                        if (currentSrc != null) {
                            sourceLanguage = targetLanguage
                            targetLanguage = currentSrc
                        }
                    },
                    enabled = canSwap,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = strings.swapLanguagesDesc,
                        tint = if (canSwap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }

            LanguageSelectorField(
                strings = strings,
                label = strings.targetLanguageLabel,
                selectedCode = targetLanguage,
                suggestedHintCode = null,
                downloadedCodes = downloadedCodes,
                onClick = { activePickerTarget = PickerRole.TARGET }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.cellularDownloadTitle,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (needsModelDownload) {
                            strings.cellularNeedsDownloadHint
                        } else {
                            strings.cellularAlreadyReadyHint
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needsModelDownload) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = allowCellular,
                    onCheckedChange = { allowCellular = it }
                )
            }
        }

        val sameLanguageSelected = sourceLanguage != null && sourceLanguage == targetLanguage

        Button(
            onClick = { onStartTranslation(sourceLanguage, targetLanguage, allowCellular) },
            enabled = !sameLanguageSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = strings.startTranslateButton,
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (sameLanguageSelected) {
            Text(
                text = strings.sameLanguageWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(strings.backToHomeButton)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    activePickerTarget?.let { role ->
        LanguagePickerBottomSheet(
            strings = strings,
            title = if (role == PickerRole.SOURCE) strings.pickSourceLanguageTitle else strings.pickTargetLanguageTitle,
            allowAutoDetect = role == PickerRole.SOURCE,
            suggestedHintCode = state.suggestedSourceLanguage,
            selectedCode = if (role == PickerRole.SOURCE) sourceLanguage else targetLanguage,
            options = languageOptions,
            downloadedCodes = downloadedCodes,
            onSelect = { chosen ->
                if (role == PickerRole.SOURCE) {
                    sourceLanguage = chosen
                } else if (chosen != null) {
                    targetLanguage = chosen
                }
                activePickerTarget = null
            },
            onDismiss = { activePickerTarget = null }
        )
    }
}

private enum class PickerRole { SOURCE, TARGET }

@Composable
private fun BookCoverThumbnail(
    coverBitmap: ImageBitmap?,
    title: String
) {
    if (coverBitmap != null) {
        Image(
            bitmap = coverBitmap,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(74.dp)
                .height(106.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(106.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0EA5E9), Color(0xFF0D9488))
                    )
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title.take(18),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectorField(
    strings: AppStrings,
    label: String,
    selectedCode: String?,
    suggestedHintCode: String?,
    downloadedCodes: Set<String>,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeText = selectedCode?.uppercase() ?: "AUTO"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedCode == null) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCode == null) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = GoogleTranslateManager.displayNameFor(selectedCode, strings.language),
                        style = MaterialTheme.typography.titleSmall
                    )
                    val subtitle = when {
                        selectedCode == null && suggestedHintCode != null ->
                            strings.detectedFromMetadataFormat(
                                GoogleTranslateManager.displayNameFor(suggestedHintCode, strings.language)
                            )
                        selectedCode == null -> strings.autoRecognizedSubtitle
                        selectedCode in downloadedCodes -> strings.readyOfflineSubtitle
                        else -> strings.willDownloadSubtitle
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerBottomSheet(
    strings: AppStrings,
    title: String,
    allowAutoDetect: Boolean,
    suggestedHintCode: String?,
    selectedCode: String?,
    options: List<GoogleTranslateManager.Companion.LanguageOption>,
    downloadedCodes: Set<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(searchQuery, options) {
        if (searchQuery.isBlank()) options
        else options.filter {
            it.englishName.contains(searchQuery, ignoreCase = true) ||
                it.indonesianName.contains(searchQuery, ignoreCase = true) ||
                it.nativeName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchLanguagePlaceholder) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (allowAutoDetect && searchQuery.isBlank()) {
                    item(key = "auto_detect") {
                        val isSelected = selectedCode == null
                        LanguageOptionRow(
                            badge = "AUTO",
                            title = strings.autoDetectLabel,
                            subtitle = if (suggestedHintCode != null) {
                                strings.recommendedAutoFormat(
                                    GoogleTranslateManager.displayNameFor(suggestedHintCode, strings.language)
                                )
                            } else {
                                strings.autoDetectSheetSubtitle
                            },
                            readyOfflineLabel = strings.readyOfflineShort,
                            isDownloaded = false,
                            isSelected = isSelected,
                            onClick = { onSelect(null) }
                        )
                    }
                }

                items(filtered, key = { it.code }) { option ->
                    LanguageOptionRow(
                        badge = option.badge,
                        title = option.nameFor(strings.language),
                        subtitle = option.nativeName,
                        readyOfflineLabel = strings.readyOfflineShort,
                        isDownloaded = option.code in downloadedCodes,
                        isSelected = selectedCode == option.code,
                        onClick = { onSelect(option.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    badge: String,
    title: String,
    subtitle: String,
    readyOfflineLabel: String,
    isDownloaded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        border = if (isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (isDownloaded) "$subtitle • $readyOfflineLabel" else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDownloaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DownloadingModelContent(
    strings: AppStrings,
    state: UiState.DownloadingModel,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CleanSurfaceCard(contentPadding = PaddingValues(28.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = strings.preparingLanguagePackTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.languagePairLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.downloadingModelBodyFormat(state.bookTitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.cancelButton)
                }
            }
        }
    }
}

@Composable
private fun TranslatingContent(
    strings: AppStrings,
    state: UiState.Translating,
    onCancel: () -> Unit
) {
    var askConfirmCancel by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = (state.percent.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 450),
        label = "chapter_progress_anim"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CleanSurfaceCard(contentPadding = PaddingValues(26.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 8.dp
                    )
                    if (state.percent > 0) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            strokeCap = StrokeCap.Round
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            strokeCap = StrokeCap.Round
                        )
                    }
                    Text(
                        text = "${state.percent}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = state.bookTitle,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (state.totalChapters > 0 && state.chapterIndex > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = strings.chapterProgressFormat(state.chapterIndex, state.totalChapters),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (state.chapterTitle.isNotBlank()) {
                                strings.currentlyTranslatingFormat(state.chapterTitle)
                            } else {
                                state.statusText
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.backgroundSafeHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = { askConfirmCancel = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(strings.cancelProcessButton)
                }
            }
        }
    }

    if (askConfirmCancel) {
        AlertDialog(
            onDismissRequest = { askConfirmCancel = false },
            title = { Text(strings.cancelDialogTitle) },
            text = { Text(strings.cancelDialogBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askConfirmCancel = false
                        onCancel()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(strings.confirmCancelButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { askConfirmCancel = false }) {
                    Text(strings.continueProcessButton)
                }
            }
        )
    }
}

@Composable
private fun SuccessContent(
    strings: AppStrings,
    state: UiState.Success,
    onSaveToDevice: () -> Unit,
    onTranslateAnother: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CleanSurfaceCard(contentPadding = PaddingValues(26.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SuccessGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = strings.successTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.successSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!state.bookAuthor.isNullOrBlank()) {
                            Text(
                                text = state.bookAuthor,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${GoogleTranslateManager.displayNameFor(state.sourceLanguage, strings.language)} → ${GoogleTranslateManager.displayNameFor(state.targetLanguage, strings.language)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${state.fileName} • ${state.chapterCount} ${strings.chaptersWord} • ${formatFileSize(state.fileSizeBytes, strings.language)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onSaveToDevice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.saveToDeviceButton,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/epub+zip"
                                putExtra(Intent.EXTRA_STREAM, state.shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, strings.shareChooserTitle)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.shareButton)
                    }

                    OutlinedButton(
                        onClick = {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(state.shareUri, "application/epub+zip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(openIntent)
                            } catch (_: ActivityNotFoundException) {
                                onShowMessage(strings.noEpubReaderMessage)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.openBookButton)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(onClick = onTranslateAnother) {
                    Text(strings.translateAnotherButton)
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    strings: AppStrings,
    state: UiState.Error,
    onRetry: () -> Unit,
    onPickAnotherFile: () -> Unit,
    onBackHome: () -> Unit
) {
    val icon = when (state.kind) {
        EpubErrorKind.DRM_PROTECTED.name -> Icons.Outlined.Lock
        EpubErrorKind.CORRUPTED_ARCHIVE.name, EpubErrorKind.INVALID_STRUCTURE.name -> Icons.Outlined.BrokenImage
        "TRANSLATION_ERROR" -> Icons.Outlined.WifiOff
        else -> Icons.Outlined.ErrorOutline
    }

    val title = when (state.kind) {
        EpubErrorKind.DRM_PROTECTED.name -> strings.errorTitleDrm
        EpubErrorKind.CORRUPTED_ARCHIVE.name -> strings.errorTitleCorrupt
        EpubErrorKind.INVALID_STRUCTURE.name -> strings.errorTitleInvalid
        EpubErrorKind.EMPTY_CONTENT.name -> strings.errorTitleEmpty
        else -> strings.errorTitleStopped
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        CleanSurfaceCard(contentPadding = PaddingValues(26.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                        .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.retryButton)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onPickAnotherFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.pickAnotherEpubButton)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onBackHome) {
                    Text(strings.backToHomeButton)
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long, lang: AppLanguage): String {
    if (bytes <= 0L) return "0 KB"
    val locale = if (lang == AppLanguage.ID) Locale("id", "ID") else Locale.US
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(locale, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(locale, "%.1f MB", mb)
}

private fun formatShortDate(millis: Long, lang: AppLanguage): String {
    if (millis <= 0L) return ""
    val locale = if (lang == AppLanguage.ID) Locale("id", "ID") else Locale.US
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", locale)
    return formatter.format(Date(millis))
}
