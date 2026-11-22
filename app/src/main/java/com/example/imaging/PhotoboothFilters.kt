package com.example.imaging

import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter as AndroidColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter as ComposeColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import com.example.model.PhotoFilter

object PhotoboothFilters {

    /**
     * Returns the shared 4x5 color matrix array so live Compose preview and
     * high-resolution Bitmap export produce identical color grading.
     */
    fun matrixValues(filter: PhotoFilter): FloatArray? = when (filter) {
        PhotoFilter.ORIGINAL -> null

        // High-contrast monochrome photobooth film look
        PhotoFilter.HIGH_CONTRAST_BW -> {
            val contrast = 1.42f
            val translate = (-0.5f * contrast + 0.5f) * 255f - 8f
            val r = 0.299f * contrast
            val g = 0.587f * contrast
            val b = 0.114f * contrast
            floatArrayOf(
                r, g, b, 0f, translate,
                r, g, b, 0f, translate,
                r, g, b, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        }

        // Warm analog vintage film with subtle faded blacks and amber highlights
        PhotoFilter.VINTAGE_FILM -> floatArrayOf(
            1.06f, 0.09f, 0.02f, 0f, 10f,
            0.03f, 0.98f, 0.02f, 0f, 7f,
            0.01f, 0.07f, 0.76f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )

        // Bright, airy Korean-studio rosy skin glow
        PhotoFilter.KOREAN_PINK -> floatArrayOf(
            1.08f, 0.04f, 0.04f, 0f, 15f,
            0.02f, 1.00f, 0.02f, 0f, 7f,
            0.04f, 0.04f, 1.03f, 0f, 13f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    fun toComposeColorFilter(filter: PhotoFilter): ComposeColorFilter? {
        val values = matrixValues(filter) ?: return null
        return ComposeColorFilter.colorMatrix(ComposeColorMatrix(values))
    }

    fun toAndroidColorFilter(filter: PhotoFilter): AndroidColorMatrixColorFilter? {
        val values = matrixValues(filter) ?: return null
        return AndroidColorMatrixColorFilter(AndroidColorMatrix(values))
    }
}
