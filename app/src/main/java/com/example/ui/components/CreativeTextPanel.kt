package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FormatShapes
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.imaging.CreativeTextRenderer
import com.example.model.BoothLayout
import com.example.model.CreativeOverlayTextState
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import com.example.model.SubjectAiUiState
import com.example.ui.theme.StudioMintSuccess
import com.example.ui.theme.studioAccentInk
import com.example.viewmodel.PhotoboothViewModel
import kotlin.math.roundToInt

private enum class CreativeSubTab {
    SHAPE,
    STYLE,
    FONT,
    ADJUST
}

@Composable
fun CreativeTextPanel(
    layout: BoothLayout,
    photoCount: Int,
    creativeText: CreativeOverlayTextState,
    subjectAi: SubjectAiUiState,
    onScanSubjectWithAi: () -> Unit,
    onUpdateText: (String) -> Unit,
    onSelectShape: (OverlayTextShape) -> Unit,
    onSelectStyle: (OverlayTextStyle) -> Unit,
    onSelectFont: (OverlayFontStyle) -> Unit,
    onSelectColor: (String) -> Unit,
    onToggleBehindSubject: (Boolean) -> Unit,
    onToggleRepeatAlongShape: (Boolean) -> Unit,
    onUpdateScale: (Float) -> Unit,
    onUpdateLetterSpacing: (Float) -> Unit,
    onUpdateLineSpacing: (Float) -> Unit,
    onUpdatePositionY: (Float) -> Unit,
    onUpdatePositionX: (Float) -> Unit,
    onUpdateBend: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onSelectTargetSlot: (Int) -> Unit,
    onResetTransform: () -> Unit,
    onClearAiError: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val accentInk = studioAccentInk()
    var activeSubTab by remember { mutableStateOf(CreativeSubTab.SHAPE) }

    val quickChips = remember(subjectAi.suggestedCaptions) {
        if (subjectAi.suggestedCaptions.isNotEmpty()) {
            subjectAi.suggestedCaptions
        } else {
            listOf("FRIENDS ♡", "KỶ NIỆM ĐẸP", "HỘI BẠN THÂN", "CHILL PHẾT", "MÃI ĐỈNH")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 290.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. AI Subject Scan + Behind-Subject Depth Toggle Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    onScanSubjectWithAi()
                },
                enabled = !subjectAi.isScanning,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("ai_scan_subject_button")
            ) {
                if (subjectAi.isScanning) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.ai_scanning_subject),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (subjectAi.subjectMasks.any { it != null }) {
                            stringResource(R.string.ai_rescan_subject)
                        } else {
                            stringResource(R.string.ai_scan_subject_btn)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Behind-Subject Toggle Pill
            Surface(
                onClick = {
                    val next = !creativeText.behindSubject
                    onToggleBehindSubject(next)
                    if (next && subjectAi.subjectMasks.none { it != null } && !subjectAi.isScanning) {
                        onScanSubjectWithAi()
                    }
                },
                shape = CircleShape,
                color = if (creativeText.behindSubject) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (creativeText.behindSubject) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("behind_subject_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FlipToBack,
                        contentDescription = null,
                        tint = if (creativeText.behindSubject) accentInk else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Sau chủ thể",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (creativeText.behindSubject) accentInk else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Status / Detected Subject / Error Banner
        if (!subjectAi.segmentationMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioMintSuccess.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, StudioMintSuccess.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subjectAi.segmentationMessage,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        if (!subjectAi.aiErrorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subjectAi.aiErrorMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearAiError,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Multi-slot selector when layout has 4 slots
        if (layout != BoothLayout.SINGLE_PHOTO && photoCount > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    val selected = creativeText.targetSlotIndex == -1
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTargetSlot(-1) },
                        label = { Text(stringResource(R.string.ai_slot_all), fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                items((0 until photoCount.coerceAtMost(4)).toList()) { idx ->
                    val selected = creativeText.targetSlotIndex == idx
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTargetSlot(idx) },
                        label = {
                            Text(stringResource(R.string.ai_slot_item, idx + 1), fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // 2. Text Input + AI Suggestion Chips
        OutlinedTextField(
            value = creativeText.text,
            onValueChange = onUpdateText,
            label = { Text(stringResource(R.string.ai_text_input_label)) },
            placeholder = { Text(stringResource(R.string.ai_text_input_placeholder)) },
            singleLine = true,
            trailingIcon = {
                if (creativeText.text.isNotEmpty()) {
                    IconButton(onClick = { onUpdateText("") }) {
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
                .testTag("creative_text_input")
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickChips) { suggestion ->
                val selected = creativeText.text == suggestion
                FilterChip(
                    selected = selected,
                    onClick = {
                        onUpdateText(if (selected) "" else suggestion)
                        focusManager.clearFocus()
                    },
                    leadingIcon = if (subjectAi.suggestedCaptions.contains(suggestion)) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    label = {
                        Text(
                            text = suggestion,
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

        // Quick Peeking Adjustment Card (size + letter spacing always handy when text is non-blank outside ADJUST)
        if (creativeText.text.isNotBlank() && activeSubTab != CreativeSubTab.ADJUST) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_peek_quick_title),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { activeSubTab = CreativeSubTab.ADJUST },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = "Thêm",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentInk
                            )
                        }
                    }

                    CompactSliderRow(
                        label = stringResource(R.string.ai_slider_size),
                        valueText = "${(creativeText.scale * 100).roundToInt()}%",
                        value = creativeText.scale,
                        valueRange = 0.45f..3.5f,
                        onValueChange = onUpdateScale,
                        sliderModifier = Modifier.testTag("quick_ai_scale_slider")
                    )

                    val spacingPct = (creativeText.letterSpacingEm * 100).roundToInt()
                    CompactSliderRow(
                        label = stringResource(R.string.ai_slider_letter_spacing),
                        valueText = "${if (spacingPct >= 0) "+" else ""}$spacingPct%",
                        value = creativeText.letterSpacingEm,
                        valueRange = -0.05f..0.45f,
                        onValueChange = onUpdateLetterSpacing,
                        sliderModifier = Modifier.testTag("quick_ai_letter_spacing_slider")
                    )
                }
            }
        }

        // 3. 4 Creative Studio Sub-Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SubTabChip(
                label = stringResource(R.string.ai_subtab_shape),
                icon = Icons.Rounded.FormatShapes,
                selected = activeSubTab == CreativeSubTab.SHAPE,
                onClick = { activeSubTab = CreativeSubTab.SHAPE },
                modifier = Modifier.weight(1f)
            )
            SubTabChip(
                label = stringResource(R.string.ai_subtab_style),
                icon = Icons.Rounded.AutoAwesome,
                selected = activeSubTab == CreativeSubTab.STYLE,
                onClick = { activeSubTab = CreativeSubTab.STYLE },
                modifier = Modifier.weight(1f)
            )
            SubTabChip(
                label = stringResource(R.string.ai_subtab_font),
                icon = Icons.Rounded.TextFields,
                selected = activeSubTab == CreativeSubTab.FONT,
                onClick = { activeSubTab = CreativeSubTab.FONT },
                modifier = Modifier.weight(1f)
            )
            SubTabChip(
                label = stringResource(R.string.ai_subtab_adjust),
                icon = Icons.Rounded.Tune,
                selected = activeSubTab == CreativeSubTab.ADJUST,
                onClick = { activeSubTab = CreativeSubTab.ADJUST },
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Active Sub-Tab Controls
        when (activeSubTab) {
            CreativeSubTab.SHAPE -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(OverlayTextShape.entries, key = { it.name }) { shape ->
                        val isSelected = shape == creativeText.shape
                        Surface(
                            onClick = { onSelectShape(shape) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .width(84.dp)
                                .testTag("shape_${shape.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = shapeIcon(shape),
                                    contentDescription = stringResource(shape.labelRes),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(shape.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Loop-repeat toggle for curved shapes (Heart, Circle, Arc, Wave)
                if (creativeText.shape == OverlayTextShape.HEART ||
                    creativeText.shape == OverlayTextShape.CIRCLE ||
                    creativeText.shape == OverlayTextShape.WAVE ||
                    creativeText.shape == OverlayTextShape.ARC_UP ||
                    creativeText.shape == OverlayTextShape.ARC_DOWN
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onToggleRepeatAlongShape(!creativeText.repeatAlongShape) }
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = accentInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ai_repeat_shape_toggle),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Switch(
                            checked = creativeText.repeatAlongShape,
                            onCheckedChange = onToggleRepeatAlongShape,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            CreativeSubTab.STYLE -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(OverlayTextStyle.entries, key = { it.name }) { style ->
                        val isSelected = style == creativeText.style
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectStyle(style) }
                                .padding(vertical = 2.dp)
                                .testTag("text_style_${style.name.lowercase()}")
                        ) {
                            StylePreviewTile(
                                style = style,
                                isSelected = isSelected
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(style.labelRes),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) accentInk else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            CreativeSubTab.FONT -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(OverlayFontStyle.entries, key = { it.name }) { font ->
                        val isSelected = font == creativeText.font
                        val composeFont = remember(font) {
                            FontFamily(CreativeTextRenderer.resolveTypeface(context, font))
                        }
                        Surface(
                            onClick = { onSelectFont(font) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = if (isSelected) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                            } else null,
                            modifier = Modifier
                                .width(92.dp)
                                .testTag("font_${font.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Việt Nam",
                                    fontFamily = composeFont,
                                    fontSize = 15.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(font.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            CreativeSubTab.ADJUST -> {
                // Color Swatches
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CreativeTextRenderer.curatedPaletteHex, key = { it }) { hex ->
                        val selected = creativeText.colorHex.equals(hex, ignoreCase = true)
                        val swatchColor = remember(hex) {
                            Color(AndroidColor.parseColor(hex))
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                                .clickable { onSelectColor(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (hex == "#FFFFFF" || hex == "#FFD426" || hex == "#FFE8A3") {
                                        Color(0xFF141416)
                                    } else {
                                        Color.White
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Behind-subject Depth Toggle + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ai_behind_subject_toggle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = creativeText.behindSubject,
                        onCheckedChange = onToggleBehindSubject,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_size),
                    valueText = "${(creativeText.scale * 100).roundToInt()}%",
                    value = creativeText.scale,
                    valueRange = 0.45f..3.5f,
                    onValueChange = onUpdateScale,
                    sliderModifier = Modifier.testTag("ai_scale_slider")
                )

                val letterPct = (creativeText.letterSpacingEm * 100).roundToInt()
                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_letter_spacing),
                    valueText = "${if (letterPct >= 0) "+" else ""}$letterPct%",
                    value = creativeText.letterSpacingEm,
                    valueRange = -0.05f..0.45f,
                    onValueChange = onUpdateLetterSpacing,
                    sliderModifier = Modifier.testTag("ai_letter_spacing_slider")
                )

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_line_spacing),
                    valueText = "${(creativeText.lineSpacingMultiplier * 100).roundToInt()}%",
                    value = creativeText.lineSpacingMultiplier,
                    valueRange = 0.75f..1.45f,
                    onValueChange = onUpdateLineSpacing,
                    sliderModifier = Modifier.testTag("ai_line_spacing_slider")
                )

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_pos_y),
                    valueText = "${(creativeText.centerYFraction * 100).roundToInt()}%",
                    value = creativeText.centerYFraction,
                    valueRange = 0.14f..0.86f,
                    onValueChange = onUpdatePositionY
                )

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_pos_x),
                    valueText = "${(creativeText.centerXFraction * 100).roundToInt()}%",
                    value = creativeText.centerXFraction,
                    valueRange = 0.20f..0.80f,
                    onValueChange = onUpdatePositionX
                )

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_bend),
                    valueText = "${creativeText.bendAngleDegrees.roundToInt()}°",
                    value = creativeText.bendAngleDegrees,
                    valueRange = -45f..45f,
                    onValueChange = onUpdateBend
                )

                CompactSliderRow(
                    label = stringResource(R.string.ai_slider_opacity),
                    valueText = "${(creativeText.opacity * 100).roundToInt()}%",
                    value = creativeText.opacity,
                    valueRange = 0.35f..1.0f,
                    onValueChange = onUpdateOpacity
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onResetTransform) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_reset_transform))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubTabChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) studioAccentInk() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) studioAccentInk() else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StylePreviewTile(
    style: OverlayTextStyle,
    isSelected: Boolean
) {
    val bgBrush = when (style) {
        OverlayTextStyle.GRADIENT_GOLD -> Brush.linearGradient(
            listOf(Color(0xFFFFF8CE), Color(0xFFFFD426), Color(0xFFD98E00))
        )
        OverlayTextStyle.GRADIENT_SUNSET -> Brush.linearGradient(
            listOf(Color(0xFFFF4D6D), Color(0xFFFF8A3D), Color(0xFFFFE066))
        )
        OverlayTextStyle.GRADIENT_OCEAN -> Brush.linearGradient(
            listOf(Color(0xFF80FFDB), Color(0xFF4CC9F0), Color(0xFF4361EE))
        )
        OverlayTextStyle.GRADIENT_RAINBOW -> Brush.linearGradient(
            listOf(Color(0xFFFF595E), Color(0xFFFFCA3A), Color(0xFF2EC4B6), Color(0xFFB5179E))
        )
        OverlayTextStyle.CHROME_METALLIC -> Brush.verticalGradient(
            listOf(Color.White, Color(0xFF94A3B8), Color(0xFF334155))
        )
        OverlayTextStyle.NEON_GLOW -> Brush.linearGradient(
            listOf(Color(0xFF141416), Color(0xFF2A1845))
        )
        OverlayTextStyle.FROSTED_GLASS -> Brush.linearGradient(
            listOf(Color(0xFF6B7280), Color(0xFF9CA3AF))
        )
        else -> Brush.linearGradient(
            listOf(Color(0xFF232328), Color(0xFF141416))
        )
    }

    val textColor = when (style) {
        OverlayTextStyle.NEON_GLOW -> Color(0xFFFFD426)
        OverlayTextStyle.CANDY_STICKER -> Color(0xFFFF5CA8)
        OverlayTextStyle.GLITCH_RGB -> Color(0xFF4DF3FF)
        OverlayTextStyle.GRADIENT_GOLD,
        OverlayTextStyle.GRADIENT_SUNSET,
        OverlayTextStyle.GRADIENT_OCEAN,
        OverlayTextStyle.GRADIENT_RAINBOW,
        OverlayTextStyle.CHROME_METALLIC -> Color(0xFF141416)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgBrush)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Aa",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = textColor
        )
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    sliderModifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = studioAccentInk()
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = sliderModifier.height(28.dp)
        )
    }
}

private fun shapeIcon(shape: OverlayTextShape): ImageVector = when (shape) {
    OverlayTextShape.STRAIGHT -> Icons.Rounded.ShortText
    OverlayTextShape.ARC_UP -> Icons.Rounded.TrendingUp
    OverlayTextShape.ARC_DOWN -> Icons.Rounded.TrendingDown
    OverlayTextShape.HEART -> Icons.Rounded.Favorite
    OverlayTextShape.CIRCLE -> Icons.Rounded.RadioButtonUnchecked
    OverlayTextShape.WAVE -> Icons.Rounded.Waves
    OverlayTextShape.MAGAZINE_STACK -> Icons.Rounded.ViewAgenda
    OverlayTextShape.DIAGONAL_UP -> Icons.Rounded.TrendingUp
    OverlayTextShape.DIAGONAL_DOWN -> Icons.Rounded.TrendingDown
    OverlayTextShape.RIBBON_V -> Icons.Rounded.Palette
}
