package com.example

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.configureAppCheck
import com.example.data.AppIconManager
import com.example.data.AppIconVariant
import com.example.data.AppThemeMode
import com.example.data.FriendsCircleRepository
import com.example.data.ThemePreferencesRepository
import com.example.imaging.GallerySaver
import com.example.model.BoothScreen
import com.example.model.CircleFeedUiState
import com.example.model.CirclePost
import com.example.model.ReplyDestinationMode
import com.example.notifications.FriendsNotificationHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutWithCleanup
import com.example.ui.components.PhotoCropEditorDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.screens.CameraBoothScreen
import com.example.ui.screens.DirectMessagesScreen
import com.example.ui.screens.FriendFeedSinglePhotoPage
import com.example.ui.screens.FriendsFeedFallbackPage
import com.example.ui.screens.FriendsFeedGridPage
import com.example.ui.screens.FriendsManageDialog
import com.example.ui.screens.PostLightboxDialog
import com.example.ui.screens.PostReactionsDialog
import com.example.ui.screens.PostRepliesDialog
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.StudioEditorScreen
import com.example.ui.theme.MyPhotoboothTheme
import com.example.viewmodel.FriendsCircleViewModel
import com.example.viewmodel.PhotoboothViewModel
import com.example.widget.FriendsWidgetProvider
import com.example.widget.FriendsWidgetStorage
import com.example.widget.PinWidgetResult
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotificationDeepLink(
    val target: String,
    val chatId: String? = null,
    val postId: String? = null
)

class MainActivity : ComponentActivity() {

