package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = PhotoboothYellowDark,
    onPrimary = StudioBlack,
    primaryContainer = PhotoboothYellowSoftDark,
    onPrimaryContainer = Color(0xFFFFEA8A),
    secondary = Color.White,
    onSecondary = StudioBlack,
    secondaryContainer = StudioSurfaceElevatedDark,
    onSecondaryContainer = Color(0xFFFAFAFA),
    background = StudioBlack,
    onBackground = Color(0xFFFAFAFA),
    surface = StudioSurfaceDark,
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = StudioSurfaceElevatedDark,
    onSurfaceVariant = StudioInkMutedDark,
    outline = Color(0xFF383840),
    error = Color(0xFFFF6B6B)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = PhotoboothYellowLight,
    onPrimary = StudioInkDark,
    primaryContainer = PhotoboothYellowSoft,
    onPrimaryContainer = Color(0xFF2B2000),
    secondary = StudioInkDark,
    onSecondary = Color.White,
    secondaryContainer = StudioSurfaceVariantLight,
    onSecondaryContainer = StudioInkDark,
    background = StudioWhite,
    onBackground = StudioInkDark,
    surface = Color.White,
    onSurface = StudioInkDark,
    surfaceVariant = StudioSurfaceVariantLight,
    onSurfaceVariant = StudioInkMutedLight,
    outline = Color(0xFFDADAE0),
    error = Color(0xFFD93848)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * High-contrast yellow/amber ink suitable for text or icons on neutral white/black surfaces.
 */
@Composable
fun studioAccentInk(): Color {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) PhotoboothYellowDark else PhotoboothAmberInk
}

@Composable
fun MyPhotoboothTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
