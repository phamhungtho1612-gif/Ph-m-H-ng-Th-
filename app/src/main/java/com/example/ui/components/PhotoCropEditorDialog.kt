package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.imaging.CropAdjustParams
import com.example.imaging.CropAspectPreset
import com.example.imaging.ImageProcessing
import com.example.ui.theme.studioAccentInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun PhotoCropEditorDialog(
    sourceBitmap: Bitmap,
    slotLabel: String,
    onApply: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val accentInk = studioAccentInk()

    var rotationDegrees by remember(sourceBitmap) { mutableIntStateOf(0) }
    var flipHorizontal by remember(sourceBitmap) { mutableStateOf(false) }
    var aspectPreset by remember(sourceBitmap) { mutableStateOf(CropAspectPreset.FREE) }
    var zoom by remember(sourceBitmap) { mutableFloatStateOf(1f) }
    var offsetXFraction by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var offsetYFraction by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var brightness by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var contrast by remember(sourceBitmap) { mutableFloatStateOf(1f) }
    var isProcessing by remember { mutableStateOf(false) }

    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

    val colorFilter = remember(brightness, contrast) {
        val c = contrast.coerceIn(0.6f, 1.6f)
        val b = brightness.coerceIn(-100f, 100f) * 0.8f
        val t = (1f - c) * 128f + b
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    val effectiveRawAspect = remember(sourceBitmap, rotationDegrees) {
        val rot = ((rotationDegrees % 360) + 360) % 360
        if (rot == 90 || rot == 270) {
            sourceBitmap.height.toFloat() / sourceBitmap.width.toFloat().coerceAtLeast(1f)
        } else {
            sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat().coerceAtLeast(1f)
        }
    }

    val cropAspect = aspectPreset.ratio ?: effectiveRawAspect

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (!isProcessing) onDismiss() },
                        modifier = Modifier.testTag("crop_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.crop_editor_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (slotLabel.isNotBlank()) {
                            Text(
                                text = slotLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isProcessing) return@Button
                            isProcessing = true
                            scope.launch {
                                val params = CropAdjustParams(
                                    rotationDegrees = rotationDegrees,
                                    flipHorizontal = flipHorizontal,
                                    aspectPreset = aspectPreset,
                                    zoom = zoom,
                                    offsetXFraction = offsetXFraction,
                                    offsetYFraction = offsetYFraction,
                                    brightness = brightness,
                                    contrast = contrast
                                )
                                val result = withContext(Dispatchers.Default) {
                                    ImageProcessing.applyCropAndAdjust(sourceBitmap, params)
                                }
                                isProcessing = false
                                onApply(result)
                            }
                        },
                        enabled = !isProcessing,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("crop_apply_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.crop_apply_btn),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Crop Viewport Stage
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.92f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val availW = maxWidth
                    val availH = maxHeight
                    val frameW: androidx.compose.ui.unit.Dp
                    val frameH: androidx.compose.ui.unit.Dp
                    if (availW / availH > cropAspect) {
                        frameH = availH
                        frameW = availH * cropAspect
                    } else {
                        frameW = availW
                        frameH = availW / cropAspect
                    }

                    val maxTranslationFactor = max(0f, zoom - 1f)
                    Box(
                        modifier = Modifier
                            .size(width = frameW, height = frameH)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .pointerInput(aspectPreset, zoom) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    val nextZoom = (zoom * gestureZoom).coerceIn(1f, 3.5f)
                                    zoom = nextZoom
                                    val denomX = (size.width / 2f).coerceAtLeast(1f)
                                    val denomY = (size.height / 2f).coerceAtLeast(1f)
                                    offsetXFraction = (offsetXFraction + pan.x / denomX)
                                        .coerceIn(-1f, 1f)
                                    offsetYFraction = (offsetYFraction + pan.y / denomY)
                                        .coerceIn(-1f, 1f)
                                }
                            }
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = stringResource(R.string.crop_editor_title),
                            contentScale = ContentScale.Crop,
                            colorFilter = colorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom * (if (flipHorizontal) -1f else 1f)
                                    scaleY = zoom
                                    rotationZ = rotationDegrees.toFloat()
                                    translationX = offsetXFraction * (size.width * 0.35f * maxTranslationFactor.coerceAtLeast(0.4f))
                                    translationY = offsetYFraction * (size.height * 0.35f * maxTranslationFactor.coerceAtLeast(0.4f))
                                }
                        )

                        // Rule-of-thirds overlay lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineColor = Color.White.copy(alpha = 0.32f)
                            val stroke = 1.dp.toPx()
                            for (i in 1..2) {
                                val x = size.width * i / 3f
                                val y = size.height * i / 3f
                                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), stroke)
                                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), stroke)
                            }
                        }
                    }
                }

                // Bottom Controls Deck
                Surface(
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Aspect Ratio Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(CropAspectPreset.entries) { preset ->
                                val selected = aspectPreset == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = { aspectPreset = preset },
                                    label = { Text(preset.label, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("crop_aspect_${preset.name.lowercase()}")
                                )
                            }
                        }

                        // Rotate / Flip / Reset Quick Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("crop_rotate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.RotateRight,
                                    contentDescription = null,
                                    tint = accentInk,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xoay 90°", color = accentInk, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { flipHorizontal = !flipHorizontal },
                                shape = CircleShape,
                                border = if (flipHorizontal) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("crop_flip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Flip,
                                    contentDescription = null,
                                    tint = accentInk,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lật", color = accentInk, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    rotationDegrees = 0
                                    flipHorizontal = false
                                    aspectPreset = CropAspectPreset.FREE
                                    zoom = 1f
                                    offsetXFraction = 0f
                                    offsetYFraction = 0f
                                    brightness = 0f
                                    contrast = 1f
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("crop_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Đặt lại", maxLines = 1)
                            }
                        }

                        // Zoom + Brightness + Contrast Sliders
                        CropSliderRow(
                            icon = Icons.Rounded.ZoomIn,
                            label = "Thu phóng",
                            valueText = "${String.format("%.1f", zoom)}×",
                            value = zoom,
                            onValueChange = { zoom = it },
                            valueRange = 1f..3.5f
                        )
                        CropSliderRow(
                            icon = Icons.Rounded.Brightness6,
                            label = "Độ sáng",
                            valueText = brightness.roundToInt().toString(),
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = -50f..50f
                        )
                        CropSliderRow(
                            icon = Icons.Rounded.Contrast,
                            label = "Tương phản",
                            valueText = "${(contrast * 100).roundToInt()}%",
                            value = contrast,
                            onValueChange = { contrast = it },
                            valueRange = 0.7f..1.5f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = studioAccentInk(),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(76.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
    }
}