    private val photoboothViewModel: PhotoboothViewModel by viewModels()
    private var openFeedFromWidget by mutableStateOf(false)
    private var pendingAvatarUri by mutableStateOf<Uri?>(null)
    private var pendingNotificationLink by mutableStateOf<NotificationDeepLink?>(null)

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePickerActivityResult(result, isDocumentFallback = false)
    }

    private val documentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handlePickerActivityResult(result, isDocumentFallback = true)
    }

    private val avatarPhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleAvatarPickerResult(result, isDocumentFallback = false)
    }

    private val avatarDocumentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleAvatarPickerResult(result, isDocumentFallback = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        FriendsNotificationHelper.ensureChannels(applicationContext)
        consumeNavigationExtras(intent)

        val themeRepo = ThemePreferencesRepository(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            val themeMode by themeRepo.themeMode.collectAsStateWithLifecycle(
                initialValue = AppThemeMode.SYSTEM
            )
            val countdownSeconds by themeRepo.countdownSeconds.collectAsStateWithLifecycle(
                initialValue = PhotoboothViewModel.DEFAULT_COUNTDOWN_SECONDS
            )
            val appIconVariant by themeRepo.appIconVariant.collectAsStateWithLifecycle(
                initialValue = AppIconVariant.YELLOW
            )
            val notificationsEnabled by themeRepo.notificationsEnabled.collectAsStateWithLifecycle(
                initialValue = true
            )

            LaunchedEffect(countdownSeconds) {
                photoboothViewModel.setCountdownDuration(countdownSeconds)
            }

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            MyPhotoboothTheme(darkTheme = useDarkTheme) {
                AppContent(
                    photoboothViewModel = photoboothViewModel,
                    themeMode = themeMode,
                    countdownSeconds = countdownSeconds,
                    appIconVariant = appIconVariant,
                    notificationsEnabled = notificationsEnabled,
                    notificationsEnabledFlow = themeRepo.notificationsEnabled,
                    openFeedFromWidget = openFeedFromWidget,
                    pendingAvatarUri = pendingAvatarUri,
                    pendingNotificationLink = pendingNotificationLink,
                    onConsumeOpenFeedFromWidget = { openFeedFromWidget = false },
                    onConsumePendingAvatarUri = { pendingAvatarUri = null },
                    onConsumeNotificationLink = { pendingNotificationLink = null },
                    onSelectThemeMode = { mode ->
                        scope.launch { themeRepo.setThemeMode(mode) }
                    },
                    onSelectCountdownSeconds = { seconds ->
                        photoboothViewModel.setCountdownDuration(seconds)
                        scope.launch { themeRepo.setCountdownSeconds(seconds) }
                    },
                    onSelectAppIcon = { variant ->
                        scope.launch {
                            themeRepo.setAppIconVariant(variant)
                            AppIconManager.applyVariant(applicationContext, variant)
                        }
                    },
                    onToggleNotifications = { enabled ->
                        scope.launch { themeRepo.setNotificationsEnabled(enabled) }
                    },
                    onLaunchPhotoPicker = { singleMode ->
                        launchDevicePhotoPicker(singleMode)
                    },
                    onLaunchFilePicker = { singleMode ->
                        launchDocumentPhotoPicker(singleMode)
                    },
                    onLaunchAvatarPicker = { launchAvatarPhotoPicker() },
                    onLaunchAvatarFilePicker = { launchAvatarDocumentPicker() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNavigationExtras(intent)
    }

    private fun consumeNavigationExtras(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(FriendsWidgetProvider.EXTRA_OPEN_FEED, false)) {
            openFeedFromWidget = true
        }
        val notifTarget = intent.getStringExtra(FriendsNotificationHelper.EXTRA_OPEN_TARGET)
        if (!notifTarget.isNullOrBlank()) {
            pendingNotificationLink = NotificationDeepLink(
                target = notifTarget,
                chatId = intent.getStringExtra(FriendsNotificationHelper.EXTRA_TARGET_CHAT_ID),
                postId = intent.getStringExtra(FriendsNotificationHelper.EXTRA_TARGET_POST_ID)
            )
        }
    }

    private fun launchDevicePhotoPicker(singleMode: Boolean) {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val intent = if (singleMode) {
            ActivityResultContracts.PickVisualMedia().createIntent(this, request)
        } else {
            ActivityResultContracts.PickMultipleVisualMedia(4).createIntent(this, request)
        }
        try {
            photoPickerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            launchDocumentPhotoPicker(singleMode)
        } catch (_: Exception) {
            launchDocumentPhotoPicker(singleMode)
        }
    }

    private fun launchDocumentPhotoPicker(singleMode: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, !singleMode)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            documentPickerLauncher.launch(intent)
        } catch (_: Exception) {
            photoboothViewModel.reportCaptureError("Thiết bị không có ứng dụng chọn ảnh khả dụng.")
        }
    }

    private fun launchAvatarPhotoPicker() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val intent = ActivityResultContracts.PickVisualMedia().createIntent(this, request)
        try {
            avatarPhotoPickerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            launchAvatarDocumentPicker()
        } catch (_: Exception) {
            launchAvatarDocumentPicker()
        }
    }

    private fun launchAvatarDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            avatarDocumentPickerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Thiết bị không có ứng dụng chọn ảnh khả dụng.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleAvatarPickerResult(result: ActivityResult, isDocumentFallback: Boolean) {
        if (result.resultCode != Activity.RESULT_OK) return
        val uri = extractUrisFromIntent(result.data).firstOrNull()
        if (uri != null) {
            if (isDocumentFallback) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            pendingAvatarUri = uri
        } else {
            Toast.makeText(
                this,
                "Không đọc được ảnh đại diện đã chọn.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handlePickerActivityResult(result: ActivityResult, isDocumentFallback: Boolean) {
        if (result.resultCode != Activity.RESULT_OK) {
            return
        }
        val uris = extractUrisFromIntent(result.data)
        if (uris.isNotEmpty()) {
            if (isDocumentFallback) {
                uris.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            }
            photoboothViewModel.loadPhotosFromUris(applicationContext, uris)
        } else {
            photoboothViewModel.reportCaptureError(
                "Không nhận được tệp ảnh từ bộ chọn. Hãy thử nút \"Chọn từ tệp\"."
            )
        }
    }

    private fun extractUrisFromIntent(data: Intent?): List<Uri> {
        if (data == null) return emptyList()
        val result = LinkedHashSet<Uri>()

        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { result.add(it) }
            }
        }
        data.data?.let { result.add(it) }

        if (result.isEmpty()) {
            val streamList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            streamList?.forEach { result.add(it) }
        }

        if (result.isEmpty()) {
            val singleStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            singleStream?.let { result.add(it) }
        }

        return result.toList()
    }
}

