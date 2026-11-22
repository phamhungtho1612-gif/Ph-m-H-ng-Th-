package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val avatarBase64: String? = null,
    val bio: String? = null,
    val friendCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendCodeEntry(
    val friendCode: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val avatarBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendCircle(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CircleInvite(
    @DocumentId val id: String = "",
    val circleId: String = "",
    val circleName: String = "",
    val fromUserId: String = "",
    val fromName: String = "",
    val toUserId: String = "",
    val toName: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CirclePost(
    @DocumentId val id: String = "",
    val circleId: String = "",
    val circleName: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorAvatarBase64: String? = null,
    val caption: String = "",
    val layout: String = BoothLayout.PHOTO_STRIP.name,
    val frameStyle: String = FrameStyle.WHITE.name,
    val filter: String = PhotoFilter.ORIGINAL.name,
    val dateLabel: String = "",
    val imageBase64: String = "",
    val memberUids: List<String> = emptyList(),
    val likedBy: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val reactorNames: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val circleId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String? = null,
    val authorAvatarBase64: String? = null,
    val text: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectChat(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String? = null,
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DirectMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val participantUids: List<String> = emptyList(),
    val replyPostId: String? = null,
    val replyPostImageBase64: String? = null,
    val replyPostAuthorId: String? = null,
    val replyPostAuthorName: String? = null,
    val replyPostAuthorAvatarBase64: String? = null,
    val replyPostAuthorPhotoUrl: String? = null,
    val replyPostCaption: String? = null,
    val replyPostTimeLabel: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val fromName: String = "",
    val toUserId: String = "",
    val toName: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Friendship(
    @DocumentId val id: String = "",
    val participantUids: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantAvatars: Map<String, String> = emptyMap(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DirectFriendItem(
    val uid: String,
    val displayName: String,
    val circleNames: List<String> = emptyList(),
    val isDirectFriend: Boolean = false,
    val avatarBase64: String? = null,
    val photoUrl: String? = null
)

data class PostReactionItem(
    val userId: String,
    val displayName: String,
    val emoji: String,
    val avatarBase64: String? = null,
    val photoUrl: String? = null
)

enum class ReplyDestinationMode {
    DIRECT_MESSAGE,
    PUBLIC_COMMENT
}

sealed interface CircleFeedUiState<out T> {
    data object Loading : CircleFeedUiState<Nothing>
    data class Success<T>(val data: T) : CircleFeedUiState<T>
    data class Error(val message: String) : CircleFeedUiState<Nothing>
}
