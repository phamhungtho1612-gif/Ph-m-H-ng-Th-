package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class FriendsWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_PINNED ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            updateAllWidgets(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateSingleWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val EXTRA_OPEN_FEED = "extra_open_friends_feed"
        const val ACTION_WIDGET_PINNED = "com.example.widget.ACTION_WIDGET_PINNED"

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, FriendsWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                for (id in ids) {
                    updateSingleWidget(appContext, manager, id)
                }
            }
        }

        fun requestPinWidget(context: Context): PinWidgetResult {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return PinWidgetResult.UNSUPPORTED
            }
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) {
                return PinWidgetResult.UNSUPPORTED
            }
            val provider = ComponentName(context, FriendsWidgetProvider::class.java)
            val callbackIntent = Intent(context, FriendsWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_PINNED
            }
            val successCallback = PendingIntent.getBroadcast(
                context,
                9101,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            val success = manager.requestPinAppWidget(provider, null, successCallback)
            return if (success) PinWidgetResult.REQUESTED else PinWidgetResult.UNSUPPORTED
        }

        private fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_friends_photo)
            val snapshot = FriendsWidgetStorage.readSnapshot(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_FEED, snapshot.bitmap != null)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            val bmp = snapshot.bitmap
            if (bmp != null) {
                views.setImageViewBitmap(R.id.widget_photo, bmp)
                views.setViewVisibility(R.id.widget_photo, View.VISIBLE)
                views.setViewVisibility(R.id.widget_scrim, View.VISIBLE)
                views.setViewVisibility(R.id.widget_author_badge, View.VISIBLE)
                views.setViewVisibility(R.id.widget_caption, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_container, View.GONE)

                val authorLine = if (snapshot.timeLabel.isNotBlank()) {
                    "${snapshot.authorName} • ${snapshot.timeLabel}"
                } else {
                    snapshot.authorName
                }
                views.setTextViewText(R.id.widget_author_badge, authorLine)
                views.setTextViewText(R.id.widget_caption, snapshot.caption)
            } else {
                views.setViewVisibility(R.id.widget_photo, View.GONE)
                views.setViewVisibility(R.id.widget_scrim, View.GONE)
                views.setViewVisibility(R.id.widget_author_badge, View.GONE)
                views.setViewVisibility(R.id.widget_caption, View.GONE)
                views.setViewVisibility(R.id.widget_empty_container, View.VISIBLE)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

enum class PinWidgetResult {
    REQUESTED,
    UNSUPPORTED
}
