package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FriendsCircleRepository
import com.example.imaging.ImageProcessing
import com.example.imaging.PhotoboothRenderer
import com.example.model.BoothLayout
import com.example.model.CircleFeedUiState
import com.example.model.CircleInvite
import com.example.model.CirclePost
import com.example.model.CreativeOverlayTextState
import com.example.model.DirectChat
import com.example.model.DirectFriendItem
import com.example.model.DirectMessage
import com.example.model.FrameStyle
import com.example.model.FriendCircle
import com.example.model.FriendRequest
import com.example.model.Friendship
import com.example.model.PhotoFilter
import com.example.model.PostComment
import com.example.model.PostReactionItem
import com.example.model.ReplyDestinationMode
import com.example.model.UserProfile
import com.example.notifications.FriendsNotificationHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PublishState {
    data object Idle : PublishState
    data object Publishing : PublishState
    data object Success : PublishState
    data class Error(val message: String) : PublishState
}

data class CircleActionState(
    val myFriendCode: String = "",
    val selectedCircleIdFilter: String? = null,
    val activeCommentPostId: String? = null,
    val activeDirectChat: DirectChat? = null,
    val isBusy: Boolean = false,
    val isUpdatingProfile: Boolean = false,
    val isSendingComment: Boolean = false,
    val isSendingDirectMessage: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val publishState: PublishState = PublishState.Idle
)

