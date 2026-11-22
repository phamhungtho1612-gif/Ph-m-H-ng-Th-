package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CircleFeedUiState
import com.example.model.DirectChat
import com.example.model.DirectFriendItem
import com.example.model.DirectMessage
import com.example.ui.components.SmallAvatarBadge
import com.example.ui.components.decodeAvatarBitmap
import com.example.ui.theme.studioAccentInk
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DirectMessagesScreen(
    currentUserId: String,
    chatsState: CircleFeedUiState<List<DirectChat>>,
    friends: List<DirectFriendItem>,
    friendAvatars: Map<String, Pair<String?, String?>>,
    activeChat: DirectChat?,
    messagesState: CircleFeedUiState<List<DirectMessage>>,
    isBusy: Boolean,
    isSending: Boolean,
    onOpenChat: (DirectChat) -> Unit,
    onOpenFriendChat: (uid: String, name: String) -> Unit,
    onStartChatByFriendCode: (String) -> Unit,
    onCloseChat: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onDeleteMessage: (chatId: String, messageId: String) -> Unit,
    onGoToBooth: () -> Unit,
    onGoToCircle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewChatDialog by remember { mutableStateOf(false) }

    if (activeChat != null) {
        BackHandler {
            onCloseChat()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (activeChat != null) {
            DirectChatThreadView(
                chat = activeChat,
                currentUserId = currentUserId,
                friendAvatars = friendAvatars,
                messagesState = messagesState,
                isSending = isSending,
                onBack = onCloseChat,
                onSendMessage = onSendMessage,
                onDeleteMessage = { msgId -> onDeleteMessage(activeChat.id, msgId) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            )
        } else {
            DirectInboxView(
                currentUserId = currentUserId,
                chatsState = chatsState,
                friends = friends,
                friendAvatars = friendAvatars,
                onOpenChat = onOpenChat,
                onOpenFriendChat = onOpenFriendChat,
                onNewChatClick = { showNewChatDialog = true },
                onGoToBooth = onGoToBooth,
                onGoToCircle = onGoToCircle,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            )
        }
    }

    if (showNewChatDialog) {
        StartDirectChatDialog(
            isBusy = isBusy,
            onDismiss = { showNewChatDialog = false },
            onStart = { code ->
                showNewChatDialog = false
                onStartChatByFriendCode(code)
            }
        )
    }
}

@Composable
private fun DirectInboxView(
    currentUserId: String,
    chatsState: CircleFeedUiState<List<DirectChat>>,
    friends: List<DirectFriendItem>,
    friendAvatars: Map<String, Pair<String?, String?>>,
    onOpenChat: (DirectChat) -> Unit,
    onOpenFriendChat: (uid: String, name: String) -> Unit,
    onNewChatClick: () -> Unit,
    onGoToBooth: () -> Unit,
    onGoToCircle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentInk = studioAccentInk()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dm_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.dm_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onNewChatClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("dm_new_chat_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dm_new_chat_btn),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Surface(
                    onClick = onGoToBooth,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("dm_back_to_booth_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.nav_booth),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Body
        Box(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
        ) {
            when (chatsState) {
                is CircleFeedUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is CircleFeedUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = chatsState.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }

                is CircleFeedUiState.Success -> {
                    val chats = chatsState.data
                    if (chats.isEmpty() && friends.isEmpty()) {
                        EmptyDirectMessagesCard(
                            onNewChatClick = onNewChatClick,
                            onGoToCircle = onGoToCircle
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (friends.isNotEmpty()) {
                                item(key = "friends_strip") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.dm_friends_section),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(friends, key = { it.uid }) { friend ->
                                                Surface(
                                                    onClick = { onOpenFriendChat(friend.uid, friend.displayName) },
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                                                    ),
                                                    modifier = Modifier.width(104.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(10.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        SmallAvatarBadge(
                                                            displayName = friend.displayName,
                                                            avatarBase64 = friend.avatarBase64 ?: friendAvatars[friend.uid]?.first,
                                                            photoUrl = friend.photoUrl ?: friendAvatars[friend.uid]?.second,
                                                            size = 42.dp
                                                        )
                                                        Text(
                                                            text = friend.displayName,
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        Text(
                                                            text = friend.circleNames.firstOrNull() ?: "",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "chats_header") {
                                Text(
                                    text = stringResource(R.string.dm_chats_section),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (chats.isEmpty()) {
                                item(key = "no_threads_yet") {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Chọn một người bạn phía trên hoặc nhập mã bạn bè để bắt đầu nhắn tin riêng.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(18.dp)
                                        )
                                    }
                                }
                            } else {
                                items(chats, key = { it.id }) { chat ->
                                    val partnerUid = chat.participantUids.firstOrNull { it != currentUserId }.orEmpty()
                                    val partnerName = chat.participantNames[partnerUid]
                                        ?.takeIf { it.isNotBlank() }
                                        ?: friends.firstOrNull { it.uid == partnerUid }?.displayName
                                        ?: "Bạn bè"
                                    val timeLabel = remember(chat.lastMessageAt, chat.updatedAt) {
                                        val d = (chat.lastMessageAt ?: chat.updatedAt)?.toDate()
                                        if (d != null) {
                                            SimpleDateFormat("HH:mm • dd/MM", Locale("vi", "VN")).format(d)
                                        } else {
                                            ""
                                        }
                                    }

                                    Surface(
                                        onClick = { onOpenChat(chat) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SmallAvatarBadge(
                                                displayName = partnerName,
                                                avatarBase64 = chat.participantAvatars[partnerUid]
                                                    ?: friendAvatars[partnerUid]?.first,
                                                photoUrl = friendAvatars[partnerUid]?.second,
                                                size = 44.dp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = partnerName,
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (timeLabel.isNotBlank()) {
                                                        Text(
                                                            text = timeLabel,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(3.dp))
                                                val previewText = if (chat.lastMessageText.isNotBlank()) {
                                                    if (chat.lastMessageSenderId == currentUserId) {
                                                        "Bạn: ${chat.lastMessageText}"
                                                    } else {
                                                        chat.lastMessageText
                                                    }
                                                } else {
                                                    stringResource(R.string.dm_start_tap_hint)
                                                }
                                                Text(
                                                    text = previewText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectChatThreadView(
    chat: DirectChat,
    currentUserId: String,
    friendAvatars: Map<String, Pair<String?, String?>>,
    messagesState: CircleFeedUiState<List<DirectMessage>>,
    isSending: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String, () -> Unit) -> Unit,
    onDeleteMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val partnerUid = chat.participantUids.firstOrNull { it != currentUserId }.orEmpty()
    val partnerName = chat.participantNames[partnerUid]?.takeIf { it.isNotBlank() } ?: "Bạn bè"
    val partnerAvatarBase64 = chat.participantAvatars[partnerUid] ?: friendAvatars[partnerUid]?.first
    val partnerPhotoUrl = friendAvatars[partnerUid]?.second
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val quickPrompts = remember {
        listOf("Chào bạn 👋", "Đi chụp photobooth nha!", "Gửi ảnh xem với ✨")
    }
    val quickEmojis = remember { listOf("🔥", "😍", "💛") }

    val messages = (messagesState as? CircleFeedUiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier) {
        // Thread Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Quay lại"
                )
            }
            SmallAvatarBadge(
                displayName = partnerName,
                avatarBase64 = partnerAvatarBase64,
                photoUrl = partnerPhotoUrl,
                size = 34.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dm_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

        // Messages List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (messagesState) {
                is CircleFeedUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is CircleFeedUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is CircleFeedUiState.Success -> {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = null,
                                    tint = studioAccentInk(),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.dm_no_messages_yet, partnerName),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickPrompts.forEach { prompt ->
                                    Surface(
                                        onClick = { draft = prompt },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isMine = msg.senderId == currentUserId
                                val timeLabel = remember(msg.createdAt) {
                                    val d = msg.createdAt?.toDate()
                                    if (d != null) {
                                        SimpleDateFormat("HH:mm", Locale("vi", "VN")).format(d)
                                    } else {
                                        "Vừa xong"
                                    }
                                }
                                val hasQuotedPhoto = !msg.replyPostImageBase64.isNullOrBlank()
                                val quotedBitmap = remember(msg.id, msg.replyPostImageBase64) {
                                    decodeAvatarBitmap(msg.replyPostImageBase64)?.asImageBitmap()
                                }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                                ) {
                                    if (hasQuotedPhoto && quotedBitmap != null) {
                                        val quoteAuthorName = msg.replyPostAuthorName?.takeIf { it.isNotBlank() }
                                            ?: partnerName
                                        val quoteAuthorAvatar = msg.replyPostAuthorAvatarBase64
                                            ?: msg.replyPostAuthorId?.let { friendAvatars[it]?.first }
                                        val quoteAuthorUrl = msg.replyPostAuthorPhotoUrl
                                            ?: msg.replyPostAuthorId?.let { friendAvatars[it]?.second }

                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 268.dp)
                                                .fillMaxWidth(0.78f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(22.dp))
                                                .background(Color.Black.copy(alpha = 0.2f))
                                        ) {
                                            Image(
                                                bitmap = quotedBitmap,
                                                contentDescription = msg.replyPostCaption ?: quoteAuthorName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Floating top-left author pill with small circular avatar
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Black.copy(alpha = 0.56f),
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(
                                                        start = 5.dp,
                                                        end = 10.dp,
                                                        top = 4.dp,
                                                        bottom = 4.dp
                                                    ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    SmallAvatarBadge(
                                                        displayName = quoteAuthorName,
                                                        avatarBase64 = quoteAuthorAvatar,
                                                        photoUrl = quoteAuthorUrl,
                                                        size = 22.dp,
                                                        borderColor = Color.White.copy(alpha = 0.7f),
                                                        borderWidth = 1.dp
                                                    )
                                                    Text(
                                                        text = quoteAuthorName,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.widthIn(max = 120.dp)
                                                    )
                                                    val qTime = msg.replyPostTimeLabel?.takeIf { it.isNotBlank() }
                                                    if (qTime != null) {
                                                        Text(
                                                            text = qTime,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.White.copy(alpha = 0.78f),
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            // Floating bottom caption pill
                                            val qCaption = msg.replyPostCaption?.trim().orEmpty()
                                            if (qCaption.isNotEmpty()) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color.Black.copy(alpha = 0.56f),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                ) {
                                                    Text(
                                                        text = qCaption,
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.SemiBold
                                                        ),
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = if (isMine) 18.dp else 4.dp,
                                            bottomEnd = if (isMine) 4.dp else 18.dp
                                        ),
                                        color = if (isMine) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        modifier = Modifier.widthIn(max = 285.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(
                                                horizontal = 14.dp,
                                                vertical = 9.dp
                                            )
                                        ) {
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isMine) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = timeLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isMine) {
                                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                if (isMine && msg.id.isNotBlank()) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = "Xóa tin nhắn",
                                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clickable { onDeleteMessage(msg.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

        // Message Input Bar with Quick Emojis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 500) draft = it },
                placeholder = { Text(stringResource(R.string.dm_input_hint, partnerName)) },
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank() && !isSending) {
                            onSendMessage(draft) { draft = "" }
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("dm_input")
            )

            if (draft.isBlank()) {
                quickEmojis.forEach { emoji ->
                    Surface(
                        onClick = { if (!isSending) onSendMessage(emoji) {} },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (draft.isNotBlank() && !isSending) {
                        onSendMessage(draft) { draft = "" }
                    }
                },
                enabled = draft.isNotBlank() && !isSending,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("dm_send_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.send_reply)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDirectMessagesCard(
    onNewChatClick: () -> Unit,
    onGoToCircle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                        contentDescription = null,
                        tint = studioAccentInk(),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.dm_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.dm_empty_desc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onNewChatClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dm_start_by_code_btn),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onGoToCircle,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nav_circle),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun StartDirectChatDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dm_start_by_code_title)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= 16) code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }
                },
                label = { Text(stringResource(R.string.friend_code_input_label)) },
                placeholder = { Text(stringResource(R.string.friend_code_input_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dm_friend_code_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onStart(code) },
                enabled = code.trim().length >= 4 && !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.dm_start_by_code_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
