package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.R
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.model.BoothLayout
import com.example.model.CircleInvite
import com.example.model.CirclePost
import com.example.model.DirectChat
import com.example.model.DirectMessage
import com.example.model.FrameStyle
import com.example.model.FriendCircle
import com.example.model.FriendCodeEntry
import com.example.model.FriendRequest
import com.example.model.Friendship
import com.example.model.PhotoFilter
import com.example.model.PostComment
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.random.Random

class FriendsCircleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Bạn cần đăng nhập Google để sử dụng Vòng bạn bè.")
    }

    private fun currentDisplayName(fallback: String = "Thành viên"): String {
        return (auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: fallback).take(60)
    }

    suspend fun ensureUserProfile(): Result<String> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Chưa đăng nhập Google"))
        val path = "users/${user.uid}"
        return try {
            val docRef = db.collection("users").document(user.uid)
            val snapshot = docRef.get().await()
            val existingProfile = snapshot.toObject(UserProfile::class.java)
            val cleanName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Bạn mới").take(60)
            val cleanPhotoUrl = user.photoUrl?.toString()?.take(600)

            var friendCode = existingProfile?.friendCode
                ?.trim()
                ?.uppercase()
                ?.takeIf { FRIEND_CODE_REGEX.matches(it) }

            if (friendCode == null) {
                friendCode = allocateUniqueFriendCode(user.uid)
            }

            if (snapshot.exists()) {
                val updateMap = mutableMapOf<String, Any?>(
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhotoUrl,
                    "friendCode" to friendCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                @Suppress("UNCHECKED_CAST")
                docRef.update(updateMap as Map<String, Any>).await()
            } else {
                val createMap = mutableMapOf<String, Any?>(
                    "userId" to user.uid,
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhotoUrl,
                    "friendCode" to friendCode,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            }

            // Sync public zero-PII friend_codes lookup doc
            val compactAvatar = existingProfile?.avatarBase64
                ?.takeIf { it.isNotBlank() }
                ?.let { compressBase64ForMiniAvatar(it) }
            val codeRef = db.collection("friend_codes").document(friendCode)
            val codeSnap = codeRef.get().await()
            if (codeSnap.exists()) {
                val ownerUid = codeSnap.getString("userId")
                if (ownerUid == user.uid) {
                    val codeUpdate = mutableMapOf<String, Any?>(
                        "displayName" to cleanName,
                        "photoUrl" to cleanPhotoUrl,
                        "avatarBase64" to compactAvatar,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    @Suppress("UNCHECKED_CAST")
                    codeRef.update(codeUpdate as Map<String, Any>).await()
                }
            } else {
                val codeCreate = mutableMapOf<String, Any?>(
                    "friendCode" to friendCode,
                    "userId" to user.uid,
                    "displayName" to cleanName,
                    "photoUrl" to cleanPhotoUrl,
                    "avatarBase64" to compactAvatar,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                codeRef.set(codeCreate).await()
            }

            Result.success(friendCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun allocateUniqueFriendCode(uid: String): String {
        repeat(5) {
            val candidate = generateCode(6)
            val existing = db.collection("friend_codes").document(candidate).get().await()
            if (!existing.exists() || existing.getString("userId") == uid) {
                return candidate
            }
        }
        return generateCode(8)
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users")
                .document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateUserBio(rawBio: String): Result<String> {
        val cleanBio = rawBio.trim().take(MAX_BIO_LENGTH)
        var docPath = "users"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                ensureUserProfile().getOrThrow()
            }
            docRef.update(
                mapOf(
                    "bio" to cleanBio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(cleanBio)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserAvatar(bitmap: Bitmap): Result<String> {
        var docPath = "users"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val encodedAvatar = encodeAvatarForFirestore(bitmap)
            val miniAvatar = encodeMiniAvatarForSharing(bitmap)
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                ensureUserProfile().getOrThrow()
            }
            docRef.update(
                mapOf(
                    "avatarBase64" to encodedAvatar,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Best-effort propagate compact avatar so friends see it in feed, friends list & DMs
            propagateOwnMiniAvatar(
                uid = uid,
                friendCode = snapshot.getString("friendCode"),
                miniAvatar = miniAvatar
            )

            Result.success(encodedAvatar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun propagateOwnMiniAvatar(
        uid: String,
        friendCode: String?,
        miniAvatar: String
    ) {
        if (miniAvatar.isBlank() || miniAvatar.length > MAX_MINI_AVATAR_LENGTH) return
        if (!friendCode.isNullOrBlank()) {
            runCatching {
                db.collection("friend_codes").document(friendCode).update(
                    mapOf(
                        "avatarBase64" to miniAvatar,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        runCatching {
            val friendshipsSnap = db.collection("friendships")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
            for (doc in friendshipsSnap.documents) {
                runCatching {
                    doc.reference.update(
                        mapOf(
                            "participantAvatars.$uid" to miniAvatar,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
        runCatching {
            val chatsSnap = db.collection("direct_chats")
                .whereArrayContains("participantUids", uid)
                .get()
                .await()
            for (doc in chatsSnap.documents) {
                runCatching {
                    doc.reference.update(
                        mapOf(
                            "participantAvatars.$uid" to miniAvatar,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
        runCatching {
            val postsSnap = db.collection("circle_posts")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            for (doc in postsSnap.documents.filter { it.getString("authorId") == uid }.take(20)) {
                runCatching {
                    doc.reference.update(
                        mapOf(
                            "authorAvatarBase64" to miniAvatar,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
    }

    private suspend fun readOwnCompactAvatar(uid: String): String? {
        val raw = runCatching {
            db.collection("users").document(uid).get().await().getString("avatarBase64")
        }.getOrNull() ?: return null
        if (raw.isBlank()) return null
        return compressBase64ForMiniAvatar(raw)
    }

    fun observeCircles(userId: String): Flow<List<FriendCircle>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circles"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FriendCircle::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeIncomingInvites(userId: String): Flow<List<CircleInvite>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circle_invites"
        emitAll(
            db.collection(path)
                .whereEqualTo("toUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CircleInvite::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { it.status == "PENDING" }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFriendships(userId: String): Flow<List<Friendship>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "friendships"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Friendship::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeIncomingFriendRequests(userId: String): Flow<List<FriendRequest>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "friend_requests"
        emitAll(
            db.collection(path)
                .whereEqualTo("toUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FriendRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                        .filter { it.status == "PENDING" }
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCirclePosts(userId: String): Flow<List<CirclePost>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circle_posts"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CirclePost::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePostComments(postId: String, userId: String): Flow<List<PostComment>> = flow {
        require(postId.isNotBlank()) { "Post ID must not be blank" }
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "circle_posts/$postId/comments"
        emitAll(
            db.collection("circle_posts")
                .document(postId)
                .collection("comments")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PostComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun directChatId(uidA: String, uidB: String): String {
        return listOf(uidA, uidB).sorted().joinToString("_")
    }

    fun observeDirectChats(userId: String): Flow<List<DirectChat>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "direct_chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending {
                        it.lastMessageAt?.seconds ?: it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDirectMessages(chatId: String, userId: String): Flow<List<DirectMessage>> = flow {
        require(chatId.isNotBlank()) { "Chat ID must not be blank" }
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "direct_chats/$chatId/messages"
        emitAll(
            db.collection("direct_chats")
                .document(chatId)
                .collection("messages")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DirectMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun openOrCreateDirectChat(
        partnerUid: String,
        partnerName: String,
        partnerAvatarBase64: String? = null
    ): Result<DirectChat> {
        var docPath = "direct_chats"
        return try {
            val uid = requireUserId()
            require(partnerUid.isNotBlank() && partnerUid != uid) {
                "Không thể mở cuộc trò chuyện với tài khoản này."
            }
            val myName = currentDisplayName("Bạn")
            val cleanPartnerName = partnerName.trim().ifBlank { "Bạn bè" }.take(60)
            val myAvatar = readOwnCompactAvatar(uid)
            val cleanPartnerAvatar = partnerAvatarBase64
                ?.takeIf { it.isNotBlank() && it.length <= MAX_MINI_AVATAR_LENGTH }
            val participants = listOf(uid, partnerUid).sorted()
            val chatId = directChatId(uid, partnerUid)
            docPath = "direct_chats/$chatId"
            val docRef = db.collection("direct_chats").document(chatId)

            val existing = try {
                docRef.get().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    e.code == FirebaseFirestoreException.Code.NOT_FOUND
                ) {
                    null
                } else {
                    throw e
                }
            }

            if (existing != null && existing.exists()) {
                val chat = existing.toObject(
                    DirectChat::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: DirectChat(
                    id = chatId,
                    participantUids = participants,
                    participantNames = mapOf(uid to myName, partnerUid to cleanPartnerName)
                )
                val avatarUpdates = mutableMapOf<String, Any>()
                if (!myAvatar.isNullOrBlank() && chat.participantAvatars[uid].isNullOrBlank()) {
                    avatarUpdates["participantAvatars.$uid"] = myAvatar
                }
                if (!cleanPartnerAvatar.isNullOrBlank() && chat.participantAvatars[partnerUid].isNullOrBlank()) {
                    avatarUpdates["participantAvatars.$partnerUid"] = cleanPartnerAvatar
                }
                if (avatarUpdates.isNotEmpty()) {
                    avatarUpdates["updatedAt"] = FieldValue.serverTimestamp()
                    runCatching { docRef.update(avatarUpdates).await() }
                }
                return Result.success(chat)
            }

            val namesMap = mapOf(uid to myName, partnerUid to cleanPartnerName)
            val avatarsMap = buildMap<String, String> {
                if (!myAvatar.isNullOrBlank()) put(uid, myAvatar)
                if (!cleanPartnerAvatar.isNullOrBlank()) put(partnerUid, cleanPartnerAvatar)
            }
            val payload = mutableMapOf<String, Any>(
                "participantUids" to participants,
                "participantNames" to namesMap,
                "lastMessageText" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarsMap.isNotEmpty()) {
                payload["participantAvatars"] = avatarsMap
            }
            docRef.set(payload).await()
            Result.success(
                DirectChat(
                    id = chatId,
                    participantUids = participants,
                    participantNames = namesMap,
                    participantAvatars = avatarsMap,
                    lastMessageText = ""
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun startDirectChatByFriendCode(rawFriendCode: String): Result<DirectChat> {
        val code = rawFriendCode.trim().uppercase()
        if (!FRIEND_CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Mã bạn bè phải gồm 4–16 ký tự chữ hoặc số."))
        }
        val path = "friend_codes/$code"
        return try {
            val uid = requireUserId()
            val snap = db.collection("friend_codes").document(code).get().await()
            val entry = snap.toObject(FriendCodeEntry::class.java)
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy bạn bè với mã \"$code\"."))
            if (entry.userId == uid) {
                return Result.failure(IllegalArgumentException("Đây là mã bạn bè của chính bạn."))
            }
            openOrCreateDirectChat(entry.userId, entry.displayName, entry.avatarBase64)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun sendDirectMessage(chat: DirectChat, rawText: String): Result<Unit> {
        val cleanText = rawText.trim().take(MAX_DIRECT_MESSAGE_LENGTH)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung tin nhắn."))
        }
        var docPath = "direct_chats/${chat.id}/messages"
        return try {
            val uid = requireUserId()
            val senderName = currentDisplayName("Bạn")
            require(chat.id.isNotBlank() && chat.participantUids.size == 2 && uid in chat.participantUids) {
                "Cuộc trò chuyện không hợp lệ."
            }

            val msgRef = db.collection("direct_chats")
                .document(chat.id)
                .collection("messages")
                .document()
            docPath = msgRef.path

            val messagePayload = mapOf(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName,
                "text" to cleanText,
                "participantUids" to chat.participantUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(messagePayload).await()

            val chatUpdate = mapOf(
                "lastMessageText" to cleanText,
                "lastMessageSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("direct_chats").document(chat.id).update(chatUpdate).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendDirectPhotoReply(
        partnerUid: String,
        partnerName: String,
        partnerAvatarBase64: String?,
        post: CirclePost,
        rawText: String
    ): Result<DirectChat> {
        val cleanText = rawText.trim().take(MAX_DIRECT_MESSAGE_LENGTH)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung tin nhắn."))
        }
        var docPath = "direct_chats"
        return try {
            val uid = requireUserId()
            val senderName = currentDisplayName("Bạn")
            val chat = openOrCreateDirectChat(
                partnerUid = partnerUid,
                partnerName = partnerName,
                partnerAvatarBase64 = partnerAvatarBase64
            ).getOrThrow()

            val quotedImage = compressBase64ForQuotedPost(post.imageBase64)
            val postAvatar = post.authorAvatarBase64
                ?.takeIf { it.isNotBlank() && it.length <= MAX_MINI_AVATAR_LENGTH }
                ?: partnerAvatarBase64?.takeIf { it.isNotBlank() && it.length <= MAX_MINI_AVATAR_LENGTH }
            val timeLabel = post.createdAt?.toDate()?.let { date ->
                SimpleDateFormat("HH:mm • dd/MM", Locale("vi", "VN")).format(date)
            } ?: "Vừa xong"

            val msgRef = db.collection("direct_chats")
                .document(chat.id)
                .collection("messages")
                .document()
            docPath = msgRef.path

            val messagePayload = mutableMapOf<String, Any?>(
                "chatId" to chat.id,
                "senderId" to uid,
                "senderName" to senderName,
                "text" to cleanText,
                "participantUids" to chat.participantUids,
                "replyPostId" to post.id.takeIf { it.isNotBlank() },
                "replyPostImageBase64" to quotedImage,
                "replyPostAuthorId" to post.authorId.takeIf { it.isNotBlank() }?.take(128),
                "replyPostAuthorName" to post.authorName.takeIf { it.isNotBlank() }?.take(60),
                "replyPostAuthorAvatarBase64" to postAvatar,
                "replyPostAuthorPhotoUrl" to post.authorPhotoUrl?.takeIf { it.isNotBlank() }?.take(600),
                "replyPostCaption" to post.caption.trim().takeIf { it.isNotBlank() }?.take(60),
                "replyPostTimeLabel" to timeLabel.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            @Suppress("UNCHECKED_CAST")
            msgRef.set(messagePayload as Map<String, Any>).await()

            val previewText = "📷 $cleanText".take(MAX_DIRECT_MESSAGE_LENGTH)
            db.collection("direct_chats").document(chat.id).update(
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(chat.copy(lastMessageText = previewText, lastMessageSenderId = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDirectMessage(chatId: String, messageId: String): Result<Unit> {
        val docPath = "direct_chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            require(chatId.isNotBlank() && messageId.isNotBlank())
            db.collection("direct_chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createCircle(name: String): Result<FriendCircle> {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > MAX_CIRCLE_NAME) {
            return Result.failure(IllegalArgumentException("Tên vòng bạn bè phải từ 1 đến 40 ký tự."))
        }
        var docPath = "circles"
        return try {
            val uid = requireUserId()
            val displayName = currentDisplayName("Trưởng nhóm")
            val inviteCode = generateCode(6)
            docPath = "circles/$inviteCode"
            val docRef = db.collection("circles").document(inviteCode)
            val memberNames = mapOf(uid to displayName)

            val payload = mapOf(
                "name" to cleanName,
                "inviteCode" to inviteCode,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "memberNames" to memberNames,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            Result.success(
                FriendCircle(
                    id = inviteCode,
                    name = cleanName,
                    inviteCode = inviteCode,
                    ownerId = uid,
                    memberUids = listOf(uid),
                    memberNames = memberNames
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun joinCircle(rawCode: String): Result<Unit> {
        val code = rawCode.trim().uppercase()
        if (!INVITE_CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Mã mời phải gồm 4–16 ký tự chữ hoặc số."))
        }
        val docPath = "circles/$code"
        return try {
            val uid = requireUserId()
            val displayName = currentDisplayName()
            db.collection("circles").document(code).update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "memberNames.$uid" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            val friendly = when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND ->
                    IllegalArgumentException("Không tìm thấy vòng bạn bè với mã \"$code\".")
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    IllegalArgumentException("Bạn đã là thành viên hoặc vòng đã đủ 20 người.")
                else -> e
            }
            Result.failure(friendly)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendCircleInviteByFriendCode(
        circle: FriendCircle,
        rawFriendCode: String
    ): Result<String> {
        val code = rawFriendCode.trim().uppercase()
        if (!FRIEND_CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Mã bạn bè phải gồm 4–16 ký tự chữ hoặc số."))
        }
        var docPath = "friend_codes/$code"
        return try {
            val uid = requireUserId()
            val fromName = currentDisplayName()
            val codeSnap = db.collection("friend_codes").document(code).get().await()
            val entry = codeSnap.toObject(FriendCodeEntry::class.java)
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy bạn bè với mã \"$code\"."))

            if (entry.userId == uid) {
                return Result.failure(IllegalArgumentException("Đây là mã bạn bè của chính bạn."))
            }

            val circleSnap = db.collection("circles").document(circle.id).get().await()
            val latestCircle = circleSnap.toObject(FriendCircle::class.java)
                ?: return Result.failure(IllegalStateException("Vòng bạn bè không còn tồn tại."))

            if (entry.userId in latestCircle.memberUids) {
                return Result.failure(IllegalArgumentException("${entry.displayName} đã ở trong vòng này rồi."))
            }
            if (latestCircle.memberUids.size >= 20) {
                return Result.failure(IllegalArgumentException("Vòng bạn bè đã đạt tối đa 20 thành viên."))
            }

            val inviteRef = db.collection("circle_invites").document()
            docPath = inviteRef.path
            val payload = mapOf(
                "circleId" to latestCircle.id,
                "circleName" to latestCircle.name.take(40),
                "fromUserId" to uid,
                "fromName" to fromName,
                "toUserId" to entry.userId,
                "toName" to entry.displayName.take(60),
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            inviteRef.set(payload).await()
            Result.success(entry.displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun respondToInvite(invite: CircleInvite, accept: Boolean): Result<Unit> {
        val docPath = "circle_invites/${invite.id}"
        return try {
            val uid = requireUserId()
            require(invite.id.isNotBlank())
            val nextStatus = if (accept) "ACCEPTED" else "DECLINED"
            db.collection("circle_invites").document(invite.id).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (accept) {
                val displayName = currentDisplayName()
                try {
                    db.collection("circles").document(invite.circleId).update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "memberNames.$uid" to displayName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (joinEx: FirebaseFirestoreException) {
                    if (joinEx.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        throw joinEx
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun leaveCircle(circleId: String): Result<Unit> {
        val docPath = "circles/$circleId"
        return try {
            val uid = requireUserId()
            require(circleId.isNotBlank())
            db.collection("circles").document(circleId).update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "memberNames.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendFriendRequestByCode(rawFriendCode: String): Result<String> {
        val code = rawFriendCode.trim().uppercase()
        if (!FRIEND_CODE_REGEX.matches(code)) {
            return Result.failure(IllegalArgumentException("Mã bạn bè phải gồm 4–16 ký tự chữ hoặc số."))
        }
        var docPath = "friend_codes/$code"
        return try {
            val uid = requireUserId()
            val fromName = currentDisplayName("Bạn")
            val codeSnap = db.collection("friend_codes").document(code).get().await()
            val entry = codeSnap.toObject(FriendCodeEntry::class.java)
                ?: return Result.failure(IllegalArgumentException("Không tìm thấy bạn bè với mã \"$code\"."))

            if (entry.userId == uid) {
                return Result.failure(IllegalArgumentException("Đây là mã bạn bè của chính bạn."))
            }

            val pairId = directChatId(uid, entry.userId)
            val existingFriendship = runCatching {
                db.collection("friendships").document(pairId).get().await()
            }.getOrNull()
            if (existingFriendship != null && existingFriendship.exists()) {
                return Result.failure(IllegalArgumentException("Bạn và ${entry.displayName} đã là bạn bè rồi."))
            }

            val reqRef = db.collection("friend_requests").document()
            docPath = reqRef.path
            val payload = mapOf(
                "fromUserId" to uid,
                "fromName" to fromName,
                "toUserId" to entry.userId,
                "toName" to entry.displayName.take(60),
                "status" to "PENDING",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            reqRef.set(payload).await()
            Result.success(entry.displayName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun respondToFriendRequest(request: FriendRequest, accept: Boolean): Result<Unit> {
        var docPath = "friend_requests/${request.id}"
        return try {
            val uid = requireUserId()
            require(request.id.isNotBlank())
            val nextStatus = if (accept) "ACCEPTED" else "DECLINED"
            db.collection("friend_requests").document(request.id).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (accept) {
                val myName = currentDisplayName("Bạn")
                val myAvatar = readOwnCompactAvatar(uid)
                val participants = listOf(uid, request.fromUserId).sorted()
                val pairId = directChatId(uid, request.fromUserId)
                docPath = "friendships/$pairId"
                val existing = runCatching {
                    db.collection("friendships").document(pairId).get().await()
                }.getOrNull()
                if (existing == null || !existing.exists()) {
                    val payload = mutableMapOf<String, Any>(
                        "participantUids" to participants,
                        "participantNames" to mapOf(
                            uid to myName,
                            request.fromUserId to request.fromName.ifBlank { "Bạn bè" }.take(60)
                        ),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (!myAvatar.isNullOrBlank()) {
                        payload["participantAvatars"] = mapOf(uid to myAvatar)
                    }
                    db.collection("friendships").document(pairId).set(payload).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeFriend(partnerUid: String): Result<Unit> {
        val uid = requireUserId()
        val pairId = directChatId(uid, partnerUid)
        val docPath = "friendships/$pairId"
        return try {
            db.collection("friendships").document(pairId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishSharedFriendsPost(
        friendUids: List<String>,
        collageBitmap: Bitmap,
        caption: String,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        filter: PhotoFilter,
        dateLabel: String
    ): Result<Unit> {
        var docPath = "circle_posts"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Bạn cần đăng nhập Google để đăng ảnh.")
            val uid = user.uid
            val members = (listOf(uid) + friendUids)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SHARED_MEMBERS)

            val encodedImage = encodeBitmapForFirestore(collageBitmap)
            val cleanCaption = caption.trim().take(36)
            val authorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Thành viên").take(60)
            val authorPhotoUrl = user.photoUrl?.toString()?.take(600)
            val authorAvatarBase64 = readOwnCompactAvatar(uid)

            val docRef = db.collection("circle_posts").document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any?>(
                "circleId" to SHARED_CIRCLE_ID,
                "circleName" to SHARED_CIRCLE_NAME,
                "authorId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to authorPhotoUrl,
                "authorAvatarBase64" to authorAvatarBase64,
                "caption" to cleanCaption,
                "layout" to layout.name,
                "frameStyle" to frameStyle.name,
                "filter" to filter.name,
                "dateLabel" to dateLabel.take(20),
                "imageBase64" to encodedImage,
                "memberUids" to members,
                "likedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, String>(),
                "reactorNames" to emptyMap<String, String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            @Suppress("UNCHECKED_CAST")
            docRef.set(payload as Map<String, Any>).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun publishPost(
        circle: FriendCircle,
        collageBitmap: Bitmap,
        caption: String,
        layout: BoothLayout,
        frameStyle: FrameStyle,
        filter: PhotoFilter,
        dateLabel: String
    ): Result<Unit> {
        var docPath = "circle_posts"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Bạn cần đăng nhập Google để đăng ảnh.")
            val uid = user.uid
            require(circle.id.isNotBlank()) { "Vòng bạn bè không hợp lệ." }

            val latestCircleSnap = db.collection("circles").document(circle.id).get().await()
            val latestCircle = latestCircleSnap.toObject(FriendCircle::class.java)
                ?: throw IllegalStateException("Không tìm thấy vòng bạn bè.")
            val members = latestCircle.memberUids
            require(uid in members) { "Bạn không còn thuộc vòng bạn bè này." }

            val encodedImage = encodeBitmapForFirestore(collageBitmap)
            val cleanCaption = caption.trim().take(36)
            val authorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Thành viên").take(60)
            val authorPhotoUrl = user.photoUrl?.toString()?.take(600)
            val authorAvatarBase64 = readOwnCompactAvatar(uid)

            val docRef = db.collection("circle_posts").document()
            docPath = docRef.path

            val payload = mutableMapOf<String, Any?>(
                "circleId" to circle.id,
                "circleName" to latestCircle.name.take(40),
                "authorId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to authorPhotoUrl,
                "authorAvatarBase64" to authorAvatarBase64,
                "caption" to cleanCaption,
                "layout" to layout.name,
                "frameStyle" to frameStyle.name,
                "filter" to filter.name,
                "dateLabel" to dateLabel.take(20),
                "imageBase64" to encodedImage,
                "memberUids" to members,
                "likedBy" to emptyList<String>(),
                "reactions" to emptyMap<String, String>(),
                "reactorNames" to emptyMap<String, String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            @Suppress("UNCHECKED_CAST")
            docRef.set(payload as Map<String, Any>).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleLike(post: CirclePost): Result<Unit> {
        val docPath = "circle_posts/${post.id}"
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank()) { "Mã bài đăng không hợp lệ." }
            val alreadyLiked = uid in post.likedBy
            val mutation = if (alreadyLiked) {
                FieldValue.arrayRemove(uid)
            } else {
                FieldValue.arrayUnion(uid)
            }
            db.collection("circle_posts").document(post.id).update(
                mapOf(
                    "likedBy" to mutation,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reactToPost(post: CirclePost, emoji: String?): Result<Unit> {
        val docPath = "circle_posts/${post.id}"
        return try {
            val uid = requireUserId()
            require(post.id.isNotBlank()) { "Mã bài đăng không hợp lệ." }
            val cleanEmoji = emoji?.trim()?.take(16).orEmpty()
            val existingEmoji = post.reactions[uid]
            val shouldRemove = cleanEmoji.isEmpty() || existingEmoji == cleanEmoji
            if (shouldRemove && existingEmoji == null) {
                return Result.success(Unit)
            }
            val myName = currentDisplayName("Bạn")
            val updates = if (shouldRemove) {
                mapOf(
                    "reactions.$uid" to FieldValue.delete(),
                    "reactorNames.$uid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "reactions.$uid" to cleanEmoji,
                    "reactorNames.$uid" to myName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            db.collection("circle_posts").document(post.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val docPath = "circle_posts/$postId"
        return try {
            requireUserId()
            require(postId.isNotBlank()) { "Mã bài đăng không hợp lệ." }
            db.collection("circle_posts").document(postId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendPostComment(post: CirclePost, rawText: String): Result<Unit> {
        val cleanText = rawText.trim().take(MAX_COMMENT_LENGTH)
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("Vui lòng nhập nội dung tin nhắn."))
        }
        var docPath = "circle_posts/${post.id}/comments"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Bạn cần đăng nhập Google để nhắn tin.")
            val uid = user.uid
            require(post.id.isNotBlank())

            val postSnap = db.collection("circle_posts").document(post.id).get().await()
            val latestPost = postSnap.toObject(CirclePost::class.java)
                ?: throw IllegalStateException("Bài đăng không còn tồn tại.")
            val members = latestPost.memberUids
            require(uid in members) { "Bạn không còn thuộc vòng bạn bè này." }

            val commentRef = db.collection("circle_posts")
                .document(post.id)
                .collection("comments")
                .document()
            docPath = commentRef.path

            val authorName = (user.displayName?.takeIf { it.isNotBlank() } ?: "Thành viên").take(60)
            val authorPhotoUrl = user.photoUrl?.toString()?.take(600)
            val authorAvatarBase64 = readOwnCompactAvatar(uid)

            val payload = mutableMapOf<String, Any?>(
                "postId" to post.id,
                "circleId" to latestPost.circleId,
                "authorId" to uid,
                "authorName" to authorName,
                "authorPhotoUrl" to authorPhotoUrl,
                "authorAvatarBase64" to authorAvatarBase64,
                "text" to cleanText,
                "memberUids" to members,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            @Suppress("UNCHECKED_CAST")
            commentRef.set(payload as Map<String, Any>).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePostComment(postId: String, commentId: String): Result<Unit> {
        val docPath = "circle_posts/$postId/comments/$commentId"
        return try {
            requireUserId()
            require(postId.isNotBlank() && commentId.isNotBlank())
            db.collection("circle_posts")
                .document(postId)
                .collection("comments")
                .document(commentId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncOwnPostMemberships(
        circles: List<FriendCircle>,
        friendships: List<Friendship>,
        posts: List<CirclePost>
    ) {
        val uid = auth.currentUser?.uid ?: return
        val circleMap = circles.associateBy { it.id }

        val unifiedFriends = linkedSetOf(uid)
        friendships.forEach { friendship ->
            friendship.participantUids.forEach { memberUid ->
                if (memberUid.isNotBlank()) unifiedFriends.add(memberUid)
            }
        }
        circles.forEach { circle ->
            circle.memberUids.forEach { memberUid ->
                if (memberUid.isNotBlank()) unifiedFriends.add(memberUid)
            }
        }
        val unifiedMemberList = unifiedFriends.take(MAX_SHARED_MEMBERS)

        for (post in posts) {
            if (post.authorId != uid || post.id.isBlank()) continue
            val latestMembers = if (post.circleId == SHARED_CIRCLE_ID) {
                unifiedMemberList
            } else {
                circleMap[post.circleId]?.memberUids ?: continue
            }
            if (post.memberUids.toSet() != latestMembers.toSet() && uid in latestMembers) {
                try {
                    db.collection("circle_posts").document(post.id).update(
                        mapOf(
                            "memberUids" to latestMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.UPDATE, "circle_posts/${post.id}")
                }
            }
        }
    }

    private suspend fun encodeBitmapForFirestore(source: Bitmap): String =
        withContext(Dispatchers.Default) {
            val maxDim = 920
            val scale = minOf(1f, maxDim.toFloat() / max(source.width, source.height).toFloat())
            val scaled = if (scale < 0.99f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }

            try {
                var quality = 82
                var base64 = ""
                while (quality >= 48) {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    base64 = encoded
                    if (encoded.length <= MAX_BASE64_LENGTH) {
                        break
                    }
                    quality -= 10
                }
                if (base64.length > MAX_BASE64_LENGTH) {
                    error("Ảnh vượt quá giới hạn kích thước tải lên.")
                }
                base64
            } finally {
                if (scaled !== source) {
                    scaled.recycle()
                }
            }
        }

    private suspend fun encodeAvatarForFirestore(source: Bitmap): String =
        withContext(Dispatchers.Default) {
            val maxDim = 280
            val scale = minOf(1f, maxDim.toFloat() / max(source.width, source.height).toFloat())
            val scaled = if (scale < 0.99f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }

            try {
                var quality = 85
                var base64 = ""
                while (quality >= 45) {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    base64 = encoded
                    if (encoded.length <= MAX_AVATAR_BASE64_LENGTH) {
                        break
                    }
                    quality -= 10
                }
                if (base64.length > MAX_AVATAR_BASE64_LENGTH) {
                    error("Ảnh đại diện quá lớn, vui lòng chọn ảnh nhẹ hơn.")
                }
                base64
            } finally {
                if (scaled !== source) {
                    scaled.recycle()
                }
            }
        }

    private suspend fun encodeMiniAvatarForSharing(source: Bitmap): String =
        withContext(Dispatchers.Default) {
            val maxDim = 96
            val scale = minOf(1f, maxDim.toFloat() / max(source.width, source.height).toFloat())
            val scaled = if (scale < 0.99f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }

            try {
                var quality = 78
                var base64 = ""
                while (quality >= 40) {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    base64 = encoded
                    if (encoded.length <= MAX_MINI_AVATAR_LENGTH) {
                        break
                    }
                    quality -= 10
                }
                base64.takeIf { it.length <= MAX_MINI_AVATAR_LENGTH }.orEmpty()
            } finally {
                if (scaled !== source && !scaled.isRecycled) {
                    scaled.recycle()
                }
            }
        }

    private suspend fun compressBase64ForMiniAvatar(encoded: String): String? =
        withContext(Dispatchers.Default) {
            if (encoded.isBlank()) return@withContext null
            if (encoded.length <= MAX_MINI_AVATAR_LENGTH) return@withContext encoded
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                ?: return@withContext null
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?: return@withContext null
            try {
                encodeMiniAvatarForSharing(bmp).takeIf { it.isNotBlank() }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }

    private suspend fun encodeQuotedPostThumbnail(source: Bitmap): String =
        withContext(Dispatchers.Default) {
            val maxDim = 420
            val scale = minOf(1f, maxDim.toFloat() / max(source.width, source.height).toFloat())
            val scaled = if (scale < 0.99f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }

            try {
                var quality = 76
                var base64 = ""
                while (quality >= 40) {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    base64 = encoded
                    if (encoded.length <= MAX_QUOTED_IMAGE_LENGTH) {
                        break
                    }
                    quality -= 10
                }
                base64.takeIf { it.length <= MAX_QUOTED_IMAGE_LENGTH }.orEmpty()
            } finally {
                if (scaled !== source && !scaled.isRecycled) {
                    scaled.recycle()
                }
            }
        }

    private suspend fun compressBase64ForQuotedPost(encoded: String): String? =
        withContext(Dispatchers.Default) {
            if (encoded.isBlank()) return@withContext null
            if (encoded.length <= MAX_QUOTED_IMAGE_LENGTH) return@withContext encoded
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                ?: return@withContext null
            val bmp = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?: return@withContext null
            try {
                encodeQuotedPostThumbnail(bmp).takeIf { it.isNotBlank() }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }

    private fun generateCode(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(length) {
            repeat(length) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
    }

    companion object {
        const val SHARED_CIRCLE_ID = "FRIENDS_ALL"
        const val SHARED_CIRCLE_NAME = "Friends"
        const val MAX_SHARED_MEMBERS = 50
        const val MAX_BIO_LENGTH = 160
        private const val MAX_CIRCLE_NAME = 40
        private const val MAX_COMMENT_LENGTH = 300
        private const val MAX_DIRECT_MESSAGE_LENGTH = 500
        private const val MAX_BASE64_LENGTH = 500_000
        private const val MAX_AVATAR_BASE64_LENGTH = 220_000
        private const val MAX_MINI_AVATAR_LENGTH = 32_000
        private const val MAX_QUOTED_IMAGE_LENGTH = 200_000
        private val INVITE_CODE_REGEX = Regex("^[A-Za-z0-9_\\-]{4,16}$")
        private val FRIEND_CODE_REGEX = Regex("^[A-Z0-9]{4,16}$")
    }
}
