package com.goepub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goepub.app.BuildConfig
import com.goepub.app.domain.translator.GoogleTranslateManager
import com.goepub.app.ui.i18n.AppStrings
import com.goepub.app.ui.main.OfflineModelsUiState
import com.goepub.app.util.AppConstants

@Composable
fun CleanSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun GoEpubBrandLogo(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
    ) {
        val w = this.size.width
        val h = this.size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0EA5E9), Color(0xFF0D9488)),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.18f, h * 0.25f),
            size = Size(w * 0.30f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        drawRoundRect(
            color = Color(0xFFF0FDFA),
            topLeft = Offset(w * 0.52f, h * 0.25f),
            size = Size(w * 0.30f, h * 0.50f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        val lineStroke = h * 0.055f
        drawLine(
            color = Color(0xFF0D9488),
            start = Offset(w * 0.25f, h * 0.38f),
            end = Offset(w * 0.41f, h * 0.38f),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0x990D9488),
            start = Offset(w * 0.25f, h * 0.50f),
            end = Offset(w * 0.42f, h * 0.50f),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0x660D9488),
            start = Offset(w * 0.25f, h * 0.62f),
            end = Offset(w * 0.37f, h * 0.62f),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color(0xFFF59E0B),
            radius = w * 0.115f,
            center = Offset(w * 0.67f, h * 0.50f)
        )

        drawLine(
            color = Color.White,
            start = Offset(w * 0.62f, h * 0.50f),
            end = Offset(w * 0.72f, h * 0.50f),
            strokeWidth = h * 0.048f,
            cap = StrokeCap.Round
        )

        val cx = w * 0.78f
        val cy = h * 0.21f
        val r = w * 0.085f
        val ri = r * 0.35f
        val star = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + ri, cy - ri)
            lineTo(cx + r, cy)
            lineTo(cx + ri, cy + ri)
            lineTo(cx, cy + r)
            lineTo(cx - ri, cy + ri)
            lineTo(cx - r, cy)
            lineTo(cx - ri, cy - ri)
            close()
        }
        drawPath(star, color = Color(0xFFFDE047))
    }
}

@Composable
fun HeroBookIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        GoEpubBrandLogo(size = 58.dp)
    }
}

/**
 * Compact banner card displayed at the bottom of the home screen for supporting the developer.
 */
@Composable
fun SupportDeveloperBannerCard(
    strings: AppStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalCafe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.supportBannerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = strings.supportBannerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = Color(0xFFE11D48),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Bilingual Developer Support / Donation ModalBottomSheet featuring Saweria & Ko-fi links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportDeveloperBottomSheet(
    strings: AppStrings,
    onBrowserError: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = strings.supportSheetTitle,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.supportSheetBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Saweria (Indonesia - QRIS / E-Wallet)
            DonationOptionButton(
                icon = Icons.Outlined.QrCode2,
                accentColor = Color(0xFFD97706),
                accentContainerColor = Color(0xFFFEF3C7),
                title = strings.supportSaweriaTitle,
                subtitle = strings.supportSaweriaSubtitle,
                onClick = {
                    AppConstants.openExternalUrl(
                        context = context,
                        url = AppConstants.SAWERIA_URL,
                        onBrowserNotFound = onBrowserError
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Ko-fi (International - PayPal / Card)
            DonationOptionButton(
                icon = Icons.Outlined.Public,
                accentColor = Color(0xFF0284C7),
                accentContainerColor = Color(0xFFE0F2FE),
                title = strings.supportKofiTitle,
                subtitle = strings.supportKofiSubtitle,
                onClick = {
                    AppConstants.openExternalUrl(
                        context = context,
                        url = AppConstants.KOFI_URL,
                        onBrowserNotFound = onBrowserError
                    )
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = strings.supportThankYouNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DonationOptionButton(
    icon: ImageVector,
    accentColor: Color,
    accentContainerColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
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
                    .background(accentContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModelsBottomSheet(
    strings: AppStrings,
    languageOptions: List<GoogleTranslateManager.Companion.LanguageOption>,
    modelsState: OfflineModelsUiState,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = strings.offlineModelsTitle,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.offlineModelsSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languageOptions, key = { it.code }) { lang ->
                    val isDownloaded = lang.code in modelsState.downloadedCodes
                    val isBusy = modelsState.busyLanguageCode == lang.code
                    val localizedName = lang.nameFor(strings.language)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDownloaded) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ) {
                                Text(
                                    text = lang.badge,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDownloaded) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = localizedName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isDownloaded) {
                                        "${lang.nativeName} • ${strings.storedOfflineStatus}"
                                    } else {
                                        "${lang.nativeName} • ${strings.notDownloadedStatus}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDownloaded) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            when {
                                isBusy -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }

                                isDownloaded -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = strings.storedOfflineStatus,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(onClick = { onDelete(lang.code) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = strings.deletePackDesc(localizedName),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                else -> {
                                    TextButton(
                                        onClick = { onDownload(lang.code) },
                                        enabled = modelsState.busyLanguageCode == null
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.downloadAction)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndAboutBottomSheet(
    strings: AppStrings,
    hasHistory: Boolean,
    onOpenSupport: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoEpubBrandLogo(size = 42.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = strings.aboutVersionTitleFormat(BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = strings.aboutSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            PolicySectionItem(
                icon = Icons.Outlined.GppGood,
                title = strings.policyOnDeviceTitle,
                body = strings.policyOnDeviceBody
            )

            PolicySectionItem(
                icon = Icons.Outlined.Storage,
                title = strings.policyStorageTitle,
                body = strings.policyStorageBody
            )

            PolicySectionItem(
                icon = Icons.Outlined.VerifiedUser,
                title = strings.policyCopyrightTitle,
                body = strings.policyCopyrightBody
            )

            PolicySectionItem(
                icon = Icons.Outlined.Info,
                title = strings.policyNetworkTitle,
                body = strings.policyNetworkBody
            )

            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenSupport()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE11D48),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.supportInAboutButton)
            }

            if (hasHistory) {
                OutlinedButton(
                    onClick = {
                        onClearHistory()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.clearAllHistoryButton)
                }
            }
        }
    }
}

@Composable
private fun PolicySectionItem(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
