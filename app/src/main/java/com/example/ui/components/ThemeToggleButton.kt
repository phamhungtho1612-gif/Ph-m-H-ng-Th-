package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppThemeMode

@Composable
fun ThemeToggleButton(
    themeMode: AppThemeMode,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val icon = when (themeMode) {
        AppThemeMode.LIGHT -> Icons.Rounded.LightMode
        AppThemeMode.DARK -> Icons.Rounded.DarkMode
        AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
    }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("theme_toggle_button")
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.theme_toggle_desc),
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_light)) },
                leadingIcon = {
                    Icon(Icons.Rounded.LightMode, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSelectThemeMode(AppThemeMode.LIGHT)
                },
                modifier = Modifier.testTag("theme_option_light")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_dark)) },
                leadingIcon = {
                    Icon(Icons.Rounded.DarkMode, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSelectThemeMode(AppThemeMode.DARK)
                },
                modifier = Modifier.testTag("theme_option_dark")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.theme_system)) },
                leadingIcon = {
                    Icon(Icons.Rounded.BrightnessAuto, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onSelectThemeMode(AppThemeMode.SYSTEM)
                },
                modifier = Modifier.testTag("theme_option_system")
            )
        }
    }
}
