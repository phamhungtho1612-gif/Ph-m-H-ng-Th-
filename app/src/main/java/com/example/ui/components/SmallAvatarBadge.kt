package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.studioAccentInk

/**
 * Reusable compact circular avatar badge that prefers uploaded [avatarBase64],
 * falls back to Google [photoUrl], and otherwise shows the first initial.
 */
@Composable
fun SmallAvatarBadge(
    displayName: String,
    avatarBase64: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    borderColor: Color? = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
    borderWidth: Dp = 1.2.dp
) {
    val accentInk = studioAccentInk()
    val decodedImageBitmap = remember(avatarBase64) {
        decodeAvatarBitmap(avatarBase64)?.asImageBitmap()
    }
    val borderMod = if (borderColor != null && borderWidth > 0.dp) {
        Modifier.border(borderWidth, borderColor, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(borderMod)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        when {
            decodedImageBitmap != null -> {
                Image(
                    bitmap = decodedImageBitmap,
                    contentDescription = displayName.ifBlank { "Ảnh đại diện" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            !photoUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = displayName.ifBlank { "Ảnh đại diện" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                val letter = displayName.trim().take(1).uppercase().ifEmpty { "F" }
                val textSize = (size.value * 0.44f).coerceIn(9f, 20f).sp
                Text(
                    text = letter,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    color = accentInk
                )
            }
        }
    }
}

fun decodeAvatarBitmap(encoded: String?): Bitmap? {
    if (encoded.isNullOrBlank()) return null
    return runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
