package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val Context.themeDataStore by preferencesDataStore(name = "photobooth_settings")

class ThemePreferencesRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val countdownSecondsKey = intPreferencesKey("countdown_seconds")
    private val appIconVariantKey = stringPreferencesKey("app_icon_variant")
    private val notificationsEnabledKey = booleanPreferencesKey("notifications_enabled")

    val themeMode: Flow<AppThemeMode> = appContext.themeDataStore.data.map { prefs ->
        val raw = prefs[themeModeKey]
        AppThemeMode.entries.firstOrNull { it.name == raw } ?: AppThemeMode.SYSTEM
    }

    val countdownSeconds: Flow<Int> = appContext.themeDataStore.data.map { prefs ->
        (prefs[countdownSecondsKey] ?: DEFAULT_COUNTDOWN_SECONDS).coerceIn(0, 10)
    }

    val appIconVariant: Flow<AppIconVariant> = appContext.themeDataStore.data.map { prefs ->
        val raw = prefs[appIconVariantKey]
        AppIconVariant.entries.firstOrNull { it.name == raw } ?: AppIconVariant.YELLOW
    }

    val notificationsEnabled: Flow<Boolean> = appContext.themeDataStore.data.map { prefs ->
        prefs[notificationsEnabledKey] ?: true
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        appContext.themeDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        appContext.themeDataStore.edit { prefs ->
            prefs[countdownSecondsKey] = seconds.coerceIn(0, 10)
        }
    }

    suspend fun setAppIconVariant(variant: AppIconVariant) {
        appContext.themeDataStore.edit { prefs ->
            prefs[appIconVariantKey] = variant.name
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.themeDataStore.edit { prefs ->
            prefs[notificationsEnabledKey] = enabled
        }
    }

    private companion object {
        const val DEFAULT_COUNTDOWN_SECONDS = 3
    }
}
