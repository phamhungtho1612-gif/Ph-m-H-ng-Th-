package com.example.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import com.example.R

enum class AppIconVariant(
    val aliasClassName: String,
    @StringRes val labelRes: Int,
    val previewBgColorHex: Long
) {
    YELLOW(
        aliasClassName = "com.example.LauncherIconYellow",
        labelRes = R.string.icon_variant_yellow,
        previewBgColorHex = 0xFFF3B900
    ),
    DARK(
        aliasClassName = "com.example.LauncherIconDark",
        labelRes = R.string.icon_variant_dark,
        previewBgColorHex = 0xFF141416
    ),
    PINK(
        aliasClassName = "com.example.LauncherIconPink",
        labelRes = R.string.icon_variant_pink,
        previewBgColorHex = 0xFFF7A8C4
    ),
    CLASSIC(
        aliasClassName = "com.example.LauncherIconClassic",
        labelRes = R.string.icon_variant_classic,
        previewBgColorHex = 0xFFFFFFFF
    )
}

object AppIconManager {

    fun getCurrentVariant(context: Context): AppIconVariant {
        val pm = context.packageManager
        return AppIconVariant.entries.firstOrNull { variant ->
            val state = pm.getComponentEnabledSetting(
                ComponentName(context.packageName, variant.aliasClassName)
            )
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIconVariant.YELLOW
    }

    fun applyVariant(context: Context, target: AppIconVariant) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val targetComponent = ComponentName(appContext.packageName, target.aliasClassName)

        if (pm.getComponentEnabledSetting(targetComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return
        }

        // Enable chosen alias first so launcher never loses the app icon entry mid-switch
        pm.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        AppIconVariant.entries.forEach { variant ->
            if (variant != target) {
                val comp = ComponentName(appContext.packageName, variant.aliasClassName)
                pm.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
