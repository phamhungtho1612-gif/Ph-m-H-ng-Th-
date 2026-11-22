package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.model.CirclePost
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

data class WidgetSnapshot(
    val postId: String,
    val authorName: String,
    val caption: String,
    val timeLabel: String,
    val bitmap: Bitmap?
)

object FriendsWidgetStorage {
    private const val PREFS_NAME = "friends_widget_prefs"
    private const val KEY_POST_ID = "post_id"
    private const val KEY_AUTHOR = "author_name"
    private const val KEY_CAPTION = "caption"
    private const val KEY_TIME_LABEL = "time_label"
    private const val PHOTO_FILE_NAME = "friends_widget_latest.jpg"
    private const val MAX_WIDGET_DIM_PX = 300

    fun saveLatestPost(context: Context, post: CirclePost?) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val photoFile = File(appContext.filesDir, PHOTO_FILE_NAME)

        if (post == null || post.imageBase64.isBlank()) {
            prefs.edit().clear().commit()
            if (photoFile.exists()) {
                photoFile.delete()
            }
            return
        }

        val currentPostId = prefs.getString(KEY_POST_ID, null)
        val timeLabel = post.createdAt?.toDate()?.let { date ->
            SimpleDateFormat("HH:mm • dd/MM", Locale("vi", "VN")).format(date)
        } ?: "Vừa xong"

        val caption = post.caption.ifBlank { "Khoảnh khắc Friends ♡" }

        if (currentPostId != post.id || !photoFile.exists() || photoFile.length() == 0L) {
            runCatching {
                val bytes = Base64.decode(post.imageBase64, Base64.DEFAULT)
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching
                val scale = minOf(
                    1f,
                    MAX_WIDGET_DIM_PX.toFloat() / max(raw.width, raw.height).toFloat()
                )
                val scaled = if (scale < 0.99f) {
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * scale).toInt().coerceAtLeast(1),
                        (raw.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    raw
                }
                val tmpFile = File(appContext.filesDir, "$PHOTO_FILE_NAME.tmp")
                FileOutputStream(tmpFile).use { fos ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                }
                if (tmpFile.exists() && tmpFile.length() > 0L) {
                    tmpFile.renameTo(photoFile)
                }
                if (scaled !== raw && !raw.isRecycled) {
                    raw.recycle()
                }
                if (!scaled.isRecycled) {
                    scaled.recycle()
                }
            }
        }

        prefs.edit()
            .putString(KEY_POST_ID, post.id)
            .putString(KEY_AUTHOR, post.authorName.ifBlank { "Bạn bè" })
            .putString(KEY_CAPTION, caption)
            .putString(KEY_TIME_LABEL, timeLabel)
            .commit()
    }

    fun readSnapshot(context: Context): WidgetSnapshot {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val photoFile = File(appContext.filesDir, PHOTO_FILE_NAME)
        val bitmap = if (photoFile.exists() && photoFile.length() > 0L) {
            decodeBinderSafeWidgetBitmap(photoFile.absolutePath)
        } else {
            null
        }
        return WidgetSnapshot(
            postId = prefs.getString(KEY_POST_ID, "").orEmpty(),
            authorName = prefs.getString(KEY_AUTHOR, "").orEmpty(),
            caption = prefs.getString(KEY_CAPTION, "").orEmpty(),
            timeLabel = prefs.getString(KEY_TIME_LABEL, "").orEmpty(),
            bitmap = bitmap
        )
    }

    private fun decodeBinderSafeWidgetBitmap(filePath: String): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            var sampleSize = 1
            val largest = max(bounds.outWidth, bounds.outHeight)
            if (largest > MAX_WIDGET_DIM_PX) {
                while (largest / (sampleSize * 2) >= MAX_WIDGET_DIM_PX) {
                    sampleSize *= 2
                }
            }
            // RGB_565 keeps a 300x400 bitmap at ~240 KB, safely below Android's 1 MB Binder IPC cap
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(filePath, opts) ?: return null
            val maxDim = max(decoded.width, decoded.height)
            if (maxDim > MAX_WIDGET_DIM_PX) {
                val scale = MAX_WIDGET_DIM_PX.toFloat() / maxDim.toFloat()
                val resized = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (resized !== decoded && !decoded.isRecycled) {
                    decoded.recycle()
                }
                resized
            } else {
                decoded
            }
        }.getOrNull()
    }
}