class FriendsCircleViewModel(
    private val repository: FriendsCircleRepository,
    private val currentUserId: String,
    private val appContext: Context? = null,
    private val notificationsEnabledFlow: Flow<Boolean>? = null
) : ViewModel() {

    private val _actionState = MutableStateFlow(CircleActionState())
    val actionState: StateFlow<CircleActionState> = _actionState.asStateFlow()

    private val _activeCommentPostId = MutableStateFlow<String?>(null)
    private val _activeDirectChatId = MutableStateFlow<String?>(null)
    private val notificationsEnabledState = MutableStateFlow(true)

    val userProfileState: StateFlow<UserProfile?> =
        repository.observeUserProfile(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing user profile", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    val circlesState: StateFlow<CircleFeedUiState<List<FriendCircle>>> =
        repository.observeCircles(currentUserId)
            .map<List<FriendCircle>, CircleFeedUiState<List<FriendCircle>>> {
                CircleFeedUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing circles", rootError)
                emit(
                    CircleFeedUiState.Error(
                        rootError.localizedMessage ?: "Không thể tải danh sách vòng bạn bè"
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CircleFeedUiState.Loading
            )

    val invitesState: StateFlow<List<CircleInvite>> =
        repository.observeIncomingInvites(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing invites", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val friendshipsState: StateFlow<List<Friendship>> =
        repository.observeFriendships(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing friendships", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val friendRequestsState: StateFlow<List<FriendRequest>> =
        repository.observeIncomingFriendRequests(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing friend requests", firestoreError ?: error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    val postsState: StateFlow<CircleFeedUiState<List<CirclePost>>> =
        repository.observeCirclePosts(currentUserId)
            .map<List<CirclePost>, CircleFeedUiState<List<CirclePost>>> {
                CircleFeedUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing circle posts", rootError)
                emit(
                    CircleFeedUiState.Error(
                        rootError.localizedMessage ?: "Không thể tải bài đăng Vòng bạn bè"
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CircleFeedUiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val commentsState: StateFlow<CircleFeedUiState<List<PostComment>>> =
        _activeCommentPostId
            .flatMapLatest { postId ->
                if (postId.isNullOrBlank()) {
                    flowOf(CircleFeedUiState.Success(emptyList()))
                } else {
                    repository.observePostComments(postId, currentUserId)
                        .map<List<PostComment>, CircleFeedUiState<List<PostComment>>> {
                            CircleFeedUiState.Success(it)
                        }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing comments", rootError)
                            emit(
                                CircleFeedUiState.Error(
                                    rootError.localizedMessage ?: "Không thể tải tin nhắn"
                                )
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CircleFeedUiState.Loading
            )

    val directChatsState: StateFlow<CircleFeedUiState<List<DirectChat>>> =
        repository.observeDirectChats(currentUserId)
            .map<List<DirectChat>, CircleFeedUiState<List<DirectChat>>> {
                CircleFeedUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing direct chats", rootError)
                emit(
                    CircleFeedUiState.Error(
                        rootError.localizedMessage ?: "Không thể tải tin nhắn cá nhân"
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CircleFeedUiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val directMessagesState: StateFlow<CircleFeedUiState<List<DirectMessage>>> =
        _activeDirectChatId
            .flatMapLatest { chatId ->
                if (chatId.isNullOrBlank()) {
                    flowOf(CircleFeedUiState.Success(emptyList()))
                } else {
                    repository.observeDirectMessages(chatId, currentUserId)
                        .map<List<DirectMessage>, CircleFeedUiState<List<DirectMessage>>> {
                            CircleFeedUiState.Success(it)
                        }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            Log.w(TAG, "Error observing direct messages", rootError)
                            emit(
                                CircleFeedUiState.Error(
                                    rootError.localizedMessage ?: "Không thể tải nội dung tin nhắn"
                                )
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = CircleFeedUiState.Loading
            )

    // Maps UID -> Pair(avatarBase64, photoUrl) gathered from profile, friendships, chats & posts
    val friendAvatarsByUid: StateFlow<Map<String, Pair<String?, String?>>> =
        combine(userProfileState, friendshipsState, directChatsState, postsState) { profile, friendships, chatsRes, postsRes ->
            val map = linkedMapOf<String, Pair<String?, String?>>()
            fun putIfBetter(uid: String, base64: String?, url: String?) {
                if (uid.isBlank()) return
                val existing = map[uid]
                val nextBase64 = base64?.takeIf { it.isNotBlank() } ?: existing?.first
                val nextUrl = url?.takeIf { it.isNotBlank() } ?: existing?.second
                if (nextBase64 != null || nextUrl != null) {
                    map[uid] = nextBase64 to nextUrl
                }
            }

            if (profile != null) {
                putIfBetter(currentUserId, profile.avatarBase64, profile.photoUrl)
            }
            friendships.forEach { f ->
                f.participantAvatars.forEach { (uid, avatar) ->
                    putIfBetter(uid, avatar, null)
                }
            }
            (chatsRes as? CircleFeedUiState.Success)?.data.orEmpty().forEach { chat ->
                chat.participantAvatars.forEach { (uid, avatar) ->
                    putIfBetter(uid, avatar, null)
                }
            }
            (postsRes as? CircleFeedUiState.Success)?.data.orEmpty().forEach { post ->
                putIfBetter(post.authorId, post.authorAvatarBase64, post.authorPhotoUrl)
            }
            map
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyMap()
        )

    val directFriendsState: StateFlow<List<DirectFriendItem>> =
        combine(circlesState, friendshipsState, friendAvatarsByUid) { state, friendships, avatars ->
            val circles = (state as? CircleFeedUiState.Success)?.data.orEmpty()
            val friendsMap = linkedMapOf<String, Triple<String, MutableList<String>, Boolean>>()

            for (friendship in friendships) {
                for (uid in friendship.participantUids) {
                    if (uid == currentUserId || uid.isBlank()) continue
                    val name = friendship.participantNames[uid]?.takeIf { it.isNotBlank() } ?: "Bạn bè"
                    friendsMap[uid] = Triple(name, mutableListOf(), true)
                }
            }

            for (circle in circles) {
                for (uid in circle.memberUids) {
                    if (uid == currentUserId || uid.isBlank()) continue
                    val name = circle.memberNames[uid]?.takeIf { it.isNotBlank() } ?: "Bạn bè"
                    val existing = friendsMap[uid]
                    if (existing != null) {
                        if (circle.name.isNotBlank() && circle.name !in existing.second) {
                            existing.second.add(circle.name)
                        }
                    } else {
                        val list = mutableListOf<String>()
                        if (circle.name.isNotBlank()) list.add(circle.name)
                        friendsMap[uid] = Triple(name, list, false)
                    }
                }
            }

            friendsMap.entries.map { (uid, triple) ->
                val avatarPair = avatars[uid]
                DirectFriendItem(
                    uid = uid,
                    displayName = triple.first,
                    avatarBase64 = avatarPair?.first,
                    photoUrl = avatarPair?.second,
                    circleNames = triple.second,
                    isDirectFriend = triple.third
                )
            }.sortedBy { it.displayName.lowercase() }
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = emptyList()
            )

    init {
        viewModelScope.launch {
            val profileResult = repository.ensureUserProfile()
            profileResult.onSuccess { friendCode ->
                _actionState.update { it.copy(myFriendCode = friendCode) }
            }
        }
        viewModelScope.launch {
            combine(circlesState, friendshipsState, postsState) { circlesRes, friendships, postsRes ->
                val circles = (circlesRes as? CircleFeedUiState.Success)?.data.orEmpty()
                val posts = (postsRes as? CircleFeedUiState.Success)?.data.orEmpty()
                Triple(circles, friendships, posts)
            }.collect { (circles, friendships, posts) ->
                if (posts.isNotEmpty() && (circles.isNotEmpty() || friendships.isNotEmpty())) {
                    repository.syncOwnPostMemberships(circles, friendships, posts)
                }
            }
        }
        notificationsEnabledFlow?.let { flow ->
            viewModelScope.launch {
                flow.collect { enabled ->
                    notificationsEnabledState.value = enabled
                }
            }
        }
        appContext?.let { ctx ->
            startNotificationObservers(ctx.applicationContext)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startNotificationObservers(context: Context) {
        // 1. Incoming friend requests
        viewModelScope.launch {
            var hydrated = false
            val seenIds = mutableSetOf<String>()
            friendRequestsState.collect { requests ->
                val pending = requests.filter { it.status == "PENDING" && it.id.isNotBlank() }
                if (!hydrated) {
                    seenIds.addAll(pending.map { it.id })
                    hydrated = true
                } else {
                    for (req in pending) {
                        if (req.id !in seenIds) {
                            seenIds.add(req.id)
                            if (notificationsEnabledState.value) {
                                FriendsNotificationHelper.notifyFriendRequest(
                                    context = context,
                                    fromName = req.fromName,
                                    requestId = req.id
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Incoming direct messages
        viewModelScope.launch {
            var hydrated = false
            val lastSnapshotByChat = mutableMapOf<String, String>()
            directChatsState.collect { state ->
                val chats = (state as? CircleFeedUiState.Success)?.data ?: return@collect
                if (!hydrated) {
                    chats.forEach { chat ->
                        lastSnapshotByChat[chat.id] =
                            "${chat.lastMessageAt?.seconds ?: 0L}_${chat.lastMessageText}"
                    }
                    hydrated = true
                } else {
                    for (chat in chats) {
                        val stamp = "${chat.lastMessageAt?.seconds ?: 0L}_${chat.lastMessageText}"
                        val prev = lastSnapshotByChat[chat.id]
                        lastSnapshotByChat[chat.id] = stamp
                        val senderId = chat.lastMessageSenderId
                        if (prev != stamp &&
                            !senderId.isNullOrBlank() &&
                            senderId != currentUserId &&
                            chat.lastMessageText.isNotBlank() &&
                            _activeDirectChatId.value != chat.id &&
                            notificationsEnabledState.value
                        ) {
                            val senderName = chat.participantNames[senderId] ?: "Bạn bè"
                            FriendsNotificationHelper.notifyDirectMessage(
                                context = context,
                                chatId = chat.id,
                                senderName = senderName,
                                previewText = chat.lastMessageText
                            )
                        }
                    }
                }
            }
        }

        // 3. Reactions & likes on user's own posts
        viewModelScope.launch {
            var hydrated = false
            val prevReactions = mutableMapOf<String, Map<String, String>>()
            val prevLikes = mutableMapOf<String, Set<String>>()
            postsState.collect { state ->
                val posts = (state as? CircleFeedUiState.Success)?.data ?: return@collect
                val ownPosts = posts.filter { it.authorId == currentUserId && it.id.isNotBlank() }
                if (!hydrated) {
                    ownPosts.forEach { post ->
                        prevReactions[post.id] = post.reactions
                        prevLikes[post.id] = post.likedBy.toSet()
                    }
                    hydrated = true
                } else {
                    val friendsLookup = directFriendsState.value.associateBy { it.uid }
                    for (post in ownPosts) {
                        val oldReacts = prevReactions[post.id]
                        val oldLikes = prevLikes[post.id]
                        prevReactions[post.id] = post.reactions
                        prevLikes[post.id] = post.likedBy.toSet()

                        if (oldReacts != null) {
                            for ((reactorUid, emoji) in post.reactions) {
                                if (reactorUid == currentUserId) continue
                                if (oldReacts[reactorUid] != emoji && notificationsEnabledState.value) {
                                    val name = post.reactorNames[reactorUid]
                                        ?: friendsLookup[reactorUid]?.displayName
                                        ?: "Bạn bè"
                                    FriendsNotificationHelper.notifyPostReaction(
                                        context = context,
                                        postId = post.id,
                                        reactorUid = reactorUid,
                                        reactorName = name,
                                        emoji = emoji
                                    )
                                }
                            }
                        }

                        if (oldLikes != null) {
                            for (likerUid in post.likedBy) {
                                if (likerUid == currentUserId) continue
                                if (likerUid !in oldLikes &&
                                    likerUid !in post.reactions &&
                                    notificationsEnabledState.value
                                ) {
                                    val name = friendsLookup[likerUid]?.displayName ?: "Bạn bè"
                                    FriendsNotificationHelper.notifyPostReaction(
                                        context = context,
                                        postId = post.id,
                                        reactorUid = likerUid,
                                        reactorName = name,
                                        emoji = "❤️"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Public comments on latest 5 own posts
        viewModelScope.launch {
            val seenCommentsByPost = mutableMapOf<String, MutableSet<String>>()
            postsState
                .map { state ->
                    (state as? CircleFeedUiState.Success)?.data
                        ?.filter { it.authorId == currentUserId && it.id.isNotBlank() }
                        ?.take(5)
                        ?.map { it.id }
                        .orEmpty()
                }
                .flatMapLatest { ownPostIds ->
                    if (ownPostIds.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        val flows = ownPostIds.map { postId ->
                            repository.observePostComments(postId, currentUserId)
                                .map { comments -> postId to comments }
                                .catch { emit(postId to emptyList()) }
                        }
                        combine(flows) { pairs -> pairs.toList() }
                    }
                }
                .collect { pairs ->
                    for ((postId, comments) in pairs) {
                        val existingSeen = seenCommentsByPost[postId]
                        if (existingSeen == null) {
                            seenCommentsByPost[postId] = comments.map { it.id }.toMutableSet()
                        } else {
                            for (comment in comments) {
                                if (comment.id.isNotBlank() && comment.id !in existingSeen) {
                                    existingSeen.add(comment.id)
                                    if (comment.authorId != currentUserId && notificationsEnabledState.value) {
                                        FriendsNotificationHelper.notifyPostComment(
                                            context = context,
                                            postId = postId,
                                            authorName = comment.authorName,
                                            commentText = comment.text
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    fun selectCircleFilter(circleId: String?) {
        _actionState.update { it.copy(selectedCircleIdFilter = circleId) }
    }

    fun updateUserBio(bio: String) {
        if (_actionState.value.isUpdatingProfile) return
        _actionState.update { it.copy(isUpdatingProfile = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.updateUserBio(bio)
            result.fold(
                onSuccess = { savedBio ->
                    _actionState.update {
                        it.copy(
                            isUpdatingProfile = false,
                            actionMessage = if (savedBio.isBlank()) {
                                "Đã xóa ghi chú tiểu sử"
                            } else {
                                "Đã lưu tiểu sử cá nhân!"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isUpdatingProfile = false,
                            actionError = err.localizedMessage ?: "Không thể lưu tiểu sử"
                        )
                    }
                }
            )
        }
    }

    fun updateAvatarFromUri(context: Context, uri: Uri) {
        if (_actionState.value.isUpdatingProfile) return
        val appContext = context.applicationContext
        _actionState.update { it.copy(isUpdatingProfile = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            var decoded: Bitmap? = null
            try {
                decoded = ImageProcessing.decodeUriToBitmap(appContext, uri)
                if (decoded == null) {
                    _actionState.update {
                        it.copy(
                            isUpdatingProfile = false,
                            actionError = "Không thể đọc ảnh đại diện đã chọn."
                        )
                    }
                    return@launch
                }
                val result = repository.updateUserAvatar(decoded)
                result.fold(
                    onSuccess = {
                        _actionState.update {
                            it.copy(
                                isUpdatingProfile = false,
                                actionMessage = "Đã cập nhật ảnh đại diện!"
                            )
                        }
                    },
                    onFailure = { err ->
                        _actionState.update {
                            it.copy(
                                isUpdatingProfile = false,
                                actionError = err.localizedMessage ?: "Không thể cập nhật ảnh đại diện"
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionState.update {
                    it.copy(
                        isUpdatingProfile = false,
                        actionError = e.localizedMessage ?: "Không thể cập nhật ảnh đại diện"
                    )
                }
            } finally {
                decoded?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }
        }
    }

    fun openPostComments(postId: String?) {
        _activeCommentPostId.value = postId
        _actionState.update { it.copy(activeCommentPostId = postId) }
    }

    fun openExistingDirectChat(chat: DirectChat) {
        _activeDirectChatId.value = chat.id
        _actionState.update { it.copy(activeDirectChat = chat, actionError = null) }
    }

    fun openDirectChatById(chatId: String) {
        val chat = (directChatsState.value as? CircleFeedUiState.Success)?.data
            ?.firstOrNull { it.id == chatId }
        if (chat != null) {
            openExistingDirectChat(chat)
        } else {
            _activeDirectChatId.value = chatId
        }
    }

    fun openDirectChatWithFriend(partnerUid: String, partnerName: String, onOpened: () -> Unit = {}) {
        if (_actionState.value.isBusy) return
        val existingChat = (directChatsState.value as? CircleFeedUiState.Success)?.data
            ?.firstOrNull { partnerUid in it.participantUids }
        if (existingChat != null) {
            openExistingDirectChat(existingChat)
            onOpened()
            return
        }

        val partnerAvatar = friendAvatarsByUid.value[partnerUid]?.first
        _actionState.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            val result = repository.openOrCreateDirectChat(partnerUid, partnerName, partnerAvatar)
            result.fold(
                onSuccess = { chat ->
                    _activeDirectChatId.value = chat.id
                    _actionState.update { it.copy(isBusy = false, activeDirectChat = chat) }
                    onOpened()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể mở cuộc trò chuyện"
                        )
                    }
                }
            )
        }
    }

    fun startDirectChatByFriendCode(friendCode: String, onOpened: () -> Unit = {}) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            val result = repository.startDirectChatByFriendCode(friendCode)
            result.fold(
                onSuccess = { chat ->
                    _activeDirectChatId.value = chat.id
                    _actionState.update { it.copy(isBusy = false, activeDirectChat = chat) }
                    onOpened()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể tìm bạn bè với mã này"
                        )
                    }
                }
            )
        }
    }

    fun closeDirectChat() {
        _activeDirectChatId.value = null
        _actionState.update { it.copy(activeDirectChat = null) }
    }

    fun sendDirectMessage(text: String, onSuccess: () -> Unit = {}) {
        val chat = _actionState.value.activeDirectChat ?: return
        if (_actionState.value.isSendingDirectMessage) return
        _actionState.update { it.copy(isSendingDirectMessage = true, actionError = null) }
        viewModelScope.launch {
            val result = repository.sendDirectMessage(chat, text)
            result.fold(
                onSuccess = {
                    _actionState.update { it.copy(isSendingDirectMessage = false) }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isSendingDirectMessage = false,
                            actionError = err.localizedMessage ?: "Không thể gửi tin nhắn"
                        )
                    }
                }
            )
        }
    }

    fun deleteDirectMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            val result = repository.deleteDirectMessage(chatId, messageId)
            result.onFailure { err ->
                _actionState.update {
                    it.copy(actionError = err.localizedMessage ?: "Không thể xóa tin nhắn")
                }
            }
        }
    }

    fun createCircle(name: String, onCreated: ((FriendCircle) -> Unit)? = null) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.createCircle(name)
            result.fold(
                onSuccess = { circle ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            selectedCircleIdFilter = circle.id,
                            actionMessage = "Đã tạo vòng \"${circle.name}\" • Mã mời: ${circle.inviteCode}"
                        )
                    }
                    onCreated?.invoke(circle)
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể tạo vòng bạn bè"
                        )
                    }
                }
            )
        }
    }

    fun joinCircle(inviteCode: String, onJoined: (() -> Unit)? = null) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.joinCircle(inviteCode)
            result.fold(
                onSuccess = {
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = "Đã tham gia vòng bạn bè!"
                        )
                    }
                    onJoined?.invoke()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể tham gia vòng bạn bè"
                        )
                    }
                }
            )
        }
    }

    fun inviteFriendByCode(circle: FriendCircle, friendCode: String) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.sendCircleInviteByFriendCode(circle, friendCode)
            result.fold(
                onSuccess = { friendName ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = "Đã gửi lời mời vào vòng \"${circle.name}\" tới $friendName!"
                        )
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể gửi lời mời"
                        )
                    }
                }
            )
        }
    }

    fun respondToInvite(invite: CircleInvite, accept: Boolean) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.respondToInvite(invite, accept)
            result.fold(
                onSuccess = {
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = if (accept) {
                                "Đã tham gia vòng \"${invite.circleName}\"!"
                            } else {
                                "Đã từ chối lời mời"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể xử lý lời mời"
                        )
                    }
                }
            )
        }
    }

    fun leaveCircle(circleId: String) {
        viewModelScope.launch {
            val result = repository.leaveCircle(circleId)
            result.fold(
                onSuccess = {
                    _actionState.update { state ->
                        state.copy(
                            selectedCircleIdFilter = if (state.selectedCircleIdFilter == circleId) {
                                null
                            } else {
                                state.selectedCircleIdFilter
                            },
                            actionMessage = "Đã rời khỏi vòng bạn bè"
                        )
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(actionError = err.localizedMessage ?: "Không thể rời vòng")
                    }
                }
            )
        }
    }

    fun sendFriendRequest(friendCode: String, onSent: () -> Unit = {}) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.sendFriendRequestByCode(friendCode)
            result.fold(
                onSuccess = { friendName ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = "Đã gửi lời mời kết bạn tới $friendName!"
                        )
                    }
                    onSent()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể gửi lời mời kết bạn"
                        )
                    }
                }
            )
        }
    }

    fun respondToFriendRequest(request: FriendRequest, accept: Boolean) {
        if (_actionState.value.isBusy) return
        _actionState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            val result = repository.respondToFriendRequest(request, accept)
            result.fold(
                onSuccess = {
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = if (accept) {
                                "Bạn và ${request.fromName} đã trở thành bạn bè!"
                            } else {
                                "Đã từ chối lời mời kết bạn"
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            actionError = err.localizedMessage ?: "Không thể xử lý lời mời kết bạn"
                        )
                    }
                }
            )
        }
    }

    fun removeFriend(partnerUid: String, partnerName: String) {
        viewModelScope.launch {
            val result = repository.removeFriend(partnerUid)
            result.fold(
                onSuccess = {
                    _actionState.update {
                        it.copy(actionMessage = "Đã hủy kết bạn với $partnerName")
                    }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(actionError = err.localizedMessage ?: "Không thể hủy kết bạn")
                    }
                }
            )
        }
    }

    fun publishToSharedFriends(
        context: Context,
        photos: List<Bitmap>,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        filter: PhotoFilter,
        captionText: String,
        showDateStamp: Boolean,
        formattedDate: String,
        creativeText: CreativeOverlayTextState = CreativeOverlayTextState(),
        subjectMasks: List<Bitmap?> = emptyList()
    ) {
        if (_actionState.value.publishState is PublishState.Publishing) return
        _actionState.update { it.copy(publishState = PublishState.Publishing) }

        viewModelScope.launch {
            var rendered: Bitmap? = null
            try {
                rendered = PhotoboothRenderer.renderCollage(
                    context = context.applicationContext,
                    photos = photos,
                    layout = layout,
                    frameStyle = frameStyle,
                    filter = filter,
                    captionText = captionText,
                    showDateStamp = showDateStamp,
                    formattedDate = formattedDate,
                    creativeText = creativeText,
                    subjectMasks = subjectMasks
                )

                val friendUids = directFriendsState.value.map { it.uid }
                val result = repository.publishSharedFriendsPost(
                    friendUids = friendUids,
                    collageBitmap = rendered,
                    caption = captionText,
                    layout = layout,
                    frameStyle = frameStyle,
                    filter = filter,
                    dateLabel = if (showDateStamp) formattedDate else ""
                )

                _actionState.update { state ->
                    result.fold(
                        onSuccess = { state.copy(publishState = PublishState.Success) },
                        onFailure = { err ->
                            state.copy(
                                publishState = PublishState.Error(
                                    err.localizedMessage ?: "Không thể đăng ảnh"
                                )
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionState.update {
                    it.copy(
                        publishState = PublishState.Error(
                            e.localizedMessage ?: "Không thể kết xuất ảnh"
                        )
                    )
                }
            } finally {
                rendered?.recycle()
            }
        }
    }

    fun publishCollageToCircle(
        context: Context,
        circle: FriendCircle,
        photos: List<Bitmap>,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        filter: PhotoFilter,
        captionText: String,
        showDateStamp: Boolean,
        formattedDate: String,
        creativeText: CreativeOverlayTextState = CreativeOverlayTextState(),
        subjectMasks: List<Bitmap?> = emptyList()
    ) {
        if (_actionState.value.publishState is PublishState.Publishing) return
        _actionState.update { it.copy(publishState = PublishState.Publishing) }

        viewModelScope.launch {
            var rendered: Bitmap? = null
            try {
                rendered = PhotoboothRenderer.renderCollage(
                    context = context.applicationContext,
                    photos = photos,
                    layout = layout,
                    frameStyle = frameStyle,
                    filter = filter,
                    captionText = captionText,
                    showDateStamp = showDateStamp,
                    formattedDate = formattedDate,
                    creativeText = creativeText,
                    subjectMasks = subjectMasks
                )

                val result = repository.publishPost(
                    circle = circle,
                    collageBitmap = rendered,
                    caption = captionText,
                    layout = layout,
                    frameStyle = frameStyle,
                    filter = filter,
                    dateLabel = if (showDateStamp) formattedDate else ""
                )

                _actionState.update { state ->
                    result.fold(
                        onSuccess = { state.copy(publishState = PublishState.Success) },
                        onFailure = { err ->
                            state.copy(
                                publishState = PublishState.Error(
                                    err.localizedMessage ?: "Không thể đăng ảnh"
                                )
                            )
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionState.update {
                    it.copy(
                        publishState = PublishState.Error(
                            e.localizedMessage ?: "Không thể kết xuất ảnh"
                        )
                    )
                }
            } finally {
                rendered?.recycle()
            }
        }
    }

    fun toggleLike(post: CirclePost) {
        viewModelScope.launch {
            val result = repository.toggleLike(post)
            result.onFailure { err ->
                _actionState.update {
                    it.copy(actionError = err.localizedMessage ?: "Không thể thả tim")
                }
            }
        }
    }

    fun reactToPost(post: CirclePost, emoji: String?) {
        viewModelScope.launch {
            val result = repository.reactToPost(post, emoji)
            result.onFailure { err ->
                _actionState.update {
                    it.copy(actionError = err.localizedMessage ?: "Không thể thả biểu tượng cảm xúc")
                }
            }
        }
    }

    fun buildReactionItems(post: CirclePost): List<PostReactionItem> {
        val avatars = friendAvatarsByUid.value
        val friends = directFriendsState.value.associateBy { it.uid }
        val result = mutableListOf<PostReactionItem>()
        val seenUids = mutableSetOf<String>()

        for ((uid, emoji) in post.reactions) {
            if (uid.isBlank() || emoji.isBlank()) continue
            seenUids.add(uid)
            val name = when {
                uid == currentUserId -> "Bạn"
                !post.reactorNames[uid].isNullOrBlank() -> post.reactorNames[uid].orEmpty()
                else -> friends[uid]?.displayName ?: "Bạn bè"
            }
            val avatarPair = avatars[uid]
            result.add(
                PostReactionItem(
                    userId = uid,
                    displayName = name,
                    emoji = emoji,
                    avatarBase64 = avatarPair?.first,
                    photoUrl = avatarPair?.second
                )
            )
        }

        for (uid in post.likedBy) {
            if (uid.isBlank() || uid in seenUids) continue
            seenUids.add(uid)
            val name = when (uid) {
                currentUserId -> "Bạn"
                else -> friends[uid]?.displayName ?: "Bạn bè"
            }
            val avatarPair = avatars[uid]
            result.add(
                PostReactionItem(
                    userId = uid,
                    displayName = name,
                    emoji = "❤️",
                    avatarBase64 = avatarPair?.first,
                    photoUrl = avatarPair?.second
                )
            )
        }
        return result
    }

    fun sendComment(post: CirclePost, text: String, onSuccess: () -> Unit = {}) {
        if (_actionState.value.isSendingComment) return
        _actionState.update { it.copy(isSendingComment = true, actionError = null) }
        viewModelScope.launch {
            val result = repository.sendPostComment(post, text)
            result.fold(
                onSuccess = {
                    _actionState.update { it.copy(isSendingComment = false) }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isSendingComment = false,
                            actionError = err.localizedMessage ?: "Không thể gửi bình luận"
                        )
                    }
                }
            )
        }
    }

    fun sendReplyToPost(
        post: CirclePost,
        mode: ReplyDestinationMode,
        text: String,
        openChatAfterSend: Boolean = false,
        onPublicSuccess: () -> Unit = {},
        onDirectSuccess: (DirectChat) -> Unit = {}
    ) {
        if (mode == ReplyDestinationMode.PUBLIC_COMMENT || post.authorId == currentUserId) {
            sendComment(post, text, onPublicSuccess)
            return
        }
        if (_actionState.value.isSendingComment) return
        _actionState.update { it.copy(isSendingComment = true, actionError = null) }
        val partnerAvatar = post.authorAvatarBase64
            ?: friendAvatarsByUid.value[post.authorId]?.first
        viewModelScope.launch {
            val result = repository.sendDirectPhotoReply(
                partnerUid = post.authorId,
                partnerName = post.authorName,
                partnerAvatarBase64 = partnerAvatar,
                post = post,
                rawText = text
            )
            result.fold(
                onSuccess = { chat ->
                    _actionState.update {
                        it.copy(
                            isSendingComment = false,
                            activeDirectChat = if (openChatAfterSend) chat else it.activeDirectChat,
                            actionMessage = if (openChatAfterSend) {
                                null
                            } else {
                                "Đã gửi ảnh & tin nhắn riêng tới ${post.authorName}!"
                            }
                        )
                    }
                    if (openChatAfterSend) {
                        _activeDirectChatId.value = chat.id
                    }
                    onDirectSuccess(chat)
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(
                            isSendingComment = false,
                            actionError = err.localizedMessage ?: "Không thể gửi tin nhắn riêng"
                        )
                    }
                }
            )
        }
    }

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            val result = repository.deletePostComment(postId, commentId)
            result.onFailure { err ->
                _actionState.update {
                    it.copy(actionError = err.localizedMessage ?: "Không thể xóa bình luận")
                }
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            val result = repository.deletePost(postId)
            result.fold(
                onSuccess = {
                    _actionState.update { it.copy(actionMessage = "Đã xóa bài đăng") }
                },
                onFailure = { err ->
                    _actionState.update {
                        it.copy(actionError = err.localizedMessage ?: "Không thể xóa bài")
                    }
                }
            )
        }
    }

    fun resetPublishState() {
        _actionState.update { it.copy(publishState = PublishState.Idle) }
    }

    fun clearActionMessages() {
        _actionState.update { it.copy(actionError = null, actionMessage = null) }
    }

    private companion object {
        const val TAG = "FriendsCircleVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
