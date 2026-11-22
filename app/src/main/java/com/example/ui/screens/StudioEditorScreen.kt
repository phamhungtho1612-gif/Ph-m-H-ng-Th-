package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.AppThemeMode
import com.example.imaging.PhotoboothFilters
import com.example.model.BoothLayout
import com.example.model.EditorTab
import com.example.model.FrameStyle
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import com.example.model.PhotoFilter
import com.example.model.PhotoboothUiState
import com.example.model.SaveState
import com.example.ui.components.CreativeTextPanel
import com.example.ui.components.PhotoboothCollagePreview
import com.example.ui.components.ThemeToggleButton
import com.example.ui.components.frameBackground
import com.example.ui.theme.BeVietnamProFontFamily
import com.example.ui.theme.StudioMintSuccess
import com.example.ui.theme.studioAccentInk
import com.example.viewmodel.PhotoboothViewModel
import com.example.viewmodel.PublishState

@Composable
fun StudioEditorScreen(
    uiState: PhotoboothUiState,
    themeMode: AppThemeMode,
    publishState: PublishState,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onSelectFrame: (FrameStyle) -> Unit,
    onSelectFilter: (PhotoFilter) -> Unit,
    onUpdateCaption: (String) -> Unit,
    onToggleShowDate: (Boolean) -> Unit,
    onToggleLayout: (BoothLayout) -> Unit,
    onScanSubjectWithAi: () -> Unit,
    onUpdateCreativeText: (String) -> Unit,
    onSelectOverlayShape: (OverlayTextShape) -> Unit,
    onSelectOverlayStyle: (OverlayTextStyle) -> Unit,
    onSelectOverlayFont: (OverlayFontStyle) -> Unit,
    onSelectOverlayColor: (String) -> Unit,
    onToggleBehindSubject: (Boolean) -> Unit,
    onToggleRepeatAlongShape: (Boolean) -> Unit,
    onUpdateCreativeTextScale: (Float) -> Unit,
    onUpdateCreativeTextLetterSpacing: (Float) -> Unit,
    onUpdateCreativeTextLineSpacing: (Float) -> Unit,
    onUpdateCreativeTextPositionY: (Float) -> Unit,
    onUpdateCreativeTextPositionX: (Float) -> Unit,
    onUpdateCreativeTextBend: (Float) -> Unit,
    onUpdateCreativeTextOpacity: (Float) -> Unit,
    onSelectTargetSlot: (Int) -> Unit,
    onResetCreativeTextTransform: () -> Unit,
    onClearAiError: () -> Unit,
    onEditPhotoClick: (Int) -> Unit,
    onRetake: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDismissSaveState: () -> Unit,
    onPublishToFriends: () -> Unit,
    onViewCircleFeed: () -> Unit,
    onResetPublishState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRetakeDialog by remember { mutableStateOf(false) }
    var showCropSlotChooser by remember { mutableStateOf(false) }

    BackHandler {
        showRetakeDialog = true
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSaveToGallery()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.storage_permission_needed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val handleSaveClick = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onSaveToGallery()
            } else {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            onSaveToGallery()
        }
    }

    val accentInk = studioAccentInk()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            val isWideScreen = maxWidth >= 700.dp

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        EditorTopBar(
                            selectedLayout = uiState.selectedLayout,
                            photoCount = uiState.capturedPhotos.size,
                            themeMode = themeMode,
                            onSelectThemeMode = onSelectThemeMode,
                            onRetakeClick = { showRetakeDialog = true },
                            onEditCropClick = {
                                if (uiState.capturedPhotos.size <= 1) {
                                    onEditPhotoClick(0)
                                } else {
                                    showCropSlotChooser = true
                                }
                            },
                            onToggleLayout = onToggleLayout
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            PhotoboothCollagePreview(
                                photos = uiState.capturedPhotos,
                                layout = uiState.selectedLayout,
                                frameStyle = uiState.selectedFrame,
                                filter = uiState.selectedFilter,
                                captionText = uiState.captionText,
                                showDateStamp = uiState.showDateStamp,
                                formattedDate = uiState.formattedDate,
                                creativeText = uiState.creativeText,
                                subjectMasks = uiState.subjectAi.subjectMasks,
                                isScanningSubject = uiState.subjectAi.isScanning,
                                showSlotSelectionRing = uiState.activeEditorTab == EditorTab.AI_TEXT,
                                onSelectSlot = { slotIndex ->
                                    onSelectTargetSlot(slotIndex)
                                    onSelectTab(EditorTab.AI_TEXT)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .weight(0.9f)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp
                    ) {
                        EditorControlDeck(
                            uiState = uiState,
                            publishState = publishState,
                            onSelectTab = onSelectTab,
                            onSelectFrame = onSelectFrame,
                            onSelectFilter = onSelectFilter,
                            onUpdateCaption = onUpdateCaption,
                            onToggleShowDate = onToggleShowDate,
                            onScanSubjectWithAi = onScanSubjectWithAi,
                            onUpdateCreativeText = onUpdateCreativeText,
                            onSelectOverlayShape = onSelectOverlayShape,
                            onSelectOverlayStyle = onSelectOverlayStyle,
                            onSelectOverlayFont = onSelectOverlayFont,
                            onSelectOverlayColor = onSelectOverlayColor,
                            onToggleBehindSubject = onToggleBehindSubject,
                            onToggleRepeatAlongShape = onToggleRepeatAlongShape,
                            onUpdateCreativeTextScale = onUpdateCreativeTextScale,
                            onUpdateCreativeTextLetterSpacing = onUpdateCreativeTextLetterSpacing,
                            onUpdateCreativeTextLineSpacing = onUpdateCreativeTextLineSpacing,
                            onUpdateCreativeTextPositionY = onUpdateCreativeTextPositionY,
                            onUpdateCreativeTextPositionX = onUpdateCreativeTextPositionX,
                            onUpdateCreativeTextBend = onUpdateCreativeTextBend,
                            onUpdateCreativeTextOpacity = onUpdateCreativeTextOpacity,
                            onSelectTargetSlot = onSelectTargetSlot,
                            onResetCreativeTextTransform = onResetCreativeTextTransform,
                            onClearAiError = onClearAiError,
                            onSaveClick = handleSaveClick,
                            onPublishClick = onPublishToFriends,
                            onRetake = onRetake,
                            onDismissSaveState = onDismissSaveState,
                            onViewCircleFeed = onViewCircleFeed,
                            onResetPublishState = onResetPublishState,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    EditorTopBar(
                        selectedLayout = uiState.selectedLayout,
                        photoCount = uiState.capturedPhotos.size,
                        themeMode = themeMode,
                        onSelectThemeMode = onSelectThemeMode,
                        onRetakeClick = { showRetakeDialog = true },
                        onEditCropClick = {
                            if (uiState.capturedPhotos.size <= 1) {
                                onEditPhotoClick(0)
                            } else {
                                showCropSlotChooser = true
                            }
                        },
                        onToggleLayout = onToggleLayout,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Live Collage Preview Stage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PhotoboothCollagePreview(
                            photos = uiState.capturedPhotos,
                            layout = uiState.selectedLayout,
                            frameStyle = uiState.selectedFrame,
                            filter = uiState.selectedFilter,
                            captionText = uiState.captionText,
                            showDateStamp = uiState.showDateStamp,
                            formattedDate = uiState.formattedDate,
                            creativeText = uiState.creativeText,
                            subjectMasks = uiState.subjectAi.subjectMasks,
                            isScanningSubject = uiState.subjectAi.isScanning,
                            showSlotSelectionRing = uiState.activeEditorTab == EditorTab.AI_TEXT,
                            onSelectSlot = { slotIndex ->
                                onSelectTargetSlot(slotIndex)
                                onSelectTab(EditorTab.AI_TEXT)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom One-Handed Customization Deck
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shadowElevation = 14.dp
                    ) {
                        EditorControlDeck(
                            uiState = uiState,
                            publishState = publishState,
                            onSelectTab = onSelectTab,
                            onSelectFrame = onSelectFrame,
                            onSelectFilter = onSelectFilter,
                            onUpdateCaption = onUpdateCaption,
                            onToggleShowDate = onToggleShowDate,
                            onScanSubjectWithAi = onScanSubjectWithAi,
                            onUpdateCreativeText = onUpdateCreativeText,
                            onSelectOverlayShape = onSelectOverlayShape,
                            onSelectOverlayStyle = onSelectOverlayStyle,
                            onSelectOverlayFont = onSelectOverlayFont,
                            onSelectOverlayColor = onSelectOverlayColor,
                            onToggleBehindSubject = onToggleBehindSubject,
                            onToggleRepeatAlongShape = onToggleRepeatAlongShape,
                            onUpdateCreativeTextScale = onUpdateCreativeTextScale,
                            onUpdateCreativeTextLetterSpacing = onUpdateCreativeTextLetterSpacing,
                            onUpdateCreativeTextLineSpacing = onUpdateCreativeTextLineSpacing,
                            onUpdateCreativeTextPositionY = onUpdateCreativeTextPositionY,
                            onUpdateCreativeTextPositionX = onUpdateCreativeTextPositionX,
                            onUpdateCreativeTextBend = onUpdateCreativeTextBend,
                            onUpdateCreativeTextOpacity = onUpdateCreativeTextOpacity,
                            onSelectTargetSlot = onSelectTargetSlot,
                            onResetCreativeTextTransform = onResetCreativeTextTransform,
                            onClearAiError = onClearAiError,
                            onSaveClick = handleSaveClick,
                            onPublishClick = onPublishToFriends,
                            onRetake = onRetake,
                            onDismissSaveState = onDismissSaveState,
                            onViewCircleFeed = onViewCircleFeed,
                            onResetPublishState = onResetPublishState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        )
                    }
                }
            }
        }
    }

    if (showRetakeDialog) {
        AlertDialog(
            onDismissRequest = { showRetakeDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.retake_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.retake_confirm_message),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRetakeDialog = false
                        onRetake()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetakeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    if (showCropSlotChooser && uiState.capturedPhotos.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCropSlotChooser = false },
            title = {
                Text(
                    text = stringResource(R.string.crop_editor_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.crop_slot_choose_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.capturedPhotos.size) { index ->
                            val bmp = uiState.capturedPhotos[index]
                            val thumb = remember(bmp) { bmp.asImageBitmap() }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        showCropSlotChooser = false
                                        onEditPhotoClick(index)
                                    }
                                    .padding(4.dp)
                                    .testTag("crop_slot_$index")
                            ) {
                                Image(
                                    bitmap = thumb,
                                    contentDescription = stringResource(R.string.ai_slot_item, index + 1),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.ai_slot_item, index + 1),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCropSlotChooser = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EditorTopBar(
    selectedLayout: BoothLayout,
    photoCount: Int,
    themeMode: AppThemeMode,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onRetakeClick: () -> Unit,
    onEditCropClick: () -> Unit,
    onToggleLayout: (BoothLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    val availableLayouts = remember(photoCount) {
        BoothLayout.entries.filter { it.shotCount <= photoCount }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onRetakeClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("retake_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = stringResource(R.string.retake_photos),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            if (photoCount > 0) {
                Surface(
                    onClick = onEditCropClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("editor_crop_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Crop,
                            contentDescription = stringResource(R.string.edit_crop_photo_btn),
                            tint = studioAccentInk(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.edit_crop_photo_btn),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = studioAccentInk(),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (availableLayouts.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("editor_layout_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        availableLayouts.forEach { layout ->
                            val active = layout == selectedLayout
                            val icon = when (layout) {
                                BoothLayout.SINGLE_PHOTO -> Icons.Rounded.CropPortrait
                                BoothLayout.PHOTO_STRIP -> Icons.Rounded.ViewStream
                                BoothLayout.GRID_2X2 -> Icons.Rounded.GridView
                            }
                            Surface(
                                onClick = { onToggleLayout(layout) },
                                shape = CircleShape,
                                color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = stringResource(layout.labelRes),
                                        tint = if (active) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = when (layout) {
                                            BoothLayout.SINGLE_PHOTO -> "1 tấm"
                                            BoothLayout.PHOTO_STRIP -> "Dải"
                                            BoothLayout.GRID_2X2 -> "2×2"
                                        },
                                        fontFamily = BeVietnamProFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = if (active) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ThemeToggleButton(
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode
            )
        }
    }
}

@Composable
private fun EditorControlDeck(
    uiState: PhotoboothUiState,
    publishState: PublishState,
    onSelectTab: (EditorTab) -> Unit,
    onSelectFrame: (FrameStyle) -> Unit,
    onSelectFilter: (PhotoFilter) -> Unit,
    onUpdateCaption: (String) -> Unit,
    onToggleShowDate: (Boolean) -> Unit,
    onScanSubjectWithAi: () -> Unit,
    onUpdateCreativeText: (String) -> Unit,
    onSelectOverlayShape: (OverlayTextShape) -> Unit,
    onSelectOverlayStyle: (OverlayTextStyle) -> Unit,
    onSelectOverlayFont: (OverlayFontStyle) -> Unit,
    onSelectOverlayColor: (String) -> Unit,
    onToggleBehindSubject: (Boolean) -> Unit,
    onToggleRepeatAlongShape: (Boolean) -> Unit,
    onUpdateCreativeTextScale: (Float) -> Unit,
    onUpdateCreativeTextLetterSpacing: (Float) -> Unit,
    onUpdateCreativeTextLineSpacing: (Float) -> Unit,
    onUpdateCreativeTextPositionY: (Float) -> Unit,
    onUpdateCreativeTextPositionX: (Float) -> Unit,
    onUpdateCreativeTextBend: (Float) -> Unit,
    onUpdateCreativeTextOpacity: (Float) -> Unit,
    onSelectTargetSlot: (Int) -> Unit,
    onResetCreativeTextTransform: () -> Unit,
    onClearAiError: () -> Unit,
    onSaveClick: () -> Unit,
    onPublishClick: () -> Unit,
    onRetake: () -> Unit,
    onDismissSaveState: () -> Unit,
    onViewCircleFeed: () -> Unit,
    onResetPublishState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentInk = studioAccentInk()

    Column(
        modifier = modifier.padding(top = 12.dp, bottom = 14.dp)
    ) {
        // 4 Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EditorTabItem(
                tab = EditorTab.FRAME,
                icon = Icons.Outlined.Palette,
                selected = uiState.activeEditorTab == EditorTab.FRAME,
                onClick = { onSelectTab(EditorTab.FRAME) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_frame")
            )
            EditorTabItem(
                tab = EditorTab.FILTER,
                icon = Icons.Outlined.AutoAwesome,
                selected = uiState.activeEditorTab == EditorTab.FILTER,
                onClick = { onSelectTab(EditorTab.FILTER) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_filter")
            )
            EditorTabItem(
                tab = EditorTab.AI_TEXT,
                icon = Icons.Outlined.AutoAwesome,
                selected = uiState.activeEditorTab == EditorTab.AI_TEXT,
                onClick = { onSelectTab(EditorTab.AI_TEXT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_ai_text")
            )
            EditorTabItem(
                tab = EditorTab.CAPTION,
                icon = Icons.Outlined.EditNote,
                selected = uiState.activeEditorTab == EditorTab.CAPTION,
                onClick = { onSelectTab(EditorTab.CAPTION) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_caption")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Tab Panel
        AnimatedContent(
            targetState = uiState.activeEditorTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "editor_tab_content"
        ) { tab ->
            when (tab) {
                EditorTab.FRAME -> FramePickerRow(
                    selectedFrame = uiState.selectedFrame,
                    onSelectFrame = onSelectFrame
                )

                EditorTab.FILTER -> FilterPickerRow(
                    samplePhoto = uiState.capturedPhotos.firstOrNull(),
                    selectedFilter = uiState.selectedFilter,
                    onSelectFilter = onSelectFilter
                )

                EditorTab.AI_TEXT -> CreativeTextPanel(
                    layout = uiState.selectedLayout,
                    photoCount = uiState.capturedPhotos.size,
                    creativeText = uiState.creativeText,
                    subjectAi = uiState.subjectAi,
                    onScanSubjectWithAi = onScanSubjectWithAi,
                    onUpdateText = onUpdateCreativeText,
                    onSelectShape = onSelectOverlayShape,
                    onSelectStyle = onSelectOverlayStyle,
                    onSelectFont = onSelectOverlayFont,
                    onSelectColor = onSelectOverlayColor,
                    onToggleBehindSubject = onToggleBehindSubject,
                    onToggleRepeatAlongShape = onToggleRepeatAlongShape,
                    onUpdateScale = onUpdateCreativeTextScale,
                    onUpdateLetterSpacing = onUpdateCreativeTextLetterSpacing,
                    onUpdateLineSpacing = onUpdateCreativeTextLineSpacing,
                    onUpdatePositionY = onUpdateCreativeTextPositionY,
                    onUpdatePositionX = onUpdateCreativeTextPositionX,
                    onUpdateBend = onUpdateCreativeTextBend,
                    onUpdateOpacity = onUpdateCreativeTextOpacity,
                    onSelectTargetSlot = onSelectTargetSlot,
                    onResetTransform = onResetCreativeTextTransform,
                    onClearAiError = onClearAiError
                )

                EditorTab.CAPTION -> CaptionEditorPanel(
                    captionText = uiState.captionText,
                    showDateStamp = uiState.showDateStamp,
                    onUpdateCaption = onUpdateCaption,
                    onToggleShowDate = onToggleShowDate
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save Feedback Banner
        AnimatedVisibility(
            visible = uiState.saveState is SaveState.Success || uiState.saveState is SaveState.Error,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            when (val state = uiState.saveState) {
                is SaveState.Success -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = StudioMintSuccess.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StudioMintSuccess.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = StudioMintSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.saved_success_message),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(state.uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(viewIntent)
                                        }
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.view_in_gallery),
                                        fontSize = 12.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = onRetake,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.new_session),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                is SaveState.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.saved_error_message, state.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onDismissSaveState,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        // Publish Feedback Banner
        AnimatedVisibility(
            visible = publishState is PublishState.Success || publishState is PublishState.Error,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            when (publishState) {
                is PublishState.Success -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = StudioMintSuccess.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StudioMintSuccess.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = StudioMintSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.published_to_circle_success),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                onResetPublishState()
                                onViewCircleFeed()
                            }) {
                                Text(
                                    text = stringResource(R.string.view_circle_feed),
                                    color = accentInk,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                is PublishState.Error -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = publishState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = onResetPublishState,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        // Dual Action Row: Save Locally + Publish to Friends Circle
        val isSaving = uiState.saveState is SaveState.Saving
        val isPublishing = publishState is PublishState.Publishing

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSaveClick,
                enabled = !isSaving,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("save_to_gallery_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = accentInk,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.save_to_gallery),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onPublishClick,
                enabled = !isPublishing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1.15f)
                    .height(52.dp)
                    .testTag("publish_to_circle_button")
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.publishing_to_circle),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.publish_to_circle),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorTabItem(
    tab: EditorTab,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fgColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(tab.labelRes),
                fontFamily = BeVietnamProFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FramePickerRow(
    selectedFrame: FrameStyle,
    onSelectFrame: (FrameStyle) -> Unit
) {
    val accentInk = studioAccentInk()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(FrameStyle.entries, key = { it.name }) { style ->
            val isSelected = style == selectedFrame
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectFrame(style) }
                    .padding(vertical = 4.dp)
                    .testTag("frame_${style.name.lowercase()}")
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .frameBackground(style)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(style.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) accentInk else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FilterPickerRow(
    samplePhoto: Bitmap?,
    selectedFilter: PhotoFilter,
    onSelectFilter: (PhotoFilter) -> Unit
) {
    val sampleImageBitmap = remember(samplePhoto) { samplePhoto?.asImageBitmap() }
    val accentInk = studioAccentInk()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(PhotoFilter.entries, key = { it.name }) { filter ->
            val isSelected = filter == selectedFilter
            val composeFilter = remember(filter) {
                PhotoboothFilters.toComposeColorFilter(filter)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectFilter(filter) }
                    .padding(vertical = 4.dp)
                    .testTag("filter_${filter.name.lowercase()}")
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 66.dp, height = 54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (sampleImageBitmap != null) {
                        Image(
                            bitmap = sampleImageBitmap,
                            contentDescription = stringResource(filter.labelRes),
                            contentScale = ContentScale.Crop,
                            colorFilter = composeFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(filter.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) accentInk else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CaptionEditorPanel(
    captionText: String,
    showDateStamp: Boolean,
    onUpdateCaption: (String) -> Unit,
    onToggleShowDate: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val quickSuggestions = remember {
        listOf("Thanh xuân ♡", "Bạn thân", "Cuối tuần vui", "Kỷ niệm đẹp")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = captionText,
            onValueChange = onUpdateCaption,
            label = { Text(stringResource(R.string.caption_label)) },
            placeholder = { Text(stringResource(R.string.caption_placeholder)) },
            singleLine = true,
            supportingText = {
                Text(
                    text = "${captionText.length}/${PhotoboothViewModel.MAX_CAPTION_LENGTH}",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            trailingIcon = {
                if (captionText.isNotEmpty()) {
                    IconButton(onClick = { onUpdateCaption("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_text)
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("caption_input")
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickSuggestions) { phrase ->
                val selected = captionText == phrase
                FilterChip(
                    selected = selected,
                    onClick = {
                        onUpdateCaption(if (selected) "" else phrase)
                        focusManager.clearFocus()
                    },
                    label = {
                        Text(
                            text = phrase,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onToggleShowDate(!showDateStamp) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.show_date_label),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = showDateStamp,
                onCheckedChange = onToggleShowDate,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("show_date_switch")
            )
        }
    }
}
