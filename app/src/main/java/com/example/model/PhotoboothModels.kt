package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import com.example.R

enum class BoothLayout(@StringRes val labelRes: Int, val shotCount: Int) {
    SINGLE_PHOTO(R.string.layout_single, 1),
    PHOTO_STRIP(R.string.layout_strip, 4),
    GRID_2X2(R.string.layout_grid, 4)
}

enum class FrameStyle(@StringRes val labelRes: Int) {
    WHITE(R.string.frame_white),
    BLACK(R.string.frame_black),
    SUN_YELLOW(R.string.frame_sun_yellow),
    CREAM_GOLD(R.string.frame_cream_gold),
    PASTEL_PINK(R.string.frame_pastel_pink),
    MINT_GREEN(R.string.frame_mint_green),
    LAVENDER(R.string.frame_lavender),
    LIGHT_BLUE(R.string.frame_light_blue),
    CARO(R.string.frame_caro),
    YELLOW_CARO(R.string.frame_yellow_caro),
    FILM_STRIP(R.string.frame_film_strip)
}

enum class PhotoFilter(@StringRes val labelRes: Int) {
    ORIGINAL(R.string.filter_original),
    HIGH_CONTRAST_BW(R.string.filter_bw),
    VINTAGE_FILM(R.string.filter_vintage),
    KOREAN_PINK(R.string.filter_korean_pink)
}

enum class EditorTab(@StringRes val labelRes: Int) {
    FRAME(R.string.tab_frame),
    FILTER(R.string.tab_filter),
    AI_TEXT(R.string.tab_ai_text),
    CAPTION(R.string.tab_caption)
}

enum class OverlayTextShape(@StringRes val labelRes: Int) {
    STRAIGHT(R.string.shape_straight),
    ARC_UP(R.string.shape_arc_up),
    ARC_DOWN(R.string.shape_arc_down),
    HEART(R.string.shape_heart),
    CIRCLE(R.string.shape_circle),
    WAVE(R.string.shape_wave),
    MAGAZINE_STACK(R.string.shape_magazine),
    DIAGONAL_UP(R.string.shape_diagonal_up),
    DIAGONAL_DOWN(R.string.shape_diagonal_down),
    RIBBON_V(R.string.shape_ribbon_v)
}

enum class OverlayTextStyle(@StringRes val labelRes: Int) {
    SOLID_SHADOW(R.string.text_style_solid),
    FROSTED_GLASS(R.string.text_style_frosted),
    CANDY_STICKER(R.string.text_style_sticker),
    NEON_GLOW(R.string.text_style_neon),
    RETRO_3D(R.string.text_style_3d),
    OUTLINE_HOLLOW(R.string.text_style_outline),
    DOUBLE_STROKE(R.string.text_style_double_stroke),
    GRADIENT_GOLD(R.string.text_style_gold),
    GRADIENT_SUNSET(R.string.text_style_sunset),
    GRADIENT_OCEAN(R.string.text_style_ocean),
    GRADIENT_RAINBOW(R.string.text_style_rainbow),
    GLITCH_RGB(R.string.text_style_glitch),
    CHROME_METALLIC(R.string.text_style_chrome),
    EDITORIAL_SOFT(R.string.text_style_editorial)
}

enum class OverlayFontStyle(@StringRes val labelRes: Int) {
    BE_VIETNAM_PRO(R.string.font_modern),
    ANTON(R.string.font_poster),
    PLAYFAIR(R.string.font_luxury),
    PACIFICO(R.string.font_brush),
    DANCING_SCRIPT(R.string.font_script),
    OSWALD(R.string.font_editorial),
    COMFORTAA(R.string.font_rounded),
    MONTSERRAT(R.string.font_minimal),
    PATRICK_HAND(R.string.font_handwriting),
    JETBRAINS_MONO(R.string.font_typewriter),
    FRAUNCES(R.string.font_classic_serif),
    CHAKRA_PETCH(R.string.font_cyber),
    SYSTEM_DEFAULT(R.string.font_system)
}

data class CreativeOverlayTextState(
    val text: String = "",
    val shape: OverlayTextShape = OverlayTextShape.ARC_UP,
    val style: OverlayTextStyle = OverlayTextStyle.FROSTED_GLASS,
    val font: OverlayFontStyle = OverlayFontStyle.ANTON,
    val colorHex: String = "#FFFFFF",
    val behindSubject: Boolean = true,
    val repeatAlongShape: Boolean = false,
    val scale: Float = 1.0f,
    val letterSpacingEm: Float = 0.0f,
    val lineSpacingMultiplier: Float = 0.95f,
    val centerXFraction: Float = 0.5f,
    val centerYFraction: Float = 0.30f,
    val bendAngleDegrees: Float = 0f,
    val opacity: Float = 0.96f,
    val targetSlotIndex: Int = 0 // -1 = all slots, 0..3 = specific slot
)

data class SubjectAiUiState(
    val isScanning: Boolean = false,
    val subjectMasks: List<Bitmap?> = emptyList(),
    val detectedSubjectLabel: String? = null,
    val moodTag: String? = null,
    val suggestedCaptions: List<String> = emptyList(),
    val segmentationMessage: String? = null,
    val aiErrorMessage: String? = null
)

enum class CameraLens {
    FRONT,
    BACK
}

sealed interface BoothScreen {
    data object Capture : BoothScreen
    data object Customize : BoothScreen
}

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Success(val uri: Uri) : SaveState
    data class Error(val message: String) : SaveState
}

data class PhotoboothUiState(
    val currentScreen: BoothScreen = BoothScreen.Capture,
    val selectedLayout: BoothLayout = BoothLayout.SINGLE_PHOTO,
    val cameraLens: CameraLens = CameraLens.FRONT,
    val isFillLightEnabled: Boolean = false,
    val countdownDurationSeconds: Int = 3,
    val isShootingSequence: Boolean = false,
    val isLoadingPhotos: Boolean = false,
    val countdownSeconds: Int? = null,
    val currentShotNumber: Int = 0,
    val flashTriggerCount: Int = 0,
    val captureErrorMessage: String? = null,
    val capturedPhotos: List<Bitmap> = emptyList(),
    val selectedFrame: FrameStyle = FrameStyle.WHITE,
    val selectedFilter: PhotoFilter = PhotoFilter.ORIGINAL,
    val captionText: String = "",
    val showDateStamp: Boolean = true,
    val formattedDate: String = "",
    val activeEditorTab: EditorTab = EditorTab.FRAME,
    val creativeText: CreativeOverlayTextState = CreativeOverlayTextState(),
    val subjectAi: SubjectAiUiState = SubjectAiUiState(),
    val pendingCropSlotIndex: Int? = null,
    val saveState: SaveState = SaveState.Idle
)
