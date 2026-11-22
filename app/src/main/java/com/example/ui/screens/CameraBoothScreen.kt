package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.imaging.ImageProcessing
import com.example.imaging.PhotoboothFilters
import com.example.model.BoothLayout
import com.example.model.CameraLens
import com.example.model.PhotoFilter
import com.example.model.PhotoboothUiState
import com.example.ui.theme.BeVietnamProFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.studioAccentInk
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
fun CameraBoothScreen(
    uiState: PhotoboothUiState,
    isBoothActive: Boolean = true,
    onSelectLayout: (BoothLayout) -> Unit,
    onSelectFilter: (PhotoFilter) -> Unit,
    onToggleLens: () -> Unit,
    onToggleFillLight: () -> Unit,
    onStartShooting: (triggerShutter: () -> Unit) -> Unit,
    onCancelShooting: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onPhotoCaptureFailed: (Throwable) -> Unit,
    onOpenDevicePhotoPicker: (Boolean) -> Unit,
    onOpenDocumentFilePicker: (Boolean) -> Unit,
    onDismissCaptureError: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCircle: () -> Unit,
    applyBottomSystemPadding: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        hasRequestedOnce = true
    }

    val currentOpenDevicePicker by rememberUpdatedState(onOpenDevicePhotoPicker)
    val currentOpenDocumentPicker by rememberUpdatedState(onOpenDocumentFilePicker)
    val currentSingleMode by rememberUpdatedState(uiState.selectedLayout == BoothLayout.SINGLE_PHOTO)
    val currentIsBusy by rememberUpdatedState(uiState.isShootingSequence || uiState.isLoadingPhotos)

    val openDevicePhotoPicker = {
        if (!currentIsBusy) {
            currentOpenDevicePicker(currentSingleMode)
        }
    }

    val openDocumentFilePicker = {
        if (!currentIsBusy) {
            currentOpenDocumentPicker(currentSingleMode)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(uiState.flashTriggerCount) {
        if (uiState.flashTriggerCount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            flashAlpha.snapTo(0.88f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    val useFrontScreenFlash = isBoothActive &&
        uiState.isFillLightEnabled &&
        uiState.cameraLens == CameraLens.FRONT

    LaunchedEffect(hostActivity, useFrontScreenFlash) {
        val window = hostActivity?.window ?: return@LaunchedEffect
        val lp = window.attributes
        val target = if (useFrontScreenFlash) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (lp.screenBrightness != target) {
            lp.screenBrightness = target
            window.attributes = lp
        }
    }

    DisposableEffect(hostActivity) {
        onDispose {
            val window = hostActivity?.window ?: return@onDispose
            val lp = window.attributes
            if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    val backgroundColor = if (useFrontScreenFlash) {
        Color(0xFFFFF8F2)
    } else {
        MaterialTheme.colorScheme.background
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (!hasCameraPermission) {
            CameraPermissionGate(
                hasRequestedOnce = hasRequestedOnce,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onPickFromDevice = openDevicePhotoPicker,
                onPickFromFiles = openDocumentFilePicker,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .let { if (applyBottomSystemPadding) it.navigationBarsPadding() else it }
            )
        } else {
            CameraBoothContent(
                uiState = uiState,
                isBoothActive = isBoothActive,
                onSelectLayout = onSelectLayout,
                onSelectFilter = onSelectFilter,
                onToggleLens = onToggleLens,
                onToggleFillLight = onToggleFillLight,
                onStartShooting = onStartShooting,
                onCancelShooting = onCancelShooting,
                onPhotoCaptured = onPhotoCaptured,
                onPhotoCaptureFailed = onPhotoCaptureFailed,
                onPickFromDevice = openDevicePhotoPicker,
                onPickFromFiles = openDocumentFilePicker,
                onDismissCaptureError = onDismissCaptureError,
                onOpenProfile = onOpenProfile,
                onOpenSettings = onOpenSettings,
                onOpenMessages = onOpenMessages,
                onOpenCircle = onOpenCircle,
                applyBottomSystemPadding = applyBottomSystemPadding,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Instant photobooth screen flash overlay
        if (flashAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value))
            )
        }
    }
}

@Composable
private fun CameraBoothContent(
    uiState: PhotoboothUiState,
    isBoothActive: Boolean,
    onSelectLayout: (BoothLayout) -> Unit,
    onSelectFilter: (PhotoFilter) -> Unit,
    onToggleLens: () -> Unit,
    onToggleFillLight: () -> Unit,
    onStartShooting: (triggerShutter: () -> Unit) -> Unit,
    onCancelShooting: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onPhotoCaptureFailed: (Throwable) -> Unit,
    onPickFromDevice: () -> Unit,
    onPickFromFiles: () -> Unit,
    onDismissCaptureError: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCircle: () -> Unit,
    applyBottomSystemPadding: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isPreviewBound by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var retryCounter by remember { mutableStateOf(0) }

    val currentLens by rememberUpdatedState(uiState.cameraLens)

    DisposableEffect(Unit) {
        onDispose {
            runCatching { boundCamera?.cameraControl?.enableTorch(false) }
            runCatching { cameraProviderRef?.unbindAll() }
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(uiState.cameraLens, retryCounter, lifecycleOwner) {
        cameraError = null
        isPreviewBound = false
        imageCapture = null
        boundCamera = null

        try {
            val cameraProvider = awaitCameraProvider(context)
            cameraProviderRef = cameraProvider

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            val preferredSelector = if (uiState.cameraLens == CameraLens.FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val activeSelector = if (cameraProvider.hasCamera(preferredSelector)) {
                preferredSelector
            } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            cameraProvider.unbindAll()
            try {
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    preview,
                    capture
                )
                imageCapture = capture
                isPreviewBound = true
            } catch (_: Exception) {
                // Preview-only fallback guarantees live PreviewView snapshot capture even if ImageCapture stalls
                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    preview
                )
                imageCapture = null
                isPreviewBound = true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            cameraError = e.localizedMessage ?: "Camera unavailable"
            isPreviewBound = false
            imageCapture = null
            boundCamera = null
        }
    }

    // Rear camera -> hardware LED torch; Front camera -> disable rear torch (screen brightness takes over)
    LaunchedEffect(boundCamera, uiState.isFillLightEnabled, uiState.cameraLens, isBoothActive) {
        val camera = boundCamera ?: return@LaunchedEffect
        val enableRearTorch = isBoothActive &&
            uiState.isFillLightEnabled &&
            uiState.cameraLens == CameraLens.BACK &&
            camera.cameraInfo.hasFlashUnit()
        runCatching { camera.cameraControl.enableTorch(enableRearTorch) }
    }

    // Always read latest state inside shutter callback to prevent stale closures
    val currentCapture by rememberUpdatedState(imageCapture)
    val currentPreviewBound by rememberUpdatedState(isPreviewBound)
    val currentOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)
    val currentOnPhotoCaptureFailed by rememberUpdatedState(onPhotoCaptureFailed)

    val triggerShutter = remember {
        {
            val resolved = AtomicBoolean(false)
            val instantBackup = AtomicReference<Bitmap?>(null)

            fun snapshotPreviewNow(): Bitmap? = runCatching {
                val raw = previewView.bitmap
                if (raw != null && !raw.isRecycled && raw.width > 0 && raw.height > 0) {
                    val normalized = ImageProcessing.normalizePreviewBitmap(raw)
                    if (normalized !== raw && !raw.isRecycled) {
                        raw.recycle()
                    }
                    normalized
                } else {
                    null
                }
            }.getOrNull()

            // Synchronously capture exact posed frame from TextureView at flash moment
            if (Looper.myLooper() == Looper.getMainLooper()) {
                instantBackup.set(snapshotPreviewNow())
            }

            fun recycleBackupIfUnused() {
                val backup = instantBackup.getAndSet(null)
                if (backup != null && !backup.isRecycled) {
                    backup.recycle()
                }
            }

            fun deliverBitmap(bitmap: Bitmap) {
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                    return
                }
                if (resolved.compareAndSet(false, true)) {
                    recycleBackupIfUnused()
                    mainHandler.post {
                        currentOnPhotoCaptured(bitmap)
                    }
                } else if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

            fun captureFallbackFrame(failureReason: Throwable) {
                mainHandler.post {
                    if (resolved.get()) {
                        recycleBackupIfUnused()
                        return@post
                    }
                    val immediate = instantBackup.getAndSet(null) ?: snapshotPreviewNow()
                    if (immediate != null) {
                        deliverBitmap(immediate)
                        return@post
                    }

                    mainHandler.postDelayed({
                        if (resolved.get()) return@postDelayed
                        val retried = snapshotPreviewNow()
                        if (retried != null) {
                            deliverBitmap(retried)
                        } else if (resolved.compareAndSet(false, true)) {
                            currentOnPhotoCaptureFailed(failureReason)
                        }
                    }, 150L)
                }
            }

            val capture = currentCapture
            if (capture == null) {
                if (currentPreviewBound) {
                    captureFallbackFrame(
                        IllegalStateException("Không thể lấy khung hình từ camera")
                    )
                } else {
                    recycleBackupIfUnused()
                    currentOnPhotoCaptureFailed(IllegalStateException("Camera chưa sẵn sàng"))
                }
            } else {
                val mirrorFront = currentLens == CameraLens.FRONT
                val timeoutRunnable = Runnable {
                    captureFallbackFrame(
                        IllegalStateException("Camera không phản hồi. Hãy thử lại hoặc tải ảnh từ máy.")
                    )
                }
                mainHandler.postDelayed(timeoutRunnable, 900L)

                try {
                    capture.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                try {
                                    val bitmap = ImageProcessing.imageProxyToBitmap(
                                        imageProxy = image,
                                        mirrorHorizontal = mirrorFront
                                    )
                                    image.close()
                                    deliverBitmap(bitmap)
                                } catch (t: Throwable) {
                                    image.close()
                                    captureFallbackFrame(t)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                captureFallbackFrame(exception)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    captureFallbackFrame(t)
                }
            }
        }
    }

    val totalShots = uiState.selectedLayout.shotCount

    Column(
        modifier = modifier
            .statusBarsPadding()
            .let { if (applyBottomSystemPadding) it.navigationBarsPadding() else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header + Progress Strip
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.booth_badge),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenSettings,
                        enabled = !uiState.isShootingSequence,
                        shape = CircleShape,
                        color = if (uiState.isShootingSequence) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                        }
                    ) {
                        val statusText = if (uiState.isShootingSequence) {
                            val base = stringResource(
                                R.string.shooting_progress,
                                uiState.currentShotNumber.coerceIn(1, totalShots),
                                totalShots
                            )
                            val sec = uiState.countdownSeconds
                            if (sec != null) "$base • ${sec}s" else base
                        } else {
                            "${totalShots} tấm • ${uiState.countdownDurationSeconds}s"
                        }
                        Text(
                            text = statusText,
                            fontFamily = BeVietnamProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (uiState.isShootingSequence) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        enabled = !uiState.isShootingSequence,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic 1-slot or 4-slot live progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (slot in 0 until totalShots) {
                    val bitmap = uiState.capturedPhotos.getOrNull(slot)
                    val isCurrent = uiState.isShootingSequence && (uiState.currentShotNumber - 1 == slot)
                    ShotProgressThumbnail(
                        slotIndex = slot + 1,
                        bitmap = bitmap,
                        isCurrent = isCurrent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Capture error banner (never swallow camera errors silently)
        AnimatedVisibility(visible = uiState.captureErrorMessage != null) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = uiState.captureErrorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onDismissCaptureError()
                            onPickFromFiles()
                        },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pick_from_files),
                            style = MaterialTheme.typography.labelSmall,
                            color = studioAccentInk()
                        )
                    }
                    IconButton(
                        onClick = onDismissCaptureError,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Live Camera Viewfinder Frame
        val viewfinderAspect = when (uiState.selectedLayout) {
            BoothLayout.SINGLE_PHOTO -> 4f / 4.2f
            BoothLayout.PHOTO_STRIP -> 4f / 3f
            BoothLayout.GRID_2X2 -> 1f / 1f
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val fillGlowBorder = if (uiState.isFillLightEnabled && uiState.cameraLens == CameraLens.FRONT) {
                BorderStroke(6.dp, Color.White)
            } else if (uiState.isFillLightEnabled && uiState.cameraLens == CameraLens.BACK) {
                BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(viewfinderAspect.coerceAtMost(1.35f)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = fillGlowBorder,
                shadowElevation = 12.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (cameraError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = studioAccentInk(),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.camera_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { retryCounter++ },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(text = stringResource(R.string.camera_error_retry))
                                }
                                OutlinedButton(onClick = onPickFromDevice) {
                                    Text(text = stringResource(R.string.pick_from_gallery))
                                }
                                OutlinedButton(onClick = onPickFromFiles) {
                                    Text(text = stringResource(R.string.pick_from_files))
                                }
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { previewView },
                            update = { view ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val cf = PhotoboothFilters.toAndroidColorFilter(uiState.selectedFilter)
                                    view.setRenderEffect(cf?.let { RenderEffect.createColorFilterEffect(it) })
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                            uiState.selectedFilter != PhotoFilter.ORIGINAL
                        ) {
                            val fallbackTint = when (uiState.selectedFilter) {
                                PhotoFilter.ORIGINAL -> Color.Transparent
                                PhotoFilter.HIGH_CONTRAST_BW -> Color(0x32121216)
                                PhotoFilter.VINTAGE_FILM -> Color(0x28D89B38)
                                PhotoFilter.KOREAN_PINK -> Color(0x26FF9EC4)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(fallbackTint)
                            )
                        }
                    }

                    // Active filter badge in booth viewfinder
                    if (uiState.selectedFilter != PhotoFilter.ORIGINAL && cameraError == null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.56f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(uiState.selectedFilter.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Subtle bottom gradient vignette for legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }
            }

            // Render Countdown & Loading overlays in a sibling layer ABOVE AndroidView Surface
            // so hardware-accelerated TextureView/PreviewView can never obscure them.
            val activeCountdown = uiState.countdownSeconds
            if (uiState.isShootingSequence && activeCountdown != null) {
                CountdownBadge(
                    seconds = activeCountdown,
                    shotNumber = uiState.currentShotNumber.coerceIn(1, totalShots),
                    totalShots = totalShots,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(10f)
                )
            } else if (uiState.isShootingSequence) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.capturing_photo_now),
                            fontFamily = BeVietnamProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            } else if (uiState.isLoadingPhotos) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.76f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.loading_picked_photos),
                            fontFamily = BeVietnamProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // One-Handed Bottom Control Deck (all within thumb reach)
        Surface(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live In-Booth Filter Selector Row
                BoothFilterSelectorRow(
                    selectedFilter = uiState.selectedFilter,
                    enabled = !uiState.isLoadingPhotos,
                    onSelectFilter = onSelectFilter
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Layout Selector Pill (1 tấm / Dải dọc 4 / Lưới 2x2)
                LayoutSelectorPill(
                    selectedLayout = uiState.selectedLayout,
                    enabled = !uiState.isShootingSequence && !uiState.isLoadingPhotos,
                    onSelectLayout = onSelectLayout
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Thumb Row: Flip Camera — Shutter — Fill Light
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleLens,
                        enabled = !uiState.isShootingSequence && !uiState.isLoadingPhotos,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("flip_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = stringResource(R.string.flip_camera),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    ShutterActionButton(
                        isShooting = uiState.isShootingSequence,
                        countdownSeconds = uiState.countdownSeconds,
                        enabled = isPreviewBound && cameraError == null && !uiState.isLoadingPhotos,
                        onStart = { onStartShooting(triggerShutter) }
                    )

                    IconButton(
                        onClick = onToggleFillLight,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (uiState.isFillLightEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (uiState.isFillLightEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        modifier = Modifier
                            .size(52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("fill_light_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isFillLightEnabled) {
                                Icons.Rounded.FlashOn
                            } else {
                                Icons.Rounded.FlashOff
                            },
                            contentDescription = stringResource(
                                when {
                                    uiState.isFillLightEnabled && uiState.cameraLens == CameraLens.BACK -> R.string.flash_back_on
                                    !uiState.isFillLightEnabled && uiState.cameraLens == CameraLens.BACK -> R.string.flash_back_off
                                    uiState.isFillLightEnabled -> R.string.flash_front_on
                                    else -> R.string.flash_front_off
                                }
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pick photos from device / files OR dedicated Cancel Shooting button + readiness hint
                if (uiState.isShootingSequence) {
                    key("cancel_shooting_row") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onCancelShooting,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("cancel_shooting_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.cancel_shooting),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = stringResource(
                                    R.string.shooting_progress,
                                    uiState.currentShotNumber.coerceIn(1, totalShots),
                                    totalShots
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    key("pick_gallery_row") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onPickFromDevice,
                                    enabled = !uiState.isLoadingPhotos,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("pick_from_gallery_button")
                                ) {
                                    val accentInk = studioAccentInk()
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoLibrary,
                                        contentDescription = null,
                                        tint = accentInk,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.pick_from_gallery),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = accentInk,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onPickFromFiles,
                                    enabled = !uiState.isLoadingPhotos,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("pick_from_files_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.pick_from_files),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }

                            val cdSec = uiState.countdownDurationSeconds
                            val hintText = when {
                                uiState.isLoadingPhotos -> stringResource(R.string.loading_picked_photos)
                                !isPreviewBound && cameraError == null -> stringResource(R.string.camera_initializing)
                                uiState.selectedLayout == BoothLayout.SINGLE_PHOTO && cdSec == 0 ->
                                    stringResource(R.string.ready_hint_single_instant)
                                uiState.selectedLayout == BoothLayout.SINGLE_PHOTO ->
                                    stringResource(R.string.ready_hint_single_timed, cdSec)
                                cdSec == 0 -> stringResource(R.string.ready_hint_four_instant)
                                else -> stringResource(R.string.ready_hint_four_timed, cdSec)
                            }
                            Text(
                                text = hintText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Swipe / quick navigation strip: right -> Cá nhân, up -> Ảnh bạn bè, left -> Tin nhắn
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onOpenProfile,
                enabled = !uiState.isShootingSequence && !uiState.isLoadingPhotos,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("open_profile_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "← ${stringResource(R.string.swipe_right_profile_hint)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Surface(
                onClick = onOpenCircle,
                enabled = !uiState.isShootingSequence && !uiState.isLoadingPhotos,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("open_circle_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = studioAccentInk(),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.nav_circle),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = studioAccentInk(),
                        maxLines = 1
                    )
                }
            }

            Surface(
                onClick = onOpenMessages,
                enabled = !uiState.isShootingSequence && !uiState.isLoadingPhotos,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("open_messages_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.swipe_left_messages_hint)} →",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoothFilterSelectorRow(
    selectedFilter: PhotoFilter,
    enabled: Boolean,
    onSelectFilter: (PhotoFilter) -> Unit
) {
    val accentInk = studioAccentInk()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotoFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter
            val swatchColor = when (filter) {
                PhotoFilter.ORIGINAL -> MaterialTheme.colorScheme.primary
                PhotoFilter.HIGH_CONTRAST_BW -> Color(0xFF2B2B30)
                PhotoFilter.VINTAGE_FILM -> Color(0xFFD99A4E)
                PhotoFilter.KOREAN_PINK -> Color(0xFFF39BB8)
            }
            Surface(
                onClick = { if (enabled) onSelectFilter(filter) },
                enabled = enabled,
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                border = if (isSelected) {
                    BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("booth_filter_${filter.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) accentInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ShotProgressThumbnail(
    slotIndex: Int,
    bitmap: Bitmap?,
    isCurrent: Boolean
) {
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        imageBitmap != null -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "$slotIndex",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isCurrent) studioAccentInk() else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CountdownBadge(
    seconds: Int,
    shotNumber: Int,
    totalShots: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "Tấm $shotNumber/$totalShots",
                fontFamily = BeVietnamProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Surface(
            modifier = Modifier.size(124.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.64f),
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = seconds,
                    transitionSpec = {
                        (fadeIn(tween(120)) + scaleIn(initialScale = 0.78f)) togetherWith
                            (fadeOut(tween(80)) + scaleOut(targetScale = 1.15f))
                    },
                    label = "countdown_digit"
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        fontFamily = BeVietnamProFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 62.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutSelectorPill(
    selectedLayout: BoothLayout,
    enabled: Boolean,
    onSelectLayout: (BoothLayout) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BoothLayout.entries.forEach { layout ->
                val isSelected = layout == selectedLayout
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val icon = when (layout) {
                    BoothLayout.SINGLE_PHOTO -> Icons.Rounded.CropPortrait
                    BoothLayout.PHOTO_STRIP -> Icons.Rounded.ViewStream
                    BoothLayout.GRID_2X2 -> Icons.Rounded.GridView
                }

                Surface(
                    onClick = { onSelectLayout(layout) },
                    enabled = enabled,
                    shape = CircleShape,
                    color = bgColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("layout_${layout.name.lowercase()}_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(layout.labelRes),
                            fontFamily = BeVietnamProFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShutterActionButton(
    isShooting: Boolean,
    countdownSeconds: Int?,
    enabled: Boolean,
    onStart: () -> Unit
) {
    val outerRingColor = if (isShooting) {
        MaterialTheme.colorScheme.primary
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }

    Surface(
        onClick = onStart,
        enabled = !isShooting && enabled,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(3.5.dp, outerRingColor),
        modifier = Modifier
            .size(78.dp)
            .testTag("shutter_button")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isShooting) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (countdownSeconds != null) {
                        Text(
                            text = countdownSeconds.toString(),
                            fontFamily = BeVietnamProFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = studioAccentInk()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.take_photo_start),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGate(
    hasRequestedOnce: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromDevice: () -> Unit,
    onPickFromFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentInk = studioAccentInk()
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(
                        if (hasRequestedOnce) {
                            R.string.camera_permission_denied_settings
                        } else {
                            R.string.camera_permission_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = if (hasRequestedOnce) onOpenSettings else onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_camera_permission_button")
                ) {
                    Text(
                        text = stringResource(
                            if (hasRequestedOnce) {
                                R.string.open_settings
                            } else {
                                R.string.camera_permission_button
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onPickFromDevice,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pick_from_gallery),
                        style = MaterialTheme.typography.labelLarge,
                        color = accentInk
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onPickFromFiles,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pick_from_files),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (!continuation.isActive) return@addListener
                try {
                    continuation.resume(providerFuture.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
