package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.imaging.GallerySaver
import com.example.model.CircleFeedUiState
import com.example.model.CircleInvite
import com.example.model.CirclePost
import com.example.model.DirectFriendItem
import com.example.model.FriendRequest
import com.example.model.PostComment
import com.example.model.PostReactionItem
import com.example.model.ReplyDestinationMode
import com.example.ui.components.SmallAvatarBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.studioAccentInk
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendFeedSinglePhotoPage(
    post: CirclePost,
    pageIndex: Int,
    totalPosts: Int,
    currentUserId: String,
    friendAvatars: Map<String, Pair<String?, String?>>,
    pendingBadgeCount: Int,
    onGoToBooth: () -> Unit,
    onToggleGridMode: () -> Unit,
    onOpenFriendsManager: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestSignOut: () -> Unit,
    onReactEmoji: (String?) -> Unit,
    onViewReactionsClick: () -> Unit,
    onReplyClick: () -> Unit,
    onInspectClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentInk = studioAccentInk()
    var showEmojiPicker by remember { mutableStateOf(false) }

    val decodedBitmap = remember(post.id, post.imageBase64) {
        decodeBase64Bitmap(post.imageBase64)
    }
    val imageBitmap = remember(decodedBitmap) { decodedBitmap?.asImageBitmap() }
    val authorAvatarBase64 = post.authorAvatarBase64?.takeIf { it.isNotBlank() }
        ?: friendAvatars[post.authorId]?.first
    val authorPhotoUrl = post.authorPhotoUrl?.takeIf { it.isNotBlank() }
        ?: friendAvatars[post.authorId]?.second

    val quickEmojis = remember { listOf("🔥", "😍", "💛", "❤️") }
    val myReaction = post.reactions[currentUserId]
        ?: if (currentUserId in post.likedBy) "❤️" else null

    val reactionCounts = remember(post.reactions, post.likedBy) {
        val counts = linkedMapOf<String, Int>()
        for ((_, emoji) in post.reactions) {
            if (emoji.isNotBlank()) counts[emoji] = (counts[emoji] ?: 0) + 1
        }
        for (uid in post.likedBy) {
            if (uid !in post.reactions) {
                counts["❤️"] = (counts["❤️"] ?: 0) + 1
            }
        }
        counts.entries.sortedByDescending { it.value }
    }
    val totalReactionCount = reactionCounts.sumOf { it.value }

    val timeText = remember(post.createdAt) {
        val date = post.createdAt?.toDate()
        if (date != null) {
            SimpleDateFormat("HH:mm • dd/MM", Locale("vi", "VN")).format(date)
        } else {
            "Vừa xong"
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FeedTopHeaderBar(
                badgeLabel = "${pageIndex + 1}/$totalPosts",
                pendingBadgeCount = pendingBadgeCount,
                isGridMode = false,
                onToggleGridMode = onToggleGridMode,
                onGoToBooth = onGoToBooth,
                onOpenFriendsManager = onOpenFriendsManager,
                onOpenMessages = onOpenMessages,
                onOpenSettings = onOpenSettings,
                onRequestSignOut = onRequestSignOut
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Single Full-Viewport Locket-Style Photo Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Author & Time Header with compact small circular avatar + Save / Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallAvatarBadge(
                            displayName = post.authorName,
                            avatarBase64 = authorAvatarBase64,
                            photoUrl = authorPhotoUrl,
                            size = 28.dp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        if (decodedBitmap != null) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val res = GallerySaver.saveBitmapToGallery(context, decodedBitmap)
                                        val msg = if (res.isSuccess) {
                                            context.getString(R.string.saved_success_message)
                                        } else {
                                            context.getString(
                                                R.string.saved_error_message,
                                                res.exceptionOrNull()?.localizedMessage ?: ""
                                            )
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.circle_save_friend_photo),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (post.authorId == currentUserId) {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.circle_delete_post),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hero Photo Container
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable(onClick = onInspectClick)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            val aspect = decodedBitmap!!.width.toFloat() / decodedBitmap.height.toFloat()
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = post.caption.ifBlank { post.authorName },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .aspectRatio(aspect, matchHeightConstraintsFirst = aspect < 1f)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Caption + Reaction Summary Badge (tappable to view who reacted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = post.caption.ifBlank { "Khoảnh khắc Friends ♡" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            onClick = onViewReactionsClick,
                            shape = CircleShape,
                            color = if (totalReactionCount > 0) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("view_reactions_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (reactionCounts.isEmpty()) {
                                    Text(text = "♡ 0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    reactionCounts.take(3).forEach { (emoji, count) ->
                                        Text(
                                            text = "$emoji $count",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = accentInk
                                        )
                                    }
                                    if (reactionCounts.size > 3) {
                                        Text(
                                            text = "+${reactionCounts.size - 3}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accentInk
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Reply Input Pill + Quick Emoji Reaction Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = onReplyClick,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("reply_post_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (post.authorId != currentUserId) {
                                        stringResource(R.string.reply_dm_hint)
                                    } else {
                                        stringResource(R.string.reply_public_hint)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.reply_to_photo),
                                    tint = accentInk,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        quickEmojis.forEach { emoji ->
                            val isSelected = myReaction == emoji
                            Surface(
                                onClick = {
                                    onReactEmoji(if (isSelected) null else emoji)
                                },
                                shape = CircleShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, accentInk)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("quick_react_$emoji")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 18.sp)
                                }
                            }
                        }

                        Surface(
                            onClick = { showEmojiPicker = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("more_reactions_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AddReaction,
                                    contentDescription = stringResource(R.string.reactions_title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Vertical Swipe Navigation Hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                if (pageIndex < totalPosts - 1) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vuốt lên để xem ảnh tiếp theo (${pageIndex + 2}/$totalPosts)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (totalPosts == 1) {
                            "Vuốt xuống để về Phòng chụp"
                        } else {
                            "Đã xem hết $totalPosts ảnh • Vuốt xuống để xem lại hoặc chụp mới"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            currentReaction = myReaction,
            onSelectEmoji = { emoji ->
                showEmojiPicker = false
                onReactEmoji(emoji)
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

@Composable
fun FriendsFeedFallbackPage(
    postsState: CircleFeedUiState<List<CirclePost>>,
    myFriendCode: String,
    friendRequests: List<FriendRequest>,
    invites: List<CircleInvite>,
    friends: List<DirectFriendItem>,
    isBusy: Boolean,
    onGoToBooth: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestSignOut: () -> Unit,
    onSendFriendRequest: (String, () -> Unit) -> Unit,
    onRespondToFriendRequest: (FriendRequest, Boolean) -> Unit,
    onRespondToInvite: (CircleInvite, Boolean) -> Unit,
    onOpenDirectChat: (uid: String, name: String) -> Unit,
    onRemoveFriend: (uid: String, name: String) -> Unit,
    onPinWidget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val pendingCount = friendRequests.size + invites.size
    var friendCodeInput by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FeedTopHeaderBar(
                badgeLabel = stringResource(R.string.circle_title),
                pendingBadgeCount = pendingCount,
                isGridMode = null,
                onToggleGridMode = null,
                onGoToBooth = onGoToBooth,
                onOpenFriendsManager = null,
                onOpenMessages = onOpenMessages,
                onOpenSettings = onOpenSettings,
                onRequestSignOut = onRequestSignOut
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            ) {
                when (postsState) {
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
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = postsState.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    }

                    is CircleFeedUiState.Success -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (myFriendCode.isNotBlank()) {
                                item(key = "my_code") {
                                    MyFriendCodeCard(
                                        friendCode = myFriendCode,
                                        onCopyCode = {
                                            clipboard.setText(AnnotatedString(myFriendCode))
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.circle_copied_toast, myFriendCode),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onShareCode = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Kết bạn với mình trên Friends bằng mã bạn bè: $myFriendCode"
                                                )
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Chia sẻ mã bạn bè")
                                            )
                                        }
                                    )
                                }
                            }

                            item(key = "add_friend_card") {
                                AddFriendByCodeCard(
                                    friendCodeInput = friendCodeInput,
                                    onCodeChange = { friendCodeInput = it },
                                    isBusy = isBusy,
                                    onSendRequest = {
                                        onSendFriendRequest(friendCodeInput) {
                                            friendCodeInput = ""
                                        }
                                    }
                                )
                            }

                            if (friendRequests.isNotEmpty()) {
                                item(key = "pending_friend_requests") {
                                    PendingFriendRequestsCard(
                                        requests = friendRequests,
                                        isBusy = isBusy,
                                        onAccept = { req -> onRespondToFriendRequest(req, true) },
                                        onDecline = { req -> onRespondToFriendRequest(req, false) }
                                    )
                                }
                            }

                            if (invites.isNotEmpty()) {
                                item(key = "pending_legacy_invites") {
                                    PendingInvitesCard(
                                        invites = invites,
                                        isBusy = isBusy,
                                        onAccept = { inv -> onRespondToInvite(inv, true) },
                                        onDecline = { inv -> onRespondToInvite(inv, false) }
                                    )
                                }
                            }

                            if (friends.isNotEmpty()) {
                                item(key = "friends_list") {
                                    MutualFriendsListCard(
                                        friends = friends,
                                        onChatClick = onOpenDirectChat,
                                        onRemoveClick = onRemoveFriend
                                    )
                                }
                            }

                            item(key = "empty_feed_cta") {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoCamera,
                                                contentDescription = null,
                                                tint = studioAccentInk(),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Text(
                                            text = if (friends.isEmpty()) {
                                                stringResource(R.string.circle_empty_title)
                                            } else {
                                                stringResource(R.string.circle_no_posts_title)
                                            },
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = if (friends.isEmpty()) {
                                                stringResource(R.string.circle_empty_desc)
                                            } else {
                                                stringResource(R.string.circle_no_posts_desc)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(
                                            onClick = onGoToBooth,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Text(
                                                text = "Chụp ảnh ngay",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = onPinWidget,
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AddToHomeScreen,
                                                contentDescription = null,
                                                tint = studioAccentInk(),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.settings_add_widget_btn),
                                                color = studioAccentInk()
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

@Composable
fun FriendsFeedGridPage(
    posts: List<CirclePost>,
    friendAvatars: Map<String, Pair<String?, String?>>,
    pendingBadgeCount: Int,
    onGoToBooth: () -> Unit,
    onToggleGridMode: () -> Unit,
    onSelectPost: (Int) -> Unit,
    onOpenFriendsManager: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FeedTopHeaderBar(
                badgeLabel = "${posts.size} ảnh",
                pendingBadgeCount = pendingBadgeCount,
                isGridMode = true,
                onToggleGridMode = onToggleGridMode,
                onGoToBooth = onGoToBooth,
                onOpenFriendsManager = onOpenFriendsManager,
                onOpenMessages = onOpenMessages,
                onOpenSettings = onOpenSettings,
                onRequestSignOut = onRequestSignOut
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .testTag("friends_feed_grid")
            ) {
                itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                    val decoded = remember(post.id, post.imageBase64) {
                        decodeBase64Bitmap(post.imageBase64)?.asImageBitmap()
                    }
                    val avatarBase64 = post.authorAvatarBase64?.takeIf { it.isNotBlank() }
                        ?: friendAvatars[post.authorId]?.first
                    val avatarUrl = post.authorPhotoUrl?.takeIf { it.isNotBlank() }
                        ?: friendAvatars[post.authorId]?.second
                    val reactionTotal = (post.reactions.keys + post.likedBy).distinct().size
                    val topEmoji = post.reactions.values.firstOrNull() ?: "♥"

                    Surface(
                        onClick = { onSelectPost(index) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .testTag("grid_post_$index")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (decoded != null) {
                                Image(
                                    bitmap = decoded,
                                    contentDescription = post.caption.ifBlank { post.authorName },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        SmallAvatarBadge(
                                            displayName = post.authorName,
                                            avatarBase64 = avatarBase64,
                                            photoUrl = avatarUrl,
                                            size = 18.dp,
                                            borderColor = Color.White.copy(alpha = 0.7f),
                                            borderWidth = 0.8.dp
                                        )
                                        Text(
                                            text = post.authorName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (reactionTotal > 0) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$topEmoji$reactionTotal",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary
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

@Composable
private fun FeedTopHeaderBar(
    badgeLabel: String,
    pendingBadgeCount: Int,
    isGridMode: Boolean?,
    onToggleGridMode: (() -> Unit)?,
    onGoToBooth: () -> Unit,
    onOpenFriendsManager: (() -> Unit)?,
    onOpenMessages: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestSignOut: () -> Unit
) {
    val accentInk = studioAccentInk()
    Row(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onGoToBooth,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("circle_back_to_booth_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.swipe_down_booth_hint),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = accentInk,
                        maxLines = 1
                    )
                }
            }

            if (onToggleGridMode != null && isGridMode != null) {
                Surface(
                    onClick = onToggleGridMode,
                    shape = CircleShape,
                    color = if (isGridMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("toggle_feed_grid_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridMode) Icons.Rounded.CropPortrait else Icons.Rounded.GridView,
                            contentDescription = stringResource(
                                if (isGridMode) R.string.circle_single_view else R.string.circle_grid_view
                            ),
                            tint = if (isGridMode) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                accentInk
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isGridMode) {
                                stringResource(R.string.circle_single_view)
                            } else {
                                "$badgeLabel • Lưới"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isGridMode) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1
                        )
                    }
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onOpenFriendsManager != null) {
                IconButton(
                    onClick = onOpenFriendsManager,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_friends_manager_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (pendingBadgeCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(pendingBadgeCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = stringResource(R.string.friends_manage_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenMessages,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("circle_messages_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = stringResource(R.string.dm_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("circle_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onToggleGridMode == null) {
                IconButton(
                    onClick = onRequestSignOut,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = stringResource(R.string.sign_out),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FriendsManageDialog(
    myFriendCode: String,
    friendRequests: List<FriendRequest>,
    invites: List<CircleInvite>,
    friends: List<DirectFriendItem>,
    isBusy: Boolean,
    onSendFriendRequest: (String, () -> Unit) -> Unit,
    onRespondToFriendRequest: (FriendRequest, Boolean) -> Unit,
    onRespondToInvite: (CircleInvite, Boolean) -> Unit,
    onOpenDirectChat: (uid: String, name: String) -> Unit,
    onRemoveFriend: (uid: String, name: String) -> Unit,
    onPinWidget: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var friendCodeInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.friends_manage_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Kết bạn để cùng chia sẻ mọi khoảnh khắc ảnh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (myFriendCode.isNotBlank()) {
                        item(key = "dialog_my_code") {
                            MyFriendCodeCard(
                                friendCode = myFriendCode,
                                onCopyCode = {
                                    clipboard.setText(AnnotatedString(myFriendCode))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.circle_copied_toast, myFriendCode),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onShareCode = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Kết bạn với mình trên Friends bằng mã bạn bè: $myFriendCode"
                                        )
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Chia sẻ mã bạn bè")
                                    )
                                }
                            )
                        }
                    }

                    item(key = "dialog_add_friend") {
                        AddFriendByCodeCard(
                            friendCodeInput = friendCodeInput,
                            onCodeChange = { friendCodeInput = it },
                            isBusy = isBusy,
                            onSendRequest = {
                                onSendFriendRequest(friendCodeInput) {
                                    friendCodeInput = ""
                                }
                            }
                        )
                    }

                    if (friendRequests.isNotEmpty()) {
                        item(key = "dialog_friend_requests") {
                            PendingFriendRequestsCard(
                                requests = friendRequests,
                                isBusy = isBusy,
                                onAccept = { req -> onRespondToFriendRequest(req, true) },
                                onDecline = { req -> onRespondToFriendRequest(req, false) }
                            )
                        }
                    }

                    if (invites.isNotEmpty()) {
                        item(key = "dialog_legacy_invites") {
                            PendingInvitesCard(
                                invites = invites,
                                isBusy = isBusy,
                                onAccept = { inv -> onRespondToInvite(inv, true) },
                                onDecline = { inv -> onRespondToInvite(inv, false) }
                            )
                        }
                    }

                    item(key = "dialog_friends_list") {
                        MutualFriendsListCard(
                            friends = friends,
                            onChatClick = { uid, name ->
                                onDismiss()
                                onOpenDirectChat(uid, name)
                            },
                            onRemoveClick = onRemoveFriend
                        )
                    }

                    item(key = "dialog_widget_btn") {
                        OutlinedButton(
                            onClick = onPinWidget,
                            shape = CircleShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddToHomeScreen,
                                contentDescription = null,
                                tint = studioAccentInk(),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings_add_widget_btn),
                                color = studioAccentInk(),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyFriendCodeCard(
    friendCode: String,
    onCopyCode: () -> Unit,
    onShareCode: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.my_friend_code_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = friendCode,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = studioAccentInk()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCopyCode) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.circle_copy_code),
                        tint = studioAccentInk()
                    )
                }
                IconButton(onClick = onShareCode) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Chia sẻ mã bạn bè",
                        tint = studioAccentInk()
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFriendByCodeCard(
    friendCodeInput: String,
    onCodeChange: (String) -> Unit,
    isBusy: Boolean,
    onSendRequest: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.circle_add_friend_btn),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = friendCodeInput,
                    onValueChange = { raw ->
                        if (raw.length <= 16) {
                            onCodeChange(raw.uppercase().filter { it.isLetterOrDigit() })
                        }
                    },
                    placeholder = { Text(stringResource(R.string.friend_code_input_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("friend_code_input")
                )
                Button(
                    onClick = onSendRequest,
                    enabled = friendCodeInput.trim().length >= 4 && !isBusy,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("send_friend_request_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kết bạn", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PendingFriendRequestsCard(
    requests: List<FriendRequest>,
    isBusy: Boolean,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.friend_requests_title, requests.size),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = studioAccentInk()
            )
            requests.forEach { request ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = request.fromName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = studioAccentInk()
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = request.fromName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onDecline(request) },
                                enabled = !isBusy,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.decline_invite), style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onAccept(request) },
                                enabled = !isBusy,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.accept_invite), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingInvitesCard(
    invites: List<CircleInvite>,
    isBusy: Boolean,
    onAccept: (CircleInvite) -> Unit,
    onDecline: (CircleInvite) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.pending_invites_title, invites.size),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            invites.forEach { invite ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = invite.circleName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Lời mời từ ${invite.fromName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onDecline(invite) },
                                enabled = !isBusy,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.decline_invite), style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onAccept(invite) },
                                enabled = !isBusy,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(stringResource(R.string.accept_invite), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MutualFriendsListCard(
    friends: List<DirectFriendItem>,
    onChatClick: (uid: String, name: String) -> Unit,
    onRemoveClick: (uid: String, name: String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.friends_list_title, friends.size),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (friends.isEmpty()) {
                Text(
                    text = "Chưa có bạn bè nào. Hãy nhập mã bạn bè phía trên để kết nối ngay!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                friends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallAvatarBadge(
                                displayName = friend.displayName,
                                avatarBase64 = friend.avatarBase64,
                                photoUrl = friend.photoUrl,
                                size = 34.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = friend.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onChatClick(friend.uid, friend.displayName) }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                    contentDescription = "Nhắn tin ${friend.displayName}",
                                    tint = studioAccentInk()
                                )
                            }
                            if (friend.isDirectFriend) {
                                IconButton(
                                    onClick = { onRemoveClick(friend.uid, friend.displayName) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PersonRemove,
                                        contentDescription = stringResource(R.string.unfriend_btn),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun PostLightboxDialog(
    post: CirclePost,
    onDismiss: () -> Unit,
    onReplyClick: () -> Unit,
    onSaveBitmap: (Bitmap) -> Unit
) {
    val bitmap = remember(post.id, post.imageBase64) { decodeBase64Bitmap(post.imageBase64) }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        SmallAvatarBadge(
                            displayName = post.authorName,
                            avatarBase64 = post.authorAvatarBase64,
                            photoUrl = post.authorPhotoUrl,
                            size = 28.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (post.caption.isNotBlank()) {
                                Text(
                                    text = post.caption,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = post.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplyClick,
                        shape = CircleShape,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.reply_to_photo))
                    }

                    if (bitmap != null) {
                        Button(
                            onClick = { onSaveBitmap(bitmap) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.circle_save_friend_photo))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostRepliesDialog(
    post: CirclePost,
    currentUserId: String,
    friendAvatars: Map<String, Pair<String?, String?>>,
    commentsState: CircleFeedUiState<List<PostComment>>,
    isSending: Boolean,
    onSendReply: (
        mode: ReplyDestinationMode,
        text: String,
        openChatAfterSend: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteComment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val canDirectMessage = post.authorId.isNotBlank() && post.authorId != currentUserId
    var selectedMode by remember(post.id) {
        mutableStateOf(
            if (canDirectMessage) ReplyDestinationMode.DIRECT_MESSAGE else ReplyDestinationMode.PUBLIC_COMMENT
        )
    }

    val thumbnail = remember(post.id, post.imageBase64) {
        decodeBase64Bitmap(post.imageBase64)?.asImageBitmap()
    }
    var draft by remember { mutableStateOf("") }
    val quickReplies = remember {
        listOf(
            "Xinh quá đi! ✨",
            "Tấm này đỉnh thật 🔥",
            "Hôm nào chụp tiếp nha ♡",
            "Dễ thương xỉu 💕"
        )
    }
    val accentInk = studioAccentInk()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = post.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reply_sheet_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SmallAvatarBadge(
                                displayName = post.authorName,
                                avatarBase64 = post.authorAvatarBase64 ?: friendAvatars[post.authorId]?.first,
                                photoUrl = post.authorPhotoUrl ?: friendAvatars[post.authorId]?.second,
                                size = 18.dp
                            )
                            Text(
                                text = post.authorName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                // Mode Switcher: Tin nhắn riêng vs. Bình luận công khai
                if (canDirectMessage) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dmActive = selectedMode == ReplyDestinationMode.DIRECT_MESSAGE
                        Surface(
                            onClick = { selectedMode = ReplyDestinationMode.DIRECT_MESSAGE },
                            shape = CircleShape,
                            color = if (dmActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("reply_mode_dm_tab")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (dmActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.reply_mode_dm),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (dmActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        val publicActive = selectedMode == ReplyDestinationMode.PUBLIC_COMMENT
                        Surface(
                            onClick = { selectedMode = ReplyDestinationMode.PUBLIC_COMMENT },
                            shape = CircleShape,
                            color = if (publicActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("reply_mode_public_tab")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (publicActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.reply_mode_public),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (publicActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                if (selectedMode == ReplyDestinationMode.DIRECT_MESSAGE && canDirectMessage) {
                    // Direct Message Quoted-Photo Preview Body
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (thumbnail != null) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.Black.copy(alpha = 0.16f))
                            ) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = post.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.56f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        SmallAvatarBadge(
                                            displayName = post.authorName,
                                            avatarBase64 = post.authorAvatarBase64 ?: friendAvatars[post.authorId]?.first,
                                            photoUrl = post.authorPhotoUrl ?: friendAvatars[post.authorId]?.second,
                                            size = 20.dp,
                                            borderColor = Color.White.copy(alpha = 0.7f),
                                            borderWidth = 1.dp
                                        )
                                        Text(
                                            text = post.authorName,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (post.caption.isNotBlank()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.56f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = post.caption,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = stringResource(R.string.reply_dm_banner, post.authorName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accentInk,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (commentsState) {
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
                                        text = commentsState.message,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            is CircleFeedUiState.Success -> {
                                val comments = commentsState.data
                                if (comments.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Forum,
                                                contentDescription = null,
                                                tint = accentInk,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.no_replies_yet),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(comments, key = { it.id }) { comment ->
                                            val isMine = comment.authorId == currentUserId
                                            val canDelete = isMine || post.authorId == currentUserId
                                            val cAvatar = comment.authorAvatarBase64
                                                ?: friendAvatars[comment.authorId]?.first
                                            val cUrl = comment.authorPhotoUrl
                                                ?: friendAvatars[comment.authorId]?.second
                                            val timeLabel = remember(comment.createdAt) {
                                                val d = comment.createdAt?.toDate()
                                                if (d != null) {
                                                    SimpleDateFormat("HH:mm • dd/MM", Locale("vi", "VN")).format(d)
                                                } else {
                                                    "Vừa xong"
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                if (!isMine) {
                                                    SmallAvatarBadge(
                                                        displayName = comment.authorName,
                                                        avatarBase64 = cAvatar,
                                                        photoUrl = cUrl,
                                                        size = 24.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(
                                                        topStart = 18.dp,
                                                        topEnd = 18.dp,
                                                        bottomStart = if (isMine) 18.dp else 4.dp,
                                                        bottomEnd = if (isMine) 4.dp else 18.dp
                                                    ),
                                                    color = if (isMine) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    },
                                                    modifier = Modifier.widthIn(max = 290.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(
                                                            horizontal = 12.dp,
                                                            vertical = 8.dp
                                                        )
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isMine) "Bạn" else comment.authorName,
                                                                style = MaterialTheme.typography.labelMedium.copy(
                                                                    fontWeight = FontWeight.Bold
                                                                ),
                                                                color = if (isMine) {
                                                                    accentInk
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurface
                                                                }
                                                            )
                                                            Text(
                                                                text = timeLabel,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            if (canDelete) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                                    contentDescription = "Xóa bình luận",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier
                                                                        .size(16.dp)
                                                                        .clickable { onDeleteComment(comment.id) }
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Text(
                                                            text = comment.text,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
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

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    quickReplies.forEach { suggestion ->
                        Surface(
                            onClick = { draft = suggestion },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 300) draft = it },
                        placeholder = {
                            Text(
                                text = if (selectedMode == ReplyDestinationMode.DIRECT_MESSAGE && canDirectMessage) {
                                    stringResource(R.string.reply_dm_hint)
                                } else {
                                    stringResource(R.string.reply_public_hint)
                                }
                            )
                        },
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
                                    onSendReply(selectedMode, draft, false) { draft = "" }
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reply_input")
                    )

                    if (selectedMode == ReplyDestinationMode.DIRECT_MESSAGE && canDirectMessage) {
                        OutlinedButton(
                            onClick = {
                                if (draft.isNotBlank() && !isSending) {
                                    onSendReply(selectedMode, draft, true) { draft = "" }
                                }
                            },
                            enabled = draft.isNotBlank() && !isSending,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("send_reply_open_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Chat,
                                contentDescription = stringResource(R.string.reply_dm_send_and_open),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (draft.isNotBlank() && !isSending) {
                                onSendReply(selectedMode, draft, false) { draft = "" }
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
                            .testTag("send_reply_button")
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostReactionsDialog(
    reactions: List<PostReactionItem>,
    myReaction: String?,
    onReactEmoji: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilterEmoji by remember { mutableStateOf<String?>(null) }
    val uniqueEmojis = remember(reactions) {
        reactions.map { it.emoji }.distinct()
    }
    val filteredList = remember(reactions, selectedFilterEmoji) {
        if (selectedFilterEmoji == null) reactions else reactions.filter { it.emoji == selectedFilterEmoji }
    }
    val quickEmojis = remember { listOf("🔥", "😍", "💛", "❤️", "😂", "👏", "✨") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = 540.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.reactions_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.reactions_count_label, reactions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                if (uniqueEmojis.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = { selectedFilterEmoji = null },
                            shape = CircleShape,
                            color = if (selectedFilterEmoji == null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = "Tất cả (${reactions.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedFilterEmoji == null) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        uniqueEmojis.forEach { emoji ->
                            val cnt = reactions.count { it.emoji == emoji }
                            val active = selectedFilterEmoji == emoji
                            Surface(
                                onClick = { selectedFilterEmoji = if (active) null else emoji },
                                shape = CircleShape,
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = "$emoji $cnt",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reactions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredList, key = { it.userId }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    SmallAvatarBadge(
                                        displayName = item.displayName,
                                        avatarBase64 = item.avatarBase64,
                                        photoUrl = item.photoUrl,
                                        size = 30.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(text = item.emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickEmojis.forEach { emoji ->
                        val active = myReaction == emoji
                        Surface(
                            onClick = { onReactEmoji(if (active) null else emoji) },
                            shape = CircleShape,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
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
fun EmojiPickerDialog(
    currentReaction: String?,
    onSelectEmoji: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = remember {
        listOf(
            "🔥", "😍", "💛", "❤️", "😂", "🥹",
            "👏", "✨", "🥰", "😮", "🎉", "💯",
            "😎", "🫶", "🥳", "👍", "🌸", "🤩"
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reactions_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    palette.forEach { emoji ->
                        val selected = currentReaction == emoji
                        Surface(
                            onClick = { onSelectEmoji(if (selected) null else emoji) },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun decodeBase64Bitmap(encoded: String): Bitmap? {
    if (encoded.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
