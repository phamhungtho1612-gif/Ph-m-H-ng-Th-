package com.example.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.example.model.BoothLayout
import com.example.model.CreativeOverlayTextState
import com.example.model.FrameStyle
import com.example.model.PhotoFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

object PhotoboothRenderer {

    suspend fun renderCollage(
        context: Context,
        photos: List<Bitmap>,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        filter: PhotoFilter,
        captionText: String,
        showDateStamp: Boolean,
        formattedDate: String,
        creativeText: CreativeOverlayTextState = CreativeOverlayTextState(),
        subjectMasks: List<Bitmap?> = emptyList()
    ): Bitmap = withContext(Dispatchers.Default) {
        val (canvasWidth, canvasHeight) = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 1600 to 2040
            BoothLayout.PHOTO_STRIP -> 1200 to 3560
            BoothLayout.GRID_2X2 -> 2000 to 2440
        }

        val output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Draw frame background
        drawFrameBackground(canvas, canvasWidth.toFloat(), canvasHeight.toFloat(), frameStyle)

        // 2. Compute photo slot rectangles
        val slotRects = computeSlotRects(layout, canvasWidth.toFloat(), canvasHeight.toFloat())
        val cornerRadius = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 28f
            BoothLayout.PHOTO_STRIP -> 20f
            BoothLayout.GRID_2X2 -> 24f
        }

        val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PhotoboothFilters.toAndroidColorFilter(filter)
        }

        // 3. Draw each captured photo + optional depth-layered creative text
        slotRects.forEachIndexed { index, destRect ->
            val bitmap = photos.getOrNull(index) ?: return@forEachIndexed
            drawCenterCroppedRoundedBitmap(
                canvas = canvas,
                bitmap = bitmap,
                destRect = destRect,
                cornerRadius = cornerRadius,
                paint = photoPaint
            )

            val shouldDrawText = creativeText.text.isNotBlank() && (
                layout == BoothLayout.SINGLE_PHOTO && index == 0 ||
                    creativeText.targetSlotIndex == -1 ||
                    creativeText.targetSlotIndex == index
                )

            if (shouldDrawText) {
                CreativeTextRenderer.drawCreativeTextInSlot(
                    context = context,
                    canvas = canvas,
                    slotRect = destRect,
                    cornerRadius = cornerRadius,
                    overlay = creativeText
                )

                val fgMask = if (creativeText.behindSubject) {
                    subjectMasks.getOrNull(index)
                } else {
                    null
                }
                if (fgMask != null && !fgMask.isRecycled) {
                    drawCenterCroppedRoundedBitmap(
                        canvas = canvas,
                        bitmap = fgMask,
                        destRect = destRect,
                        cornerRadius = cornerRadius,
                        paint = photoPaint
                    )
                }
            }
        }

        // 4. Draw footer (memory caption below frame + date stamp at bottom corner)
        val footerTop = slotRects.lastOrNull()?.bottom ?: (canvasHeight * 0.85f)
        val marginHorizontal = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 96f
            BoothLayout.PHOTO_STRIP -> 76f
            BoothLayout.GRID_2X2 -> 92f
        }
        val footerRect = RectF(
            marginHorizontal,
            footerTop + 28f,
            canvasWidth - marginHorizontal,
            canvasHeight - 54f
        )

        drawFooter(
            context = context,
            canvas = canvas,
            footerRect = footerRect,
            layout = layout,
            frameStyle = frameStyle,
            captionText = captionText.trim(),
            showDateStamp = showDateStamp,
            formattedDate = formattedDate
        )

        output
    }

    private fun drawFrameBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        frameStyle: FrameStyle
    ) {
        when (frameStyle) {
            FrameStyle.WHITE -> canvas.drawColor(Color.WHITE)
            FrameStyle.BLACK -> canvas.drawColor(Color.parseColor("#141416"))
            FrameStyle.SUN_YELLOW -> canvas.drawColor(Color.parseColor("#FFE14D"))
            FrameStyle.CREAM_GOLD -> canvas.drawColor(Color.parseColor("#FFF4D2"))
            FrameStyle.PASTEL_PINK -> canvas.drawColor(Color.parseColor("#FFD6E2"))
            FrameStyle.MINT_GREEN -> canvas.drawColor(Color.parseColor("#D3F6E5"))
            FrameStyle.LAVENDER -> canvas.drawColor(Color.parseColor("#E8DCFF"))
            FrameStyle.LIGHT_BLUE -> canvas.drawColor(Color.parseColor("#D2EAFF"))
            FrameStyle.CARO -> {
                drawCheckeredBackground(
                    canvas = canvas,
                    width = width,
                    height = height,
                    baseColor = Color.parseColor("#FFFBF7"),
                    stripeColor = Color.argb(89, 255, 155, 179)
                )
            }
            FrameStyle.YELLOW_CARO -> {
                drawCheckeredBackground(
                    canvas = canvas,
                    width = width,
                    height = height,
                    baseColor = Color.parseColor("#FFFDF0"),
                    stripeColor = Color.argb(102, 245, 196, 0)
                )
            }
            FrameStyle.FILM_STRIP -> {
                canvas.drawColor(Color.parseColor("#19181D"))
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FAF7F0")
                    style = Paint.Style.FILL
                }
                val inset = width * 0.013f
                val holeW = width * 0.018f
                val holeH = width * 0.025f
                val step = width * 0.052f
                val radius = holeW * 0.25f
                var y = inset * 1.4f
                while (y + holeH < height - inset) {
                    canvas.drawRoundRect(
                        RectF(inset, y, inset + holeW, y + holeH),
                        radius,
                        radius,
                        holePaint
                    )
                    canvas.drawRoundRect(
                        RectF(width - inset - holeW, y, width - inset, y + holeH),
                        radius,
                        radius,
                        holePaint
                    )
                    y += step
                }
            }
        }
    }

    private fun drawCheckeredBackground(
        canvas: Canvas,
        width: Float,
        height: Float,
        baseColor: Int,
        stripeColor: Int
    ) {
        canvas.drawColor(baseColor)
        val cellSize = 72f
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stripeColor
            style = Paint.Style.FILL
        }
        var x = 0f
        var col = 0
        while (x < width) {
            if (col % 2 == 1) {
                canvas.drawRect(x, 0f, x + cellSize, height, stripePaint)
            }
            x += cellSize
            col++
        }
        var y = 0f
        var row = 0
        while (y < height) {
            if (row % 2 == 1) {
                canvas.drawRect(0f, y, width, y + cellSize, stripePaint)
            }
            y += cellSize
            row++
        }
    }

    private fun computeSlotRects(
        layout: BoothLayout,
        width: Float,
        height: Float
    ): List<RectF> {
        return when (layout) {
            BoothLayout.SINGLE_PHOTO -> {
                val margin = 96f
                val slotWidth = width - margin * 2f
                val slotHeight = 1540f
                listOf(RectF(margin, margin, margin + slotWidth, margin + slotHeight))
            }

            BoothLayout.PHOTO_STRIP -> {
                val margin = 76f
                val gap = 36f
                val slotWidth = width - margin * 2f
                val slotHeight = 725f
                (0 until 4).map { index ->
                    val top = margin + index * (slotHeight + gap)
                    RectF(margin, top, margin + slotWidth, top + slotHeight)
                }
            }

            BoothLayout.GRID_2X2 -> {
                val margin = 92f
                val gap = 44f
                val slotWidth = (width - margin * 2f - gap) / 2f
                val slotHeight = 935f
                (0 until 4).map { index ->
                    val row = index / 2
                    val col = index % 2
                    val left = margin + col * (slotWidth + gap)
                    val top = margin + row * (slotHeight + gap)
                    RectF(left, top, left + slotWidth, top + slotHeight)
                }
            }
        }
    }

    private fun drawCenterCroppedRoundedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        destRect: RectF,
        cornerRadius: Float,
        paint: Paint
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        val destWidth = destRect.width()
        val destHeight = destRect.height()

        val scale = max(destWidth / srcWidth, destHeight / srcHeight)
        val cropWidth = destWidth / scale
        val cropHeight = destHeight / scale
        val srcLeft = ((srcWidth - cropWidth) / 2f).toInt().coerceAtLeast(0)
        val srcTop = ((srcHeight - cropHeight) / 2f).toInt().coerceAtLeast(0)
        val srcRight = (srcLeft + cropWidth).toInt().coerceAtMost(bitmap.width)
        val srcBottom = (srcTop + cropHeight).toInt().coerceAtMost(bitmap.height)

        val srcRect = Rect(srcLeft, srcTop, srcRight, srcBottom)

        val clipPath = Path().apply {
            addRoundRect(destRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
        canvas.restoreToCount(saveCount)
    }

    private fun drawFooter(
        context: Context,
        canvas: Canvas,
        footerRect: RectF,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        captionText: String,
        showDateStamp: Boolean,
        formattedDate: String
    ) {
        val inkColor = when (frameStyle) {
            FrameStyle.WHITE -> Color.parseColor("#1A1A1D")
            FrameStyle.BLACK -> Color.parseColor("#F5F5F7")
            FrameStyle.SUN_YELLOW -> Color.parseColor("#241B00")
            FrameStyle.CREAM_GOLD -> Color.parseColor("#473500")
            FrameStyle.PASTEL_PINK -> Color.parseColor("#4F2433")
            FrameStyle.MINT_GREEN -> Color.parseColor("#134230")
            FrameStyle.LAVENDER -> Color.parseColor("#332054")
            FrameStyle.LIGHT_BLUE -> Color.parseColor("#1A3654")
            FrameStyle.CARO -> Color.parseColor("#4A2531")
            FrameStyle.YELLOW_CARO -> Color.parseColor("#3A2B00")
            FrameStyle.FILM_STRIP -> Color.parseColor("#FFD426")
        }

        val hasPatternPill = frameStyle == FrameStyle.CARO || frameStyle == FrameStyle.YELLOW_CARO
        if (hasPatternPill) {
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (frameStyle == FrameStyle.YELLOW_CARO) {
                    Color.argb(235, 255, 253, 240)
                } else {
                    Color.argb(235, 255, 251, 247)
                }
                style = Paint.Style.FILL
            }
            val pillRect = RectF(
                footerRect.left,
                footerRect.top + 12f,
                footerRect.right,
                footerRect.bottom
            )
            canvas.drawRoundRect(pillRect, 28f, 28f, pillPaint)
        }

        val sansTypeface = Typeface.DEFAULT_BOLD
        val monoTypeface = Typeface.DEFAULT_BOLD

        val brandSize = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 42f
            BoothLayout.PHOTO_STRIP -> 40f
            BoothLayout.GRID_2X2 -> 48f
        }
        val captionSize = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 58f
            BoothLayout.PHOTO_STRIP -> 54f
            BoothLayout.GRID_2X2 -> 64f
        }
        val dateSize = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 36f
            BoothLayout.PHOTO_STRIP -> 34f
            BoothLayout.GRID_2X2 -> 40f
        }

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            typeface = Typeface.create(sansTypeface, Typeface.BOLD)
            textSize = captionSize
            textAlign = Paint.Align.CENTER
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            alpha = 190
            typeface = Typeface.create(monoTypeface, Typeface.BOLD)
            textSize = brandSize
            letterSpacing = 0.14f
            textAlign = Paint.Align.LEFT
        }

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            alpha = 200
            typeface = monoTypeface
            textSize = dateSize
            textAlign = Paint.Align.RIGHT
        }

        val horizontalPadding = if (hasPatternPill) 36f else 8f

        if (captionText.isNotEmpty()) {
            val maxCaptionWidth = footerRect.width() - horizontalPadding * 2f
            var adjustedSize = captionSize
            captionPaint.textSize = adjustedSize
            while (captionPaint.measureText(captionText) > maxCaptionWidth && adjustedSize > 28f) {
                adjustedSize -= 2f
                captionPaint.textSize = adjustedSize
            }
            val captionY = footerRect.top + (footerRect.height() * 0.44f)
            canvas.drawText(captionText, footerRect.centerX(), captionY, captionPaint)
        } else {
            val brandCenterPaint = Paint(brandPaint).apply {
                textAlign = Paint.Align.CENTER
                textSize = brandSize * 1.15f
            }
            val brandY = footerRect.top + (footerRect.height() * 0.44f)
            canvas.drawText("FRIENDS", footerRect.centerX(), brandY, brandCenterPaint)
        }

        val bottomY = footerRect.bottom - 26f
        if (captionText.isNotEmpty()) {
            canvas.drawText(
                "FRIENDS",
                footerRect.left + horizontalPadding,
                bottomY,
                brandPaint
            )
        }
        if (showDateStamp && formattedDate.isNotBlank()) {
            canvas.drawText(
                formattedDate,
                footerRect.right - horizontalPadding,
                bottomY,
                datePaint
            )
        }
    }
}