@Composable
private fun AppContent(
    photoboothViewModel: PhotoboothViewModel,
    themeMode: AppThemeMode,
    countdownSeconds: Int,
    appIconVariant: AppIconVariant,
    notificationsEnabled: Boolean,
    notificationsEnabledFlow: Flow<Boolean>,
    openFeedFromWidget: Boolean,
    pendingAvatarUri: Uri?,
    pendingNotificationLink: NotificationDeepLink?,
    onConsumeOpenFeedFromWidget: () -> Unit,
    onConsumePendingAvatarUri: () -> Unit,
    onConsumeNotificationLink: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectCountdownSeconds: (Int) -> Unit,
    onSelectAppIcon: (AppIconVariant) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLaunchPhotoPicker: (Boolean) -> Unit,
    onLaunchFilePicker: (Boolean) -> Unit,
    onLaunchAvatarPicker: () -> Unit,
    onLaunchAvatarFilePicker: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedPhotoboothApp(
                currentUserId = user.uid,
                fallbackDisplayName = user.displayName.orEmpty(),
                fallbackEmail = user.email,
                fallbackPhotoUrl = user.photoUrl?.toString(),
                photoboothViewModel = photoboothViewModel,
                themeMode = themeMode,
                countdownSeconds = countdownSeconds,
                appIconVariant = appIconVariant,
                notificationsEnabled = notificationsEnabled,
                notificationsEnabledFlow = notificationsEnabledFlow,
                openFeedFromWidget = openFeedFromWidget,
                pendingAvatarUri = pendingAvatarUri,
                pendingNotificationLink = pendingNotificationLink,
                onConsumeOpenFeedFromWidget = onConsumeOpenFeedFromWidget,
                onConsumePendingAvatarUri = onConsumePendingAvatarUri,
                onConsumeNotificationLink = onConsumeNotificationLink,
                onSelectThemeMode = onSelectThemeMode,
                onSelectCountdownSeconds = onSelectCountdownSeconds,
                onSelectAppIcon = onSelectAppIcon,
                onToggleNotifications = onToggleNotifications,
                onLaunchPhotoPicker = onLaunchPhotoPicker,
                onLaunchFilePicker = onLaunchFilePicker,
                onLaunchAvatarPicker = onLaunchAvatarPicker,
                onLaunchAvatarFilePicker = onLaunchAvatarFilePicker,
                onSignOut = {
                    photoboothViewModel.cancelShootingSequence()
                    photoboothViewModel.returnToCameraForRetake()
                    signOutWithCleanup(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedPhotoboothApp(
    currentUserId: String,
    fallbackDisplayName: String,
    fallbackEmail: String?,
    fallbackPhotoUrl: String?,
    photoboothViewModel: PhotoboothViewModel,
    themeMode: AppThemeMode,
    countdownSeconds: Int,
    appIconVariant: AppIconVariant,
    notificationsEnabled: Boolean,
    notificationsEnabledFlow: Flow<Boolean>,
    openFeedFromWidget: Boolean,
    pendingAvatarUri: Uri?,
    pendingNotificationLink: NotificationDeepLink?,
    onConsumeOpenFeedFromWidget: () -> Unit,
    onConsumePendingAvatarUri: () -> Unit,
    onConsumeNotificationLink: () -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectCountdownSeconds: (Int) -> Unit,
    onSelectAppIcon: (AppIconVariant) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onLaunchPhotoPicker: (Boolean) -> Unit,
    onLaunchFilePicker: (Boolean) -> Unit,
    onLaunchAvatarPicker: () -> Unit,
    onLaunchAvatarFilePicker: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationPermission by remember {
        mutableStateOf(FriendsNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || FriendsNotificationHelper.hasNotificationPermission(context)
        if (hasNotificationPermission) {
            FriendsNotificationHelper.ensureChannels(context.applicationContext)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.notifications_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !FriendsNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
            FriendsNotificationHelper.ensureChannels(context.applicationContext)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = FriendsNotificationHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var askedNotifOnce by rememberSaveable(currentUserId) { mutableStateOf(false) }
    LaunchedEffect(currentUserId, notificationsEnabled) {
        if (!askedNotifOnce && notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !FriendsNotificationHelper.hasNotificationPermission(context)
        ) {
            askedNotifOnce = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val friendsCircleViewModel: FriendsCircleViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FriendsCircleViewModel(
                    repository = FriendsCircleRepository(db),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext,
                    notificationsEnabledFlow = notificationsEnabledFlow
                )
            }
        }
    )

    LaunchedEffect(pendingAvatarUri) {
        val uri = pendingAvatarUri ?: return@LaunchedEffect
        onConsumePendingAvatarUri()
        friendsCircleViewModel.updateAvatarFromUri(context.applicationContext, uri)
    }

    val boothUiState by photoboothViewModel.uiState.collectAsStateWithLifecycle()
    val userProfile by friendsCircleViewModel.userProfileState.collectAsStateWithLifecycle()
    val invites by friendsCircleViewModel.invitesState.collectAsStateWithLifecycle()
    val friendRequests by friendsCircleViewModel.friendRequestsState.collectAsStateWithLifecycle()
    val postsState by friendsCircleViewModel.postsState.collectAsStateWithLifecycle()
    val commentsState by friendsCircleViewModel.commentsState.collectAsStateWithLifecycle()
    val directChatsState by friendsCircleViewModel.directChatsState.collectAsStateWithLifecycle()
    val directMessagesState by friendsCircleViewModel.directMessagesState.collectAsStateWithLifecycle()
    val directFriends by friendsCircleViewModel.directFriendsState.collectAsStateWithLifecycle()
    val friendAvatars by friendsCircleViewModel.friendAvatarsByUid.collectAsStateWithLifecycle()
    val circleActionState by friendsCircleViewModel.actionState.collectAsStateWithLifecycle()

    LaunchedEffect(circleActionState.actionMessage, circleActionState.actionError) {
        val msg = circleActionState.actionMessage ?: circleActionState.actionError
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            friendsCircleViewModel.clearActionMessages()
        }
    }

    // Sync latest feed photo to Home Screen Widget automatically
    val posts = (postsState as? CircleFeedUiState.Success)?.data.orEmpty()
    val latestPostId = posts.firstOrNull()?.id
    LaunchedEffect(latestPostId, postsState) {
        if (postsState is CircleFeedUiState.Success) {
            val latest = posts.firstOrNull()
            withContext(Dispatchers.IO) {
                FriendsWidgetStorage.saveLatestPost(context.applicationContext, latest)
                FriendsWidgetProvider.updateAllWidgets(context.applicationContext)
            }
        }
    }

    var showSettingsDialog by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var showFriendsManager by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var isFeedGridMode by rememberSaveable(currentUserId) { mutableStateOf(false) }
    var pendingGridTargetIndex by remember { mutableStateOf<Int?>(null) }
    var postToDelete by remember { mutableStateOf<CirclePost?>(null) }
    var inspectedPost by remember { mutableStateOf<CirclePost?>(null) }
    var replyingPost by remember { mutableStateOf<CirclePost?>(null) }
    var reactionViewerPostId by remember { mutableStateOf<String?>(null) }

    val handlePinWidget = {
        val res = FriendsWidgetProvider.requestPinWidget(context)
        val toastMsg = if (res == PinWidgetResult.REQUESTED) {
            context.getString(R.string.widget_pin_requested)
        } else {
            context.getString(R.string.widget_pin_unsupported)
        }
        Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
    }

    // HorizontalPager (3 pages):
    // Page 0 (swipe right) = Profile & Settings
    // Page 1 (center / initial) = Booth / Friends Feed VerticalPager
    // Page 2 (swipe left) = Direct Messages
    val horizontalPagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    // VerticalPager: Page 0 = Phòng chụp, Page 1..N = 1 friend photo per swipe up (or Grid / Fallback hub)
    val verticalPageCount = if (posts.isEmpty() || isFeedGridMode) 2 else 1 + posts.size
    val verticalPagerState = rememberPagerState(initialPage = 0, pageCount = { verticalPageCount })

    LaunchedEffect(verticalPageCount) {
        if (verticalPagerState.currentPage >= verticalPageCount) {
            verticalPagerState.scrollToPage((verticalPageCount - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(isFeedGridMode, verticalPageCount, pendingGridTargetIndex) {
        val targetIdx = pendingGridTargetIndex ?: return@LaunchedEffect
        if (!isFeedGridMode && verticalPageCount > 1) {
            val targetPage = (1 + targetIdx).coerceIn(1, verticalPageCount - 1)
            pendingGridTargetIndex = null
            verticalPagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(openFeedFromWidget) {
        if (openFeedFromWidget) {
            isFeedGridMode = false
            horizontalPagerState.scrollToPage(1)
            verticalPagerState.animateScrollToPage(1.coerceAtMost(verticalPageCount - 1))
            onConsumeOpenFeedFromWidget()
        }
    }

    LaunchedEffect(pendingNotificationLink, posts.size) {
        val link = pendingNotificationLink ?: return@LaunchedEffect
        when (link.target) {
            FriendsNotificationHelper.TARGET_FRIENDS_MANAGER -> {
                showFriendsManager = true
                onConsumeNotificationLink()
            }
            FriendsNotificationHelper.TARGET_DIRECT_CHAT -> {
                val chatId = link.chatId
                if (!chatId.isNullOrBlank()) {
                    friendsCircleViewModel.openDirectChatById(chatId)
                }
                horizontalPagerState.animateScrollToPage(2)
                onConsumeNotificationLink()
            }
            FriendsNotificationHelper.TARGET_FEED_POST -> {
                val idx = posts.indexOfFirst { it.id == link.postId }.let { if (it >= 0) it else 0 }
                isFeedGridMode = false
                horizontalPagerState.scrollToPage(1)
                if (verticalPageCount > 1) {
                    verticalPagerState.animateScrollToPage((1 + idx).coerceIn(1, verticalPageCount - 1))
                }
                onConsumeNotificationLink()
            }
            else -> onConsumeNotificationLink()
        }
    }

    val hasModalOpen = showSettingsDialog ||
        showFriendsManager ||
        showSignOutDialog ||
        postToDelete != null ||
        inspectedPost != null ||
        replyingPost != null ||
        reactionViewerPostId != null ||
        boothUiState.pendingCropSlotIndex != null

    val canSwipePager = boothUiState.currentScreen == BoothScreen.Capture &&
        !boothUiState.isShootingSequence &&
        !boothUiState.isLoadingPhotos &&
        !hasModalOpen

    val horizontalScrollEnabled = canSwipePager &&
        circleActionState.activeDirectChat == null &&
        (horizontalPagerState.currentPage != 1 || verticalPagerState.currentPage == 0)

    val verticalScrollEnabled = canSwipePager &&
        horizontalPagerState.currentPage == 1 &&
        !isFeedGridMode

    if (horizontalPagerState.currentPage == 0 && !hasModalOpen) {
        BackHandler {
            scope.launch { horizontalPagerState.animateScrollToPage(1) }
        }
    } else if (horizontalPagerState.currentPage == 2 && circleActionState.activeDirectChat == null && !hasModalOpen) {
        BackHandler {
            scope.launch { horizontalPagerState.animateScrollToPage(1) }
        }
    } else if (
        horizontalPagerState.currentPage == 1 &&
        verticalPagerState.currentPage > 0 &&
        boothUiState.currentScreen == BoothScreen.Capture &&
        !hasModalOpen
    ) {
        BackHandler {
            if (isFeedGridMode) {
                isFeedGridMode = false
            } else {
                scope.launch { verticalPagerState.animateScrollToPage(0) }
            }
        }
    }

    val pendingBadgeCount = friendRequests.size + invites.size
    val isBoothActive = horizontalPagerState.currentPage == 1 &&
        verticalPagerState.currentPage == 0 &&
        boothUiState.currentScreen == BoothScreen.Capture

    HorizontalPager(
        state = horizontalPagerState,
        userScrollEnabled = horizontalScrollEnabled,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize()
    ) { horizontalPage ->
        when (horizontalPage) {
            0 -> ProfileAndSettingsScreen(
                userProfile = userProfile,
                fallbackDisplayName = fallbackDisplayName,
                fallbackEmail = fallbackEmail,
                fallbackPhotoUrl = fallbackPhotoUrl,
                myFriendCode = circleActionState.myFriendCode,
                friendsCount = directFriends.size,
                pendingBadgeCount = pendingBadgeCount,
                isUpdatingProfile = circleActionState.isUpdatingProfile,
                themeMode = themeMode,
                countdownSeconds = countdownSeconds,
                appIconVariant = appIconVariant,
                notificationsEnabled = notificationsEnabled,
                hasNotificationPermission = hasNotificationPermission,
                onPickAvatarFromGallery = onLaunchAvatarPicker,
                onPickAvatarFromFiles = onLaunchAvatarFilePicker,
                onSaveBio = friendsCircleViewModel::updateUserBio,
                onOpenFriendsManager = { showFriendsManager = true },
                onSelectThemeMode = onSelectThemeMode,
                onSelectCountdownSeconds = onSelectCountdownSeconds,
                onSelectAppIcon = onSelectAppIcon,
                onToggleNotifications = onToggleNotifications,
                onRequestNotificationPermission = requestNotificationPermission,
                onPinWidget = handlePinWidget,
                onGoToBooth = {
                    scope.launch { horizontalPagerState.animateScrollToPage(1) }
                },
                onRequestSignOut = { showSignOutDialog = true },
                modifier = Modifier.fillMaxSize()
            )

            1 -> VerticalPager(
                state = verticalPagerState,
                userScrollEnabled = verticalScrollEnabled,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { verticalPage ->
                when (verticalPage) {
                    0 -> AnimatedContent(
                        targetState = boothUiState.currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "photobooth_screen_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { screen ->
                        when (screen) {
                            BoothScreen.Capture -> CameraBoothScreen(
                                uiState = boothUiState,
                                isBoothActive = isBoothActive,
                                onSelectLayout = photoboothViewModel::selectLayout,
                                onSelectFilter = photoboothViewModel::selectFilter,
                                onToggleLens = photoboothViewModel::toggleCameraLens,
                                onToggleFillLight = photoboothViewModel::toggleFillLight,
                                onStartShooting = photoboothViewModel::startShootingSequence,
                                onCancelShooting = photoboothViewModel::cancelShootingSequence,
                                onPhotoCaptured = photoboothViewModel::onPhotoCaptured,
                                onPhotoCaptureFailed = photoboothViewModel::onPhotoCaptureFailed,
                                onOpenDevicePhotoPicker = onLaunchPhotoPicker,
                                onOpenDocumentFilePicker = onLaunchFilePicker,
                                onDismissCaptureError = photoboothViewModel::dismissCaptureError,
                                onOpenProfile = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(0) }
                                },
                                onOpenSettings = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(0) }
                                },
                                onOpenMessages = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(2) }
                                },
                                onOpenCircle = {
                                    scope.launch { verticalPagerState.animateScrollToPage(1) }
                                },
                                applyBottomSystemPadding = true
                            )

                            BoothScreen.Customize -> StudioEditorScreen(
                                uiState = boothUiState,
                                themeMode = themeMode,
                                publishState = circleActionState.publishState,
                                onSelectThemeMode = onSelectThemeMode,
                                onSelectTab = photoboothViewModel::selectEditorTab,
                                onSelectFrame = photoboothViewModel::selectFrameStyle,
                                onSelectFilter = photoboothViewModel::selectFilter,
                                onUpdateCaption = photoboothViewModel::updateCaption,
                                onToggleShowDate = photoboothViewModel::toggleShowDateStamp,
                                onToggleLayout = photoboothViewModel::toggleLayoutInEditor,
                                onScanSubjectWithAi = {
                                    photoboothViewModel.scanSubjectWithAi(context)
                                },
                                onUpdateCreativeText = photoboothViewModel::updateCreativeText,
                                onSelectOverlayShape = photoboothViewModel::selectOverlayShape,
                                onSelectOverlayStyle = photoboothViewModel::selectOverlayStyle,
                                onSelectOverlayFont = photoboothViewModel::selectOverlayFont,
                                onSelectOverlayColor = photoboothViewModel::selectOverlayColor,
                                onToggleBehindSubject = photoboothViewModel::toggleBehindSubject,
                                onToggleRepeatAlongShape = photoboothViewModel::toggleRepeatAlongShape,
                                onUpdateCreativeTextScale = photoboothViewModel::updateCreativeTextScale,
                                onUpdateCreativeTextLetterSpacing = photoboothViewModel::updateCreativeTextLetterSpacing,
                                onUpdateCreativeTextLineSpacing = photoboothViewModel::updateCreativeTextLineSpacing,
                                onUpdateCreativeTextPositionY = photoboothViewModel::updateCreativeTextPositionY,
                                onUpdateCreativeTextPositionX = photoboothViewModel::updateCreativeTextPositionX,
                                onUpdateCreativeTextBend = photoboothViewModel::updateCreativeTextBend,
                                onUpdateCreativeTextOpacity = photoboothViewModel::updateCreativeTextOpacity,
                                onSelectTargetSlot = photoboothViewModel::selectTargetSlot,
                                onResetCreativeTextTransform = photoboothViewModel::resetCreativeTextTransform,
                                onClearAiError = photoboothViewModel::clearAiError,
                                onEditPhotoClick = photoboothViewModel::openCropEditor,
                                onRetake = {
                                    friendsCircleViewModel.resetPublishState()
                                    photoboothViewModel.returnToCameraForRetake()
                                },
                                onSaveToGallery = {
                                    photoboothViewModel.saveCollageToGallery(context)
                                },
                                onDismissSaveState = photoboothViewModel::dismissSaveState,
                                onPublishToFriends = {
                                    friendsCircleViewModel.publishToSharedFriends(
                                        context = context,
                                        photos = boothUiState.capturedPhotos,
                                        layout = boothUiState.selectedLayout,
                                        frameStyle = boothUiState.selectedFrame,
                                        filter = boothUiState.selectedFilter,
                                        captionText = boothUiState.captionText,
                                        showDateStamp = boothUiState.showDateStamp,
                                        formattedDate = boothUiState.formattedDate,
                                        creativeText = boothUiState.creativeText,
                                        subjectMasks = boothUiState.subjectAi.subjectMasks
                                    )
                                },
                                onViewCircleFeed = {
                                    photoboothViewModel.returnToCameraForRetake()
                                    isFeedGridMode = false
                                    scope.launch { verticalPagerState.animateScrollToPage(1) }
                                },
                                onResetPublishState = friendsCircleViewModel::resetPublishState
                            )
                        }
                    }

                    else -> {
                        if (posts.isEmpty()) {
                            FriendsFeedFallbackPage(
                                postsState = postsState,
                                myFriendCode = circleActionState.myFriendCode,
                                friendRequests = friendRequests,
                                invites = invites,
                                friends = directFriends,
                                isBusy = circleActionState.isBusy,
                                onGoToBooth = {
                                    scope.launch { verticalPagerState.animateScrollToPage(0) }
                                },
                                onOpenMessages = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(2) }
                                },
                                onOpenSettings = { showSettingsDialog = true },
                                onRequestSignOut = { showSignOutDialog = true },
                                onSendFriendRequest = friendsCircleViewModel::sendFriendRequest,
                                onRespondToFriendRequest = friendsCircleViewModel::respondToFriendRequest,
                                onRespondToInvite = friendsCircleViewModel::respondToInvite,
                                onOpenDirectChat = { uid, name ->
                                    friendsCircleViewModel.openDirectChatWithFriend(uid, name) {
                                        scope.launch { horizontalPagerState.animateScrollToPage(2) }
                                    }
                                },
                                onRemoveFriend = friendsCircleViewModel::removeFriend,
                                onPinWidget = handlePinWidget
                            )
                        } else if (isFeedGridMode) {
                            FriendsFeedGridPage(
                                posts = posts,
                                friendAvatars = friendAvatars,
                                pendingBadgeCount = pendingBadgeCount,
                                onGoToBooth = {
                                    isFeedGridMode = false
                                    scope.launch { verticalPagerState.animateScrollToPage(0) }
                                },
                                onToggleGridMode = { isFeedGridMode = false },
                                onSelectPost = { selectedIndex ->
                                    pendingGridTargetIndex = selectedIndex
                                    isFeedGridMode = false
                                },
                                onOpenFriendsManager = { showFriendsManager = true },
                                onOpenMessages = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(2) }
                                },
                                onOpenSettings = { showSettingsDialog = true },
                                onRequestSignOut = { showSignOutDialog = true }
                            )
                        } else {
                            val postIndex = (verticalPage - 1).coerceIn(0, posts.lastIndex)
                            val post = posts[postIndex]
                            FriendFeedSinglePhotoPage(
                                post = post,
                                pageIndex = postIndex,
                                totalPosts = posts.size,
                                currentUserId = currentUserId,
                                friendAvatars = friendAvatars,
                                pendingBadgeCount = pendingBadgeCount,
                                onGoToBooth = {
                                    scope.launch { verticalPagerState.animateScrollToPage(0) }
                                },
                                onToggleGridMode = { isFeedGridMode = true },
                                onOpenFriendsManager = { showFriendsManager = true },
                                onOpenMessages = {
                                    scope.launch { horizontalPagerState.animateScrollToPage(2) }
                                },
                                onOpenSettings = { showSettingsDialog = true },
                                onRequestSignOut = { showSignOutDialog = true },
                                onReactEmoji = { emoji -> friendsCircleViewModel.reactToPost(post, emoji) },
                                onViewReactionsClick = { reactionViewerPostId = post.id },
                                onReplyClick = {
                                    replyingPost = post
                                    friendsCircleViewModel.openPostComments(post.id)
                                },
                                onInspectClick = { inspectedPost = post },
                                onDeleteClick = { postToDelete = post }
                            )
                        }
                    }
                }
            }

            else -> DirectMessagesScreen(
                currentUserId = currentUserId,
                chatsState = directChatsState,
                friends = directFriends,
                friendAvatars = friendAvatars,
                activeChat = circleActionState.activeDirectChat,
                messagesState = directMessagesState,
                isBusy = circleActionState.isBusy,
                isSending = circleActionState.isSendingDirectMessage,
                onOpenChat = friendsCircleViewModel::openExistingDirectChat,
                onOpenFriendChat = { uid, name ->
                    friendsCircleViewModel.openDirectChatWithFriend(uid, name)
                },
                onStartChatByFriendCode = { code ->
                    friendsCircleViewModel.startDirectChatByFriendCode(code)
                },
                onCloseChat = friendsCircleViewModel::closeDirectChat,
                onSendMessage = friendsCircleViewModel::sendDirectMessage,
                onDeleteMessage = friendsCircleViewModel::deleteDirectMessage,
                onGoToBooth = {
                    scope.launch {
                        verticalPagerState.scrollToPage(0)
                        horizontalPagerState.animateScrollToPage(1)
                    }
                },
                onGoToCircle = {
                    scope.launch {
                        isFeedGridMode = false
                        verticalPagerState.scrollToPage(1.coerceAtMost(verticalPageCount - 1))
                        horizontalPagerState.animateScrollToPage(1)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    val pendingCropSlot = boothUiState.pendingCropSlotIndex
    val cropSourceBitmap = pendingCropSlot?.let { boothUiState.capturedPhotos.getOrNull(it) }
    if (pendingCropSlot != null && cropSourceBitmap != null) {
        PhotoCropEditorDialog(
            sourceBitmap = cropSourceBitmap,
            slotLabel = if (boothUiState.capturedPhotos.size > 1) {
                stringResource(R.string.ai_slot_item, pendingCropSlot + 1)
            } else {
                ""
            },
            onApply = { edited ->
                photoboothViewModel.applyEditedPhotoForSlot(pendingCropSlot, edited)
            },
            onDismiss = photoboothViewModel::dismissCropEditor
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            themeMode = themeMode,
            countdownSeconds = countdownSeconds,
            appIconVariant = appIconVariant,
            notificationsEnabled = notificationsEnabled,
            hasNotificationPermission = hasNotificationPermission,
            onSelectThemeMode = onSelectThemeMode,
            onSelectCountdownSeconds = onSelectCountdownSeconds,
            onSelectAppIcon = onSelectAppIcon,
            onToggleNotifications = onToggleNotifications,
            onRequestNotificationPermission = requestNotificationPermission,
            onPinWidget = handlePinWidget,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showFriendsManager) {
        FriendsManageDialog(
            myFriendCode = circleActionState.myFriendCode,
            friendRequests = friendRequests,
            invites = invites,
            friends = directFriends,
            isBusy = circleActionState.isBusy,
            onSendFriendRequest = friendsCircleViewModel::sendFriendRequest,
            onRespondToFriendRequest = friendsCircleViewModel::respondToFriendRequest,
            onRespondToInvite = friendsCircleViewModel::respondToInvite,
            onOpenDirectChat = { uid, name ->
                friendsCircleViewModel.openDirectChatWithFriend(uid, name) {
                    scope.launch { horizontalPagerState.animateScrollToPage(2) }
                }
            },
            onRemoveFriend = friendsCircleViewModel::removeFriend,
            onPinWidget = handlePinWidget,
            onDismiss = { showFriendsManager = false }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    postToDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text(stringResource(R.string.circle_delete_confirm_title)) },
            text = { Text(stringResource(R.string.circle_delete_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        friendsCircleViewModel.deletePost(post.id)
                        postToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.circle_delete_post))
                }
            },
            dismissButton = {
                TextButton(onClick = { postToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    inspectedPost?.let { post ->
        PostLightboxDialog(
            post = post,
            onDismiss = { inspectedPost = null },
            onReplyClick = {
                inspectedPost = null
                replyingPost = post
                friendsCircleViewModel.openPostComments(post.id)
            },
            onSaveBitmap = { bitmap ->
                scope.launch {
                    val res = GallerySaver.saveBitmapToGallery(context, bitmap)
                    val toastText = if (res.isSuccess) {
                        context.getString(R.string.saved_success_message)
                    } else {
                        context.getString(
                            R.string.saved_error_message,
                            res.exceptionOrNull()?.localizedMessage ?: ""
                        )
                    }
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    reactionViewerPostId?.let { targetId ->
        val livePost = posts.firstOrNull { it.id == targetId }
        if (livePost != null) {
            val reactionItems = remember(livePost.reactions, livePost.likedBy, friendAvatars, directFriends) {
                friendsCircleViewModel.buildReactionItems(livePost)
            }
            val myEmoji = livePost.reactions[currentUserId]
                ?: if (currentUserId in livePost.likedBy) "❤️" else null
            PostReactionsDialog(
                reactions = reactionItems,
                myReaction = myEmoji,
                onReactEmoji = { emoji -> friendsCircleViewModel.reactToPost(livePost, emoji) },
                onDismiss = { reactionViewerPostId = null }
            )
        } else {
            reactionViewerPostId = null
        }
    }

    replyingPost?.let { post ->
        PostRepliesDialog(
            post = post,
            currentUserId = currentUserId,
            friendAvatars = friendAvatars,
            commentsState = commentsState,
            isSending = circleActionState.isSendingComment,
            onSendReply = { mode, text, openChatAfterSend, onDone ->
                friendsCircleViewModel.sendReplyToPost(
                    post = post,
                    mode = mode,
                    text = text,
                    openChatAfterSend = openChatAfterSend,
                    onPublicSuccess = onDone,
                    onDirectSuccess = {
                        onDone()
                        if (openChatAfterSend) {
                            replyingPost = null
                            friendsCircleViewModel.openPostComments(null)
                            scope.launch { horizontalPagerState.animateScrollToPage(2) }
                        } else if (mode == ReplyDestinationMode.DIRECT_MESSAGE) {
                            replyingPost = null
                            friendsCircleViewModel.openPostComments(null)
                        }
                    }
                )
            },
            onDeleteComment = { commentId ->
                friendsCircleViewModel.deleteComment(post.id, commentId)
            },
            onDismiss = {
                replyingPost = null
                friendsCircleViewModel.openPostComments(null)
            }
        )
    }
}
