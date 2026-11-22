package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SubjectAiManager
import com.example.imaging.GallerySaver
import com.example.imaging.ImageProcessing
import com.example.imaging.PhotoboothRenderer
import com.example.model.BoothLayout
import com.example.model.BoothScreen
import com.example.model.CameraLens
import com.example.model.CreativeOverlayTextState
import com.example.model.EditorTab
import com.example.model.FrameStyle
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import com.example.model.PhotoFilter
import com.example.model.PhotoboothUiState
import com.example.model.SaveState
import com.example.model.SubjectAiUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoboothViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        PhotoboothUiState(
            formattedDate = currentFormattedDate()
        )
    )
    val uiState: StateFlow<PhotoboothUiState> = _uiState.asStateFlow()

    private var sequenceJob: Job? = null
    private var pendingPhotoDeferred: CompletableDeferred<Bitmap>? = null

    fun setCountdownDuration(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
        _uiState.update { it.copy(countdownDurationSeconds = clamped) }
    }

    fun selectLayout(layout: BoothLayout) {
        if (_uiState.value.isShootingSequence || _uiState.value.isLoadingPhotos) return
        _uiState.update { it.copy(selectedLayout = layout, captureErrorMessage = null) }
    }

    fun toggleCameraLens() {
        if (_uiState.value.isShootingSequence || _uiState.value.isLoadingPhotos) return
        _uiState.update { state ->
            val nextLens = if (state.cameraLens == CameraLens.FRONT) {
                CameraLens.BACK
            } else {
                CameraLens.FRONT
            }
            state.copy(cameraLens = nextLens, captureErrorMessage = null)
        }
    }

    fun toggleFillLight() {
        _uiState.update { it.copy(isFillLightEnabled = !it.isFillLightEnabled) }
    }

    fun dismissCaptureError() {
        _uiState.update { it.copy(captureErrorMessage = null) }
    }

    fun reportCaptureError(message: String) {
        _uiState.update { it.copy(captureErrorMessage = message, isLoadingPhotos = false) }
    }

    fun startShootingSequence(triggerShutter: () -> Unit) {
        if (_uiState.value.isShootingSequence || _uiState.value.isLoadingPhotos) return

        val totalShots = _uiState.value.selectedLayout.shotCount
        val countdownDuration = _uiState.value.countdownDurationSeconds
            .coerceIn(MIN_COUNTDOWN_SECONDS, MAX_COUNTDOWN_SECONDS)
        sequenceJob?.cancel()

        _uiState.update {
            it.copy(
                isShootingSequence = true,
                countdownSeconds = if (countdownDuration > 0) countdownDuration else null,
                currentShotNumber = 1,
                capturedPhotos = emptyList(),
                captureErrorMessage = null,
                saveState = SaveState.Idle
            )
        }

        sequenceJob = viewModelScope.launch {
            try {
                for (shotIndex in 0 until totalShots) {
                    _uiState.update {
                        it.copy(
                            currentShotNumber = shotIndex + 1,
                            countdownSeconds = if (countdownDuration > 0) countdownDuration else null
                        )
                    }

                    if (countdownDuration > 0) {
                        for (second in countdownDuration downTo 1) {
                            _uiState.update { it.copy(countdownSeconds = second) }
                            delay(1000L)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            countdownSeconds = null,
                            flashTriggerCount = it.flashTriggerCount + 1
                        )
                    }

                    val deferred = CompletableDeferred<Bitmap>()
                    pendingPhotoDeferred = deferred
                    try {
                        triggerShutter()
                    } catch (t: Throwable) {
                        deferred.completeExceptionally(t)
                    }

                    val capturedBitmap = withTimeout(CAPTURE_TIMEOUT_MS) {
                        deferred.await()
                    }
                    pendingPhotoDeferred = null

                    _uiState.update { state ->
                        state.copy(capturedPhotos = state.capturedPhotos + capturedBitmap)
                    }

                    if (shotIndex < totalShots - 1) {
                        delay(500L)
                    }
                }

                delay(300L)
                _uiState.update {
                    it.copy(
                        isShootingSequence = false,
                        countdownSeconds = null,
                        currentShotNumber = 0,
                        formattedDate = currentFormattedDate(),
                        currentScreen = BoothScreen.Customize,
                        subjectAi = SubjectAiUiState(),
                        saveState = SaveState.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingPhotoDeferred = null
                _uiState.update {
                    it.copy(
                        isShootingSequence = false,
                        countdownSeconds = null,
                        currentShotNumber = 0,
                        captureErrorMessage = e.localizedMessage
                            ?: "Không thể chụp ảnh. Vui lòng thử lại hoặc tải ảnh từ máy."
                    )
                }
            }
        }
    }

    fun loadPhotosFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isShootingSequence || _uiState.value.isLoadingPhotos) return
        val appContext = context.applicationContext

        _uiState.update { it.copy(isLoadingPhotos = true, captureErrorMessage = null) }

        viewModelScope.launch {
            try {
                val decoded = mutableListOf<Bitmap>()
                for (uri in uris.take(4)) {
                    val bmp = ImageProcessing.decodeUriToBitmap(appContext, uri)
                    if (bmp != null) {
                        decoded.add(bmp)
                    }
                }

                if (decoded.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoadingPhotos = false,
                            captureErrorMessage = "Không thể đọc ảnh đã chọn từ máy. Vui lòng thử ảnh khác."
                        )
                    }
                    return@launch
                }

                val currentLayout = _uiState.value.selectedLayout
                val targetLayout = when {
                    decoded.size == 1 -> BoothLayout.SINGLE_PHOTO
                    currentLayout == BoothLayout.SINGLE_PHOTO -> BoothLayout.PHOTO_STRIP
                    else -> currentLayout
                }

                val finalPhotos = if (targetLayout == BoothLayout.SINGLE_PHOTO) {
                    listOf(decoded.first())
                } else if (decoded.size >= 4) {
                    decoded.take(4)
                } else {
                    (0 until 4).map { idx ->
                        val src = decoded[idx % decoded.size]
                        if (idx < decoded.size) src else (src.copy(Bitmap.Config.ARGB_8888, false) ?: src)
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoadingPhotos = false,
                        selectedLayout = targetLayout,
                        capturedPhotos = finalPhotos,
                        formattedDate = currentFormattedDate(),
                        currentScreen = BoothScreen.Customize,
                        subjectAi = SubjectAiUiState(),
                        pendingCropSlotIndex = if (decoded.size == 1) 0 else null,
                        saveState = SaveState.Idle
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isLoadingPhotos = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPhotos = false,
                        captureErrorMessage = e.localizedMessage
                            ?: "Không thể đọc ảnh đã chọn từ máy."
                    )
                }
            }
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        val isRecycled = runCatching { bitmap.isRecycled }.getOrDefault(false)
        if (isRecycled) {
            pendingPhotoDeferred?.completeExceptionally(
                IllegalStateException("Ảnh vừa chụp không hợp lệ")
            )
            return
        }
        val deferred = pendingPhotoDeferred
        if (deferred == null || !deferred.complete(bitmap)) {
            runCatching {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun onPhotoCaptureFailed(throwable: Throwable) {
        pendingPhotoDeferred?.completeExceptionally(throwable)
    }

    fun cancelShootingSequence() {
        sequenceJob?.cancel()
        sequenceJob = null
        pendingPhotoDeferred?.cancel()
        pendingPhotoDeferred = null
        _uiState.update {
            it.copy(
                isShootingSequence = false,
                isLoadingPhotos = false,
                countdownSeconds = null,
                currentShotNumber = 0,
                capturedPhotos = emptyList(),
                captureErrorMessage = null
            )
        }
    }

    fun openCropEditor(slotIndex: Int) {
        val photos = _uiState.value.capturedPhotos
        if (photos.isEmpty()) return
        val safeIdx = slotIndex.coerceIn(0, photos.lastIndex)
        _uiState.update { it.copy(pendingCropSlotIndex = safeIdx) }
    }

    fun dismissCropEditor() {
        _uiState.update { it.copy(pendingCropSlotIndex = null) }
    }

    fun applyEditedPhotoForSlot(slotIndex: Int, editedBitmap: Bitmap) {
        _uiState.update { state ->
            val currentPhotos = state.capturedPhotos.toMutableList()
            if (slotIndex !in currentPhotos.indices) {
                return@update state.copy(pendingCropSlotIndex = null)
            }
            currentPhotos[slotIndex] = editedBitmap
            val updatedMasks = state.subjectAi.subjectMasks.toMutableList()
            if (slotIndex in updatedMasks.indices) {
                updatedMasks[slotIndex] = null
            }
            state.copy(
                capturedPhotos = currentPhotos,
                subjectAi = state.subjectAi.copy(
                    subjectMasks = updatedMasks,
                    segmentationMessage = null
                ),
                pendingCropSlotIndex = null,
                saveState = SaveState.Idle
            )
        }
    }

    fun selectEditorTab(tab: EditorTab) {
        _uiState.update { it.copy(activeEditorTab = tab) }
    }

    fun selectFrameStyle(style: FrameStyle) {
        _uiState.update { it.copy(selectedFrame = style, saveState = SaveState.Idle) }
    }

    fun selectFilter(filter: PhotoFilter) {
        _uiState.update { it.copy(selectedFilter = filter, saveState = SaveState.Idle) }
    }

    fun updateCaption(text: String) {
        val clipped = text.take(MAX_CAPTION_LENGTH)
        _uiState.update { it.copy(captionText = clipped, saveState = SaveState.Idle) }
    }

    fun toggleShowDateStamp(show: Boolean) {
        _uiState.update { it.copy(showDateStamp = show, saveState = SaveState.Idle) }
    }

    fun toggleLayoutInEditor(layout: BoothLayout) {
        val current = _uiState.value
        if (layout.shotCount > current.capturedPhotos.size) return
        _uiState.update { it.copy(selectedLayout = layout, saveState = SaveState.Idle) }
    }

    fun updateCreativeText(text: String) {
        val clipped = text.take(MAX_CREATIVE_TEXT_LENGTH)
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(text = clipped),
                saveState = SaveState.Idle
            )
        }
    }

    fun selectOverlayShape(shape: OverlayTextShape) {
        _uiState.update { state ->
            val autoRepeat = when (shape) {
                OverlayTextShape.HEART, OverlayTextShape.CIRCLE -> state.creativeText.repeatAlongShape
                else -> false
            }
            state.copy(
                creativeText = state.creativeText.copy(
                    shape = shape,
                    repeatAlongShape = autoRepeat
                ),
                saveState = SaveState.Idle
            )
        }
    }

    fun selectOverlayStyle(style: OverlayTextStyle) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(style = style),
                saveState = SaveState.Idle
            )
        }
    }

    fun selectOverlayFont(font: OverlayFontStyle) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(font = font),
                saveState = SaveState.Idle
            )
        }
    }

    fun selectOverlayColor(colorHex: String) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(colorHex = colorHex),
                saveState = SaveState.Idle
            )
        }
    }

    fun toggleBehindSubject(behind: Boolean) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(behindSubject = behind),
                saveState = SaveState.Idle
            )
        }
    }

    fun toggleRepeatAlongShape(repeat: Boolean) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(repeatAlongShape = repeat),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextScale(scale: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(scale = scale.coerceIn(0.45f, 3.5f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextLetterSpacing(em: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(letterSpacingEm = em.coerceIn(-0.05f, 0.45f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextLineSpacing(multiplier: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(lineSpacingMultiplier = multiplier.coerceIn(0.75f, 1.45f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextPositionY(yFraction: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(centerYFraction = yFraction.coerceIn(0.12f, 0.88f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextPositionX(xFraction: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(centerXFraction = xFraction.coerceIn(0.15f, 0.85f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextBend(degrees: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(bendAngleDegrees = degrees.coerceIn(-45f, 45f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun updateCreativeTextOpacity(opacity: Float) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(opacity = opacity.coerceIn(0.30f, 1.0f)),
                saveState = SaveState.Idle
            )
        }
    }

    fun selectTargetSlot(slotIndex: Int) {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(targetSlotIndex = slotIndex),
                saveState = SaveState.Idle
            )
        }
    }

    fun resetCreativeTextTransform() {
        _uiState.update {
            it.copy(
                creativeText = it.creativeText.copy(
                    scale = 1.0f,
                    letterSpacingEm = 0.0f,
                    lineSpacingMultiplier = 0.95f,
                    centerXFraction = 0.5f,
                    centerYFraction = 0.30f,
                    bendAngleDegrees = 0f,
                    opacity = 0.96f
                ),
                saveState = SaveState.Idle
            )
        }
    }

    fun scanSubjectWithAi(context: Context, analyzeCaptionSuggestions: Boolean = true) {
        val current = _uiState.value
        val photos = current.capturedPhotos
        if (photos.isEmpty() || current.subjectAi.isScanning) return
        val appContext = context.applicationContext

        _uiState.update { state ->
            state.copy(
                subjectAi = state.subjectAi.copy(
                    isScanning = true,
                    segmentationMessage = null,
                    aiErrorMessage = null
                )
            )
        }

        viewModelScope.launch {
            try {
                val primaryIndex = if (
                    current.selectedLayout == BoothLayout.SINGLE_PHOTO ||
                    current.creativeText.targetSlotIndex < 0
                ) {
                    0
                } else {
                    current.creativeText.targetSlotIndex.coerceIn(0, photos.lastIndex)
                }

                // Run on-device subject segmentation across slots and Gemini vision subject identification
                val masksAndAi = coroutineScope {
                    val maskDeferreds = photos.map { bmp ->
                        async { SubjectAiManager.segmentForeground(appContext, bmp) }
                    }
                    val aiDeferred = if (analyzeCaptionSuggestions) {
                        async { SubjectAiManager.analyzeSubjectForText(photos[primaryIndex]) }
                    } else {
                        null
                    }
                    val maskResults = maskDeferreds.awaitAll()
                    val aiResult = aiDeferred?.await()
                    maskResults to aiResult
                }

                val maskResults = masksAndAi.first
                val aiResult = masksAndAi.second

                val masks = maskResults.map { it.getOrNull() }
                val hasAnyMask = masks.any { it != null }
                val firstSegError = maskResults.firstNotNullOfOrNull { it.exceptionOrNull()?.localizedMessage }

                _uiState.update { state ->
                    val aiData = aiResult?.getOrNull()
                    val aiError = aiResult?.exceptionOrNull()?.localizedMessage

                    val updatedCreative = if (aiData != null) {
                        val shouldFillText = state.creativeText.text.isBlank()
                        state.creativeText.copy(
                            text = if (shouldFillText) {
                                aiData.suggestedTexts.firstOrNull().orEmpty().take(MAX_CREATIVE_TEXT_LENGTH)
                            } else {
                                state.creativeText.text
                            },
                            behindSubject = hasAnyMask || state.creativeText.behindSubject,
                            shape = if (shouldFillText) aiData.recommendedShape else state.creativeText.shape,
                            style = if (shouldFillText) aiData.recommendedStyle else state.creativeText.style,
                            font = if (shouldFillText) aiData.recommendedFont else state.creativeText.font,
                            colorHex = if (shouldFillText) aiData.recommendedColorHex else state.creativeText.colorHex
                        )
                    } else {
                        state.creativeText.copy(
                            behindSubject = if (hasAnyMask) true else state.creativeText.behindSubject
                        )
                    }

                    val segMsg = when {
                        hasAnyMask && aiData != null ->
                            "Đã tách chủ thể: ${aiData.subjectLabel} (${aiData.moodTag})"
                        hasAnyMask ->
                            "Đã tách nền chủ thể thành công! Chữ sẽ nằm phía sau chủ thể."
                        firstSegError != null -> firstSegError
                        else -> "Không thể tách chủ thể rõ nét trên ảnh này."
                    }

                    state.copy(
                        creativeText = updatedCreative,
                        subjectAi = SubjectAiUiState(
                            isScanning = false,
                            subjectMasks = masks,
                            detectedSubjectLabel = aiData?.subjectLabel,
                            moodTag = aiData?.moodTag,
                            suggestedCaptions = aiData?.suggestedTexts ?: state.subjectAi.suggestedCaptions,
                            segmentationMessage = segMsg,
                            aiErrorMessage = aiError
                        ),
                        saveState = SaveState.Idle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "scanSubjectWithAi failed", e)
                _uiState.update { state ->
                    state.copy(
                        subjectAi = state.subjectAi.copy(
                            isScanning = false,
                            aiErrorMessage = e.localizedMessage ?: "Không thể quét chủ thể AI."
                        )
                    )
                }
            }
        }
    }

    fun clearAiError() {
        _uiState.update {
            it.copy(subjectAi = it.subjectAi.copy(aiErrorMessage = null, segmentationMessage = null))
        }
    }

    fun returnToCameraForRetake() {
        _uiState.update {
            it.copy(
                currentScreen = BoothScreen.Capture,
                capturedPhotos = emptyList(),
                isShootingSequence = false,
                countdownSeconds = null,
                currentShotNumber = 0,
                captureErrorMessage = null,
                creativeText = CreativeOverlayTextState(),
                subjectAi = SubjectAiUiState(),
                pendingCropSlotIndex = null,
                saveState = SaveState.Idle
            )
        }
    }

    fun saveCollageToGallery(context: Context) {
        val current = _uiState.value
        val required = current.selectedLayout.shotCount
        if (current.saveState is SaveState.Saving || current.capturedPhotos.size < required) {
            return
        }

        _uiState.update { it.copy(saveState = SaveState.Saving) }

        viewModelScope.launch {
            var renderedBitmap: Bitmap? = null
            try {
                renderedBitmap = PhotoboothRenderer.renderCollage(
                    context = context.applicationContext,
                    photos = current.capturedPhotos,
                    layout = current.selectedLayout,
                    frameStyle = current.selectedFrame,
                    filter = current.selectedFilter,
                    captionText = current.captionText,
                    showDateStamp = current.showDateStamp,
                    formattedDate = current.formattedDate,
                    creativeText = current.creativeText,
                    subjectMasks = current.subjectAi.subjectMasks
                )

                val result = GallerySaver.saveBitmapToGallery(
                    context = context.applicationContext,
                    bitmap = renderedBitmap
                )

                _uiState.update { state ->
                    result.fold(
                        onSuccess = { uri -> state.copy(saveState = SaveState.Success(uri)) },
                        onFailure = { err ->
                            state.copy(
                                saveState = SaveState.Error(
                                    err.localizedMessage ?: "Lỗi không xác định"
                                )
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        saveState = SaveState.Error(
                            e.localizedMessage ?: "Không thể xuất ảnh"
                        )
                    )
                }
            } finally {
                renderedBitmap?.recycle()
            }
        }
    }

    fun dismissSaveState() {
        _uiState.update { it.copy(saveState = SaveState.Idle) }
    }

    private fun clearCapturedBitmaps() {
        val uniqueBitmaps = _uiState.value.capturedPhotos.distinct()
        uniqueBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun currentFormattedDate(): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        sequenceJob?.cancel()
        clearCapturedBitmaps()
    }

    companion object {
        private const val TAG = "PhotoboothVM"
        const val DEFAULT_COUNTDOWN_SECONDS = 3
        const val MIN_COUNTDOWN_SECONDS = 0
        const val MAX_COUNTDOWN_SECONDS = 10
        const val MAX_CAPTION_LENGTH = 36
        const val MAX_CREATIVE_TEXT_LENGTH = 32
        private const val CAPTURE_TIMEOUT_MS = 6000L
    }
}
