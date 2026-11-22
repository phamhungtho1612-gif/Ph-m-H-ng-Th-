package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object FriendsNotificationHelper {
    const val CHANNEL_REQUESTS = "friends_requests"
    const val CHANNEL_MESSAGES = "friends_messages"
    const val CHANNEL_COMMENTS = "friends_comments"
    const val CHANNEL_REACTIONS = "friends_reactions"

    const val EXTRA_OPEN_TARGET = "extra_notif_target"
    const val EXTRA_TARGET_CHAT_ID = "extra_notif_chat_id"
    const val EXTRA_TARGET_POST_ID = "extra_notif_post_id"

    const val TARGET_FRIENDS_MANAGER = "friends_manager"
    const val TARGET_DIRECT_CHAT = "direct_chat"
    const val TARGET_FEED_POST = "feed_post"

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_REQUESTS,
                "Lời mời kết bạn",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi có người gửi lời mời kết bạn hoặc mời vào vòng"
            },
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Tin nhắn cá nhân",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo khi bạn bè gửi tin nhắn hoặc trả lời ảnh riêng tư"
            },
            NotificationChannel(
                CHANNEL_COMMENTS,
                "Bình luận công khai",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi có bình luận mới trên ảnh của bạn"
            },
            NotificationChannel(
                CHANNEL_REACTIONS,
                "Biểu tượng cảm xúc",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi bạn bè thả biểu tượng cảm xúc vào ảnh của bạn"
            }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun notifyFriendRequest(
        context: Context,
        fromName: String,
        requestId: String
    ) {
        val cleanName = fromName.ifBlank { "Một người bạn" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TARGET, TARGET_FRIENDS_MANAGER)
        }
        postNotification(
            context = context,
            channelId = CHANNEL_REQUESTS,
            notificationId = ("req_$requestId").hashCode(),
            title = "Lời mời kết bạn mới",
            body = "$cleanName muốn kết bạn với bạn trên Friends",
            intent = intent
        )
    }

    fun notifyDirectMessage(
        context: Context,
        chatId: String,
        senderName: String,
        previewText: String
    ) {
        val cleanName = senderName.ifBlank { "Bạn bè" }
        val cleanPreview = previewText.ifBlank { "Đã gửi cho bạn một tin nhắn mới" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TARGET, TARGET_DIRECT_CHAT)
            putExtra(EXTRA_TARGET_CHAT_ID, chatId)
        }
        postNotification(
            context = context,
            channelId = CHANNEL_MESSAGES,
            notificationId = ("dm_$chatId").hashCode(),
            title = cleanName,
            body = cleanPreview,
            intent = intent
        )
    }

    fun notifyPostComment(
        context: Context,
        postId: String,
        authorName: String,
        commentText: String
    ) {
        val cleanName = authorName.ifBlank { "Bạn bè" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TARGET, TARGET_FEED_POST)
            putExtra(EXTRA_TARGET_POST_ID, postId)
        }
        postNotification(
            context = context,
            channelId = CHANNEL_COMMENTS,
            notificationId = ("comment_${postId}_$authorName").hashCode(),
            title = "Bình luận mới từ $cleanName",
            body = commentText.ifBlank { "Đã bình luận trên ảnh của bạn" },
            intent = intent
        )
    }

    fun notifyPostReaction(
        context: Context,
        postId: String,
        reactorUid: String,
        reactorName: String,
        emoji: String
    ) {
        val cleanName = reactorName.ifBlank { "Bạn bè" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TARGET, TARGET_FEED_POST)
            putExtra(EXTRA_TARGET_POST_ID, postId)
        }
        postNotification(
            context = context,
            channelId = CHANNEL_REACTIONS,
            notificationId = ("react_${postId}_$reactorUid").hashCode(),
            title = "Cảm xúc mới trên ảnh của bạn",
            body = "$cleanName đã thả $emoji vào bức ảnh của bạn",
            intent = intent
        )
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        intent: Intent
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_friends)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
