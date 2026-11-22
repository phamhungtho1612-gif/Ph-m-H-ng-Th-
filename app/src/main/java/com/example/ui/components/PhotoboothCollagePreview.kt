package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.imaging.CreativeTextRenderer
import com.example.imaging.PhotoboothFilters
import com.example.model.BoothLayout
import com.example.model.CreativeOverlayTextState
import com.example.model.FrameStyle
import com.example.model.PhotoFilter
import com.example.ui.theme.BeVietnamProFontFamily
import com.example.ui.theme.FrameBlackBg
import com.example.ui.theme.FrameBlackInk
import com.example.ui.theme.FrameCaroBaseBg
import com.example.ui.theme.FrameCaroInk
import com.example.ui.theme.FrameCaroStripe
import com.example.ui.theme.FrameCreamGoldBg
import com.example.ui.theme.FrameCreamGoldInk
import com.example.ui.theme.FrameFilmStripBg
import com.example.ui.theme.FrameFilmStripHole
import com.example.ui.theme.FrameFilmStripInk
import com.example.ui.theme.FrameLavenderBg
import com.example.ui.theme.FrameLavenderInk
import com.example.ui.theme.FrameLightBlueBg
import com.example.ui.theme.FrameLightBlueInk
import com.example.ui.theme.FrameMintGreenBg
import com.example.ui.theme.FrameMintGreenInk
import com.example.ui.theme.FramePastelPinkBg
import com.example.ui.theme.FramePastelPinkInk
import com.example.ui.theme.FrameSunYellowBg
import com.example.ui.theme.FrameSunYellowInk
import com.example.ui.theme.FrameWhiteBg
import com.example.ui.theme.FrameWhiteInk
import com.example.ui.theme.FrameYellowCaroBaseBg
import com.example.ui.theme.FrameYellowCaroInk
import com.example.ui.theme.FrameYellowCaroStripe
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun PhotoboothCollagePreview(
    photos: List<Bitmap>,
    layout: BoothLayout,
    frameStyle: FrameStyle,
    filter: PhotoFilter,
    captionText: String,
    showDateStamp: Boolean,
    formattedDate: String,
    modifier: Modifier = Modifier,
    creativeText: CreativeOverlayTextState = CreativeOverlayTextState(),
    subjectMasks: List<Bitmap?> = emptyList(),
    isScanningSubject: Boolean = false,
    showSlotSelectionRing: Boolean = false,
    onSelectSlot: ((Int) -> Unit)? = null,
    onNudgeCreativeText: ((deltaXFraction: Float, deltaYFraction: Float, zoomFactor: Float, rotationDelta: Float) -> Unit)? = null
) {
    val colorFilter = remember(filter) { PhotoboothFilters.toComposeColorFilter(filter) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val aspectRatio = when (layout) {
            BoothLayout.SINGLE_PHOTO -> 1600f / 2040f
            BoothLayout.PHOTO_STRIP -> 1200f / 3560f
            BoothLayout.GRID_2X2 -> 2000f / 2440f
        }

        val availableW = maxWidth
        val availableH = maxHeight
        val targetWidth: Dp
        val targetHeight: Dp

        if (availableW / availableH > aspectRatio) {
            targetHeight = availableH
            targetWidth = availableH * aspectRatio
        } else {
            targetWidth = availableW
            targetHeight = availableW / aspectRatio
        }

        val inkColor = frameInkColor(frameStyle)
        val density = LocalDensity.current

        val horizontalPadding = targetWidth * when (layout) {
            BoothLayout.SINGLE_PHOTO -> 0.060f
            BoothLayout.PHOTO_STRIP -> 0.063f
            BoothLayout.GRID_2X2 -> 0.046f
        }
        val topPadding = horizontalPadding
        val slotGap = targetWidth * (if (layout == BoothLayout.PHOTO_STRIP) 0.030f else 0.022f)
        val slotCorner = targetWidth * when (layout) {
            BoothLayout.SINGLE_PHOTO -> 0.018f
            BoothLayout.PHOTO_STRIP -> 0.017f
            BoothLayout.GRID_2X2 -> 0.012f
        }

        val captionFontSize = with(density) {
            (targetWidth * when (layout) {
                BoothLayout.SINGLE_PHOTO -> 0.036f
                BoothLayout.PHOTO_STRIP -> 0.045f
                BoothLayout.GRID_2X2 -> 0.032f
            }).toSp()
        }
        val brandFontSize = with(density) {
            (targetWidth * when (layout) {
                BoothLayout.SINGLE_PHOTO -> 0.026f
                BoothLayout.PHOTO_STRIP -> 0.033f
                BoothLayout.GRID_2X2 -> 0.024f
            }).toSp()
        }
        val dateFontSize = with(density) {
            (targetWidth * when (layout) {
                BoothLayout.SINGLE_PHOTO -> 0.022f
                BoothLayout.PHOTO_STRIP -> 0.028f
                BoothLayout.GRID_2X2 -> 0.020f
            }).toSp()
        }

        Surface(
            modifier = Modifier
                .padding(4.dp)
                .height(targetHeight)
                .aspectRatio(aspectRatio),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 14.dp,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .frameBackground(frameStyle)
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = topPadding,
                        bottom = topPadding * 0.65f
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    fun isSlotTargeted(index: Int): Boolean {
                        return layout == BoothLayout.SINGLE_PHOTO && index == 0 ||
                            creativeText.targetSlotIndex == -1 ||
                            creativeText.targetSlotIndex == index
                    }

                    when (layout) {
                        BoothLayout.SINGLE_PHOTO -> {
                            PhotoSlot(
                                bitmap = photos.firstOrNull(),
                                maskBitmap = subjectMasks.firstOrNull(),
                                creativeText = creativeText,
                                applyCreativeText = true,
                                isScanningSubject = isScanningSubject,
                                showSelectedRing = false,
                                enableGestureEdit = showSlotSelectionRing && creativeText.text.isNotBlank(),
                                onNudgeCreativeText = onNudgeCreativeText,
                                onClick = null,
                                colorFilter = colorFilter,
                                cornerRadius = slotCorner,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        BoothLayout.PHOTO_STRIP -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(slotGap)
                            ) {
                                for (index in 0 until 4) {
                                    val targeted = isSlotTargeted(index)
                                    PhotoSlot(
                                        bitmap = photos.getOrNull(index),
                                        maskBitmap = subjectMasks.getOrNull(index),
                                        creativeText = creativeText,
                                        applyCreativeText = targeted,
                                        isScanningSubject = isScanningSubject && targeted,
                                        showSelectedRing = showSlotSelectionRing && creativeText.targetSlotIndex == index,
                                        enableGestureEdit = showSlotSelectionRing && targeted && creativeText.text.isNotBlank(),
                                        onNudgeCreativeText = onNudgeCreativeText,
                                        onClick = onSelectSlot?.let { cb -> { cb(index) } },
                                        colorFilter = colorFilter,
                                        cornerRadius = slotCorner,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                }
                            }
                        }

                        BoothLayout.GRID_2X2 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(slotGap)
                            ) {
                                for (row in 0 until 2) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(slotGap)
                                    ) {
                                        for (col in 0 until 2) {
                                            val index = row * 2 + col
                                            val targeted = isSlotTargeted(index)
                                            PhotoSlot(
                                                bitmap = photos.getOrNull(index),
                                                maskBitmap = subjectMasks.getOrNull(index),
                                                creativeText = creativeText,
                                                applyCreativeText = targeted,
                                                isScanningSubject = isScanningSubject && targeted,
                                                showSelectedRing = showSlotSelectionRing && creativeText.targetSlotIndex == index,
                                                enableGestureEdit = showSlotSelectionRing && targeted && creativeText.text.isNotBlank(),
                                                onNudgeCreativeText = onNudgeCreativeText,
                                                onClick = onSelectSlot?.let { cb -> { cb(index) } },
                                                colorFilter = colorFilter,
                                                cornerRadius = slotCorner,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(targetHeight * 0.016f))

                // Footer region (caption below frame + date stamp)
                val cleanCaption = captionText.trim()
                val footerHeightFraction = when (layout) {
                    BoothLayout.SINGLE_PHOTO -> 0.145f
                    BoothLayout.PHOTO_STRIP -> 0.115f
                    BoothLayout.GRID_2X2 -> 0.145f
                }
                val hasPatternPill = frameStyle == FrameStyle.CARO || frameStyle == FrameStyle.YELLOW_CARO
                val footerModifier = Modifier
                    .fillMaxWidth()
                    .height(targetHeight * footerHeightFraction)
                    .let { mod ->
                        if (hasPatternPill) {
                            val pillColor = if (frameStyle == FrameStyle.YELLOW_CARO) {
                                FrameYellowCaroBaseBg.copy(alpha = 0.92f)
                            } else {
                                FrameCaroBaseBg.copy(alpha = 0.92f)
                            }
                            mod
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillColor)
                                .padding(horizontal = horizontalPadding * 0.4f)
                        } else {
                            mod
                        }
                    }

                Column(
                    modifier = footerModifier,
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cleanCaption.isNotEmpty()) {
                            Text(
                                text = cleanCaption,
                                fontFamily = BeVietnamProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = captionFontSize,
                                color = inkColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "FRIENDS",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = brandFontSize * 1.1f,
                                color = inkColor.copy(alpha = 0.78f),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = targetHeight * 0.008f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cleanCaption.isNotEmpty()) {
                            Text(
                                text = "FRIENDS",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = brandFontSize,
                                color = inkColor.copy(alpha = 0.74f),
                                maxLines = 1
                            )
                        } else {
                            Spacer(modifier = Modifier)
                        }

                        if (showDateStamp && formattedDate.isNotBlank()) {
                            Text(
                                text = formattedDate,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = dateFontSize,
                                color = inkColor.copy(alpha = 0.78f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSlot(
    bitmap: Bitmap?,
    maskBitmap: Bitmap?,
    creativeText: CreativeOverlayTextState,
    applyCreativeText: Boolean,
    isScanningSubject: Boolean,
    showSelectedRing: Boolean,
    enableGestureEdit: Boolean,
    onNudgeCreativeText: ((Float, Float, Float, Float) -> Unit)?,
    onClick: (() -> Unit)?,
    colorFilter: ColorFilter?,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    val maskImageBitmap = remember(maskBitmap) { maskBitmap?.asImageBitmap() }
    val latestNudge by rememberUpdatedState(onNudgeCreativeText)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF232328))
            .then(
                if (enableGestureEdit && latestNudge != null) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            latestNudge?.invoke(pan.x / w, pan.y / h, zoom, rotation)
                        }
                    }
                } else if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .then(
                if (showSelectedRing) {
                    Modifier.border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (applyCreativeText && creativeText.text.isNotBlank()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val rect = RectF(0f, 0f, size.width, size.height)
                drawIntoCanvas { canvas ->
                    CreativeTextRenderer.drawCreativeTextInSlot(
                        context = context,
                        canvas = canvas.nativeCanvas,
                        slotRect = rect,
                        cornerRadius = 0f,
                        overlay = creativeText
                    )
                }
            }
        }

        if (applyCreativeText && creativeText.behindSubject && maskImageBitmap != null) {
            Image(
                bitmap = maskImageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isScanningSubject) {
            SubjectScanBeamOverlay(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SubjectScanBeamOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "subject_scan")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )

    Canvas(modifier = modifier) {
        drawRect(color = Color.Black.copy(alpha = 0.22f))
        val bandHeight = (size.height * 0.16f).coerceAtLeast(18f)
        val beamCenterY = progress * size.height
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x99FFD426),
                    Color(0xEEFFF7C2),
                    Color(0x99FFD426),
                    Color.Transparent
                ),
                startY = beamCenterY - bandHeight,
                endY = beamCenterY + bandHeight
            )
        )
    }
}

fun Modifier.frameBackground(frameStyle: FrameStyle): Modifier = when (frameStyle) {
    FrameStyle.WHITE -> this.background(FrameWhiteBg)
    FrameStyle.BLACK -> this.background(FrameBlackBg)
    FrameStyle.SUN_YELLOW -> this.background(FrameSunYellowBg)
    FrameStyle.CREAM_GOLD -> this.background(FrameCreamGoldBg)
    FrameStyle.PASTEL_PINK -> this.background(FramePastelPinkBg)
    FrameStyle.MINT_GREEN -> this.background(FrameMintGreenBg)
    FrameStyle.LAVENDER -> this.background(FrameLavenderBg)
    FrameStyle.LIGHT_BLUE -> this.background(FrameLightBlueBg)
    FrameStyle.CARO -> checkeredModifier(FrameCaroBaseBg, FrameCaroStripe)
    FrameStyle.YELLOW_CARO -> checkeredModifier(FrameYellowCaroBaseBg, FrameYellowCaroStripe)
    FrameStyle.FILM_STRIP -> this
        .background(FrameFilmStripBg)
        .drawBehind {
            val inset = size.width * 0.013f
            val holeW = (size.width * 0.018f).coerceAtLeast(3f)
            val holeH = (size.width * 0.025f).coerceAtLeast(4f)
            val step = (size.width * 0.052f).coerceAtLeast(8f)
            val corner = CornerRadius(holeW * 0.25f, holeW * 0.25f)
            var y = inset * 1.4f
            while (y + holeH < size.height - inset) {
                drawRoundRect(
                    color = FrameFilmStripHole,
                    topLeft = Offset(inset, y),
                    size = Size(holeW, holeH),
                    cornerRadius = corner
                )
                drawRoundRect(
                    color = FrameFilmStripHole,
                    topLeft = Offset(size.width - inset - holeW, y),
                    size = Size(holeW, holeH),
                    cornerRadius = corner
                )
                y += step
            }
        }
}

private fun Modifier.checkeredModifier(baseColor: Color, stripeColor: Color): Modifier =
    this
        .background(baseColor)
        .drawBehind {
            val cell = (size.minDimension / 12f).coerceAtLeast(6f)
            var x = 0f
            var col = 0
            while (x < size.width) {
                if (col % 2 == 1) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(x, 0f),
                        size = Size(cell, size.height)
                    )
                }
                x += cell
                col++
            }
            var y = 0f
            var row = 0
            while (y < size.height) {
                if (row % 2 == 1) {
                    drawRect(
                        color = stripeColor,
                        topLeft = Offset(0f, y),
                        size = Size(size.width, cell)
                    )
                }
                y += cell
                row++
            }
        }

fun frameInkColor(frameStyle: FrameStyle): Color = when (frameStyle) {
    FrameStyle.WHITE -> FrameWhiteInk
    FrameStyle.BLACK -> FrameBlackInk
    FrameStyle.SUN_YELLOW -> FrameSunYellowInk
    FrameStyle.CREAM_GOLD -> FrameCreamGoldInk
    FrameStyle.PASTEL_PINK -> FramePastelPinkInk
    FrameStyle.MINT_GREEN -> FrameMintGreenInk
    FrameStyle.LAVENDER -> FrameLavenderInk
    FrameStyle.LIGHT_BLUE -> FrameLightBlueInk
    FrameStyle.CARO -> FrameCaroInk
    FrameStyle.YELLOW_CARO -> FrameYellowCaroInk
    FrameStyle.FILM_STRIP -> FrameFilmStripInk
}
