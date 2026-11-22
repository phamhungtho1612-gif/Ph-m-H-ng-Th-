package com.example

import android.graphics.Bitmap
import com.example.data.AppIconVariant
import com.example.data.FriendsCircleRepository
import com.example.imaging.CreativeTextRenderer
import com.example.imaging.CropAdjustParams
import com.example.imaging.CropAspectPreset
import com.example.imaging.PhotoboothFilters
import com.example.model.BoothLayout
import com.example.model.BoothScreen
import com.example.model.CameraLens
import com.example.model.CircleInvite
import com.example.model.CirclePost
import com.example.model.DirectChat
import com.example.model.DirectFriendItem
import com.example.model.DirectMessage
import com.example.model.EditorTab
import com.example.model.FrameStyle
import com.example.model.FriendCircle
import com.example.model.FriendCodeEntry
import com.example.model.FriendRequest
import com.example.model.Friendship
import com.example.model.OverlayFontStyle
import com.example.model.OverlayTextShape
import com.example.model.OverlayTextStyle
import com.example.model.PhotoFilter
import com.example.model.PostComment
import com.example.model.PostReactionItem
import com.example.model.ReplyDestinationMode
import com.example.model.UserProfile
import com.example.viewmodel.PhotoboothViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoboothUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filters expose expected color matrices`() {
        assertNull(PhotoboothFilters.matrixValues(PhotoFilter.ORIGINAL))
        assertEquals(20, PhotoboothFilters.matrixValues(PhotoFilter.HIGH_CONTRAST_BW)?.size)
        assertEquals(20, PhotoboothFilters.matrixValues(PhotoFilter.VINTAGE_FILM)?.size)
        assertEquals(20, PhotoboothFilters.matrixValues(PhotoFilter.KOREAN_PINK)?.size)
    }

    @Test
    fun `booth layouts and expanded frame styles match requirements`() {
        assertEquals(1, BoothLayout.SINGLE_PHOTO.shotCount)
        assertEquals(4, BoothLayout.PHOTO_STRIP.shotCount)
        assertEquals(4, BoothLayout.GRID_2X2.shotCount)
        assertEquals(11, FrameStyle.entries.size)
        assertTrue(FrameStyle.YELLOW_CARO in FrameStyle.entries)
        assertTrue(FrameStyle.FILM_STRIP in FrameStyle.entries)
        assertTrue(FrameStyle.SUN_YELLOW in FrameStyle.entries)
    }

    @Test
    fun `viewModel updates layout, lens, frame, filter, countdown clamping, and clips caption`() {
        val viewModel = PhotoboothViewModel()

        assertEquals(BoothLayout.SINGLE_PHOTO, viewModel.uiState.value.selectedLayout)
        assertEquals(CameraLens.FRONT, viewModel.uiState.value.cameraLens)
        assertEquals(3, viewModel.uiState.value.countdownDurationSeconds)
        assertTrue(viewModel.uiState.value.showDateStamp)
        assertNotNull(viewModel.uiState.value.formattedDate)

        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)
        assertEquals(BoothLayout.SINGLE_PHOTO, viewModel.uiState.value.selectedLayout)

        viewModel.selectLayout(BoothLayout.GRID_2X2)
        assertEquals(BoothLayout.GRID_2X2, viewModel.uiState.value.selectedLayout)

        viewModel.toggleCameraLens()
        assertEquals(CameraLens.BACK, viewModel.uiState.value.cameraLens)

        viewModel.toggleFillLight()
        assertTrue(viewModel.uiState.value.isFillLightEnabled)

        viewModel.selectFrameStyle(FrameStyle.FILM_STRIP)
        assertEquals(FrameStyle.FILM_STRIP, viewModel.uiState.value.selectedFrame)

        viewModel.selectFilter(PhotoFilter.KOREAN_PINK)
        assertEquals(PhotoFilter.KOREAN_PINK, viewModel.uiState.value.selectedFilter)

        val longCaption = "Một dòng chữ kỷ niệm rất rất dài vượt quá ba mươi sáu ký tự"
        viewModel.updateCaption(longCaption)
        assertEquals(PhotoboothViewModel.MAX_CAPTION_LENGTH, viewModel.uiState.value.captionText.length)

        viewModel.setCountdownDuration(-5)
        assertEquals(0, viewModel.uiState.value.countdownDurationSeconds)

        viewModel.setCountdownDuration(15)
        assertEquals(10, viewModel.uiState.value.countdownDurationSeconds)

        viewModel.setCountdownDuration(7)
        assertEquals(7, viewModel.uiState.value.countdownDurationSeconds)
    }

    @Test
    fun `consecutive 4-photo capture triggers 3s countdown, flash, and transitions to customize`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.PHOTO_STRIP)

        var shutterCalls = 0
        viewModel.startShootingSequence {
            shutterCalls++
            viewModel.onPhotoCaptured(stubBitmap())
        }

        // Start of Shot 1: countdown = 3
        runCurrent()
        assertTrue(viewModel.uiState.value.isShootingSequence)
        assertEquals(1, viewModel.uiState.value.currentShotNumber)
        assertEquals(3, viewModel.uiState.value.countdownSeconds)
        assertEquals(0, viewModel.uiState.value.flashTriggerCount)

        // Advance through 3-2-1 countdown for Shot 1
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.countdownSeconds)

        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.countdownSeconds)

        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(1, shutterCalls)
        assertEquals(1, viewModel.uiState.value.flashTriggerCount)
        assertEquals(1, viewModel.uiState.value.capturedPhotos.size)

        // Complete Shots 2, 3, and 4 (each 500ms pause + 3000ms countdown) + 300ms transition
        advanceTimeBy(3 * 3500L + 350L)
        runCurrent()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isShootingSequence)
        assertEquals(4, shutterCalls)
        assertEquals(4, finalState.flashTriggerCount)
        assertEquals(4, finalState.capturedPhotos.size)
        assertEquals(BoothScreen.Customize, finalState.currentScreen)
        assertNull(finalState.captureErrorMessage)
    }

    @Test
    fun `zero-second countdown shoots immediately without countdown digits`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)
        viewModel.setCountdownDuration(0)

        var shutterCalls = 0
        viewModel.startShootingSequence {
            shutterCalls++
            viewModel.onPhotoCaptured(stubBitmap())
        }

        runCurrent()
        assertEquals(1, shutterCalls)
        assertNull(viewModel.uiState.value.countdownSeconds)

        advanceTimeBy(350L)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isShootingSequence)
        assertEquals(1, state.capturedPhotos.size)
        assertEquals(BoothScreen.Customize, state.currentScreen)
    }

    @Test
    fun `single photo capture runs 1 countdown and transitions to customize`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)

        var shutterCalls = 0
        viewModel.startShootingSequence {
            shutterCalls++
            viewModel.onPhotoCaptured(stubBitmap())
        }

        advanceTimeBy(3350L)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isShootingSequence)
        assertEquals(1, shutterCalls)
        assertEquals(1, state.flashTriggerCount)
        assertEquals(1, state.capturedPhotos.size)
        assertEquals(BoothScreen.Customize, state.currentScreen)
    }

    @Test
    fun `cancelShootingSequence and capture failure reset sequence cleanly`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.startShootingSequence {}
        runCurrent()
        assertTrue(viewModel.uiState.value.isShootingSequence)

        viewModel.cancelShootingSequence()
        runCurrent()
        assertFalse(viewModel.uiState.value.isShootingSequence)
        assertNull(viewModel.uiState.value.countdownSeconds)
        assertEquals(0, viewModel.uiState.value.currentShotNumber)

        viewModel.startShootingSequence {
            viewModel.onPhotoCaptureFailed(IllegalStateException("Camera test error"))
        }
        advanceTimeBy(3000L)
        runCurrent()

        assertFalse(viewModel.uiState.value.isShootingSequence)
        assertEquals("Camera test error", viewModel.uiState.value.captureErrorMessage)
    }

    @Test
    fun `theme modes, direct messages, and friends circle models have expected defaults`() {
        assertEquals(3, com.example.data.AppThemeMode.entries.size)
        val circle = FriendCircle(id = "ABC123", name = "Hội bạn thân")
        assertEquals("ABC123", circle.id)
        assertTrue(circle.memberUids.isEmpty())
        assertTrue(circle.memberNames.isEmpty())

        val post = CirclePost(id = "post_1", circleId = "ABC123", layout = BoothLayout.SINGLE_PHOTO.name)
        assertEquals(BoothLayout.SINGLE_PHOTO.name, post.layout)
        assertEquals(FrameStyle.WHITE.name, post.frameStyle)
        assertEquals(PhotoFilter.ORIGINAL.name, post.filter)
        assertTrue(post.likedBy.isEmpty())

        val invite = CircleInvite(id = "inv_1", circleId = "ABC123", fromUserId = "u1", toUserId = "u2")
        assertEquals("PENDING", invite.status)

        val codeEntry = FriendCodeEntry(friendCode = "AB7K29", userId = "u1", displayName = "Lan")
        assertEquals("AB7K29", codeEntry.friendCode)

        val comment = PostComment(id = "c1", postId = "post_1", text = "Xinh quá!")
        assertEquals("Xinh quá!", comment.text)
        assertTrue(comment.memberUids.isEmpty())

        val dmChat = DirectChat(id = "u1_u2", participantUids = listOf("u1", "u2"))
        assertEquals(2, dmChat.participantUids.size)

        val dmMessage = DirectMessage(id = "m1", chatId = "u1_u2", senderId = "u1", text = "Chào bạn!")
        assertEquals("Chào bạn!", dmMessage.text)

        val friendReq = FriendRequest(id = "req_1", fromUserId = "u1", toUserId = "u2", fromName = "Lan")
        assertEquals("PENDING", friendReq.status)
        assertEquals("Lan", friendReq.fromName)

        val friendship = Friendship(id = "u1_u2", participantUids = listOf("u1", "u2"))
        assertEquals(2, friendship.participantUids.size)

        val friendItem = DirectFriendItem(uid = "u2", displayName = "Minh", isDirectFriend = true)
        assertTrue(friendItem.isDirectFriend)

        assertEquals("FRIENDS_ALL", FriendsCircleRepository.SHARED_CIRCLE_ID)
        assertEquals("Friends", FriendsCircleRepository.SHARED_CIRCLE_NAME)
        assertEquals(4, AppIconVariant.entries.size)
        assertEquals(4, AppIconVariant.entries.map { it.aliasClassName }.distinct().size)

        val profile = UserProfile(
            userId = "u1",
            displayName = "Lan",
            bio = "Thích chụp ảnh cùng hội bạn",
            avatarBase64 = "abc123"
        )
        assertEquals("Thích chụp ảnh cùng hội bạn", profile.bio)
        assertEquals("abc123", profile.avatarBase64)
    }

    @Test
    fun `in-booth filter selection persists through capture sequence and retake`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)
        viewModel.setCountdownDuration(0)
        viewModel.selectFilter(PhotoFilter.VINTAGE_FILM)

        viewModel.startShootingSequence {
            viewModel.onPhotoCaptured(stubBitmap())
        }
        advanceTimeBy(350L)
        runCurrent()

        assertEquals(BoothScreen.Customize, viewModel.uiState.value.currentScreen)
        assertEquals(PhotoFilter.VINTAGE_FILM, viewModel.uiState.value.selectedFilter)

        viewModel.returnToCameraForRetake()
        assertEquals(BoothScreen.Capture, viewModel.uiState.value.currentScreen)
        assertEquals(PhotoFilter.VINTAGE_FILM, viewModel.uiState.value.selectedFilter)
    }

    @Test
    fun `double tapping startShootingSequence does not restart or cancel active sequence`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)

        var firstCalls = 0
        var secondCalls = 0
        viewModel.startShootingSequence {
            firstCalls++
            viewModel.onPhotoCaptured(stubBitmap())
        }
        runCurrent()
        assertTrue(viewModel.uiState.value.isShootingSequence)
        assertEquals(3, viewModel.uiState.value.countdownSeconds)

        // Second rapid shutter call while shooting must be ignored
        viewModel.startShootingSequence {
            secondCalls++
            viewModel.onPhotoCaptured(stubBitmap())
        }
        runCurrent()
        assertTrue(viewModel.uiState.value.isShootingSequence)

        advanceTimeBy(3350L)
        runCurrent()

        assertEquals(1, firstCalls)
        assertEquals(0, secondCalls)
        assertFalse(viewModel.uiState.value.isShootingSequence)
        assertEquals(BoothScreen.Customize, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `reportCaptureError sets message and clears loading while dismissCaptureError resets it`() {
        val viewModel = PhotoboothViewModel()
        assertFalse(viewModel.uiState.value.isLoadingPhotos)
        assertNull(viewModel.uiState.value.captureErrorMessage)

        viewModel.reportCaptureError("Không nhận được tệp ảnh từ bộ chọn")
        assertFalse(viewModel.uiState.value.isLoadingPhotos)
        assertEquals(
            "Không nhận được tệp ảnh từ bộ chọn",
            viewModel.uiState.value.captureErrorMessage
        )

        viewModel.dismissCaptureError()
        assertNull(viewModel.uiState.value.captureErrorMessage)
    }

    @Test
    fun `creative AI text shapes, styles, fonts, and viewModel controls cover full customization`() {
        assertEquals(10, OverlayTextShape.entries.size)
        assertTrue(OverlayTextShape.ARC_UP in OverlayTextShape.entries)
        assertTrue(OverlayTextShape.HEART in OverlayTextShape.entries)
        assertTrue(OverlayTextShape.CIRCLE in OverlayTextShape.entries)
        assertTrue(OverlayTextShape.WAVE in OverlayTextShape.entries)
        assertTrue(OverlayTextShape.MAGAZINE_STACK in OverlayTextShape.entries)

        assertEquals(14, OverlayTextStyle.entries.size)
        assertEquals(13, OverlayFontStyle.entries.size)
        assertTrue(CreativeTextRenderer.curatedPaletteHex.size >= 12)

        val viewModel = PhotoboothViewModel()
        assertEquals("", viewModel.uiState.value.creativeText.text)
        assertTrue(viewModel.uiState.value.creativeText.behindSubject)

        viewModel.selectEditorTab(EditorTab.AI_TEXT)
        assertEquals(EditorTab.AI_TEXT, viewModel.uiState.value.activeEditorTab)

        viewModel.updateCreativeText("KỶ NIỆM BÊN NHAU MÃI MÃI KHÔNG BAO GIỜ QUÊN ĐƯỢC")
        assertEquals(
            PhotoboothViewModel.MAX_CREATIVE_TEXT_LENGTH,
            viewModel.uiState.value.creativeText.text.length
        )

        viewModel.selectOverlayShape(OverlayTextShape.HEART)
        viewModel.toggleRepeatAlongShape(true)
        viewModel.selectOverlayStyle(OverlayTextStyle.NEON_GLOW)
        viewModel.selectOverlayFont(OverlayFontStyle.DANCING_SCRIPT)
        viewModel.selectOverlayColor("#FFD426")
        viewModel.updateCreativeTextScale(5.0f)
        viewModel.updateCreativeTextLetterSpacing(0.32f)
        viewModel.updateCreativeTextLineSpacing(1.30f)
        viewModel.updateCreativeTextPositionY(0.02f)
        viewModel.updateCreativeTextPositionX(0.99f)
        viewModel.updateCreativeTextBend(90f)
        viewModel.updateCreativeTextOpacity(0.1f)
        viewModel.selectTargetSlot(2)

        val edited = viewModel.uiState.value.creativeText
        assertEquals(OverlayTextShape.HEART, edited.shape)
        assertTrue(edited.repeatAlongShape)
        assertEquals(OverlayTextStyle.NEON_GLOW, edited.style)
        assertEquals(OverlayFontStyle.DANCING_SCRIPT, edited.font)
        assertEquals("#FFD426", edited.colorHex)
        assertEquals(3.5f, edited.scale, 0.001f)
        assertEquals(0.32f, edited.letterSpacingEm, 0.001f)
        assertEquals(1.30f, edited.lineSpacingMultiplier, 0.001f)
        assertEquals(0.12f, edited.centerYFraction, 0.001f)
        assertEquals(0.85f, edited.centerXFraction, 0.001f)
        assertEquals(45f, edited.bendAngleDegrees, 0.001f)
        assertEquals(0.30f, edited.opacity, 0.001f)
        assertEquals(2, edited.targetSlotIndex)

        viewModel.toggleBehindSubject(false)
        assertFalse(viewModel.uiState.value.creativeText.behindSubject)

        viewModel.resetCreativeTextTransform()
        val reset = viewModel.uiState.value.creativeText
        assertEquals(1.0f, reset.scale, 0.001f)
        assertEquals(0.0f, reset.letterSpacingEm, 0.001f)
        assertEquals(0.95f, reset.lineSpacingMultiplier, 0.001f)
        assertEquals(0.5f, reset.centerXFraction, 0.001f)
        assertEquals(0.30f, reset.centerYFraction, 0.001f)
        assertEquals(0f, reset.bendAngleDegrees, 0.001f)
        assertEquals(0.96f, reset.opacity, 0.001f)
    }

    @Test
    fun `reactions, quoted DM photo replies, and crop presets expose expected values`() {
        val post = CirclePost(
            id = "p1",
            reactions = mapOf("u2" to "🔥", "u3" to "💛"),
            reactorNames = mapOf("u2" to "Minh", "u3" to "Lan")
        )
        assertEquals("🔥", post.reactions["u2"])
        assertEquals("Lan", post.reactorNames["u3"])

        val reactionItem = PostReactionItem(
            userId = "u2",
            displayName = "Minh",
            emoji = "🔥",
            avatarBase64 = "avatar_b64"
        )
        assertEquals("Minh", reactionItem.displayName)
        assertEquals("🔥", reactionItem.emoji)
        assertEquals("avatar_b64", reactionItem.avatarBase64)

        val quotedDm = DirectMessage(
            id = "m_quote",
            chatId = "u1_u2",
            senderId = "u2",
            senderName = "Minh",
            text = "Tự làm hả",
            replyPostId = "p1",
            replyPostImageBase64 = "img_b64",
            replyPostAuthorId = "u1",
            replyPostAuthorName = "Lan",
            replyPostCaption = "bất ngờ cho hai bạn ấy",
            replyPostTimeLabel = "3d"
        )
        assertEquals("p1", quotedDm.replyPostId)
        assertEquals("img_b64", quotedDm.replyPostImageBase64)
        assertEquals("bất ngờ cho hai bạn ấy", quotedDm.replyPostCaption)
        assertEquals(2, ReplyDestinationMode.entries.size)
        assertTrue(ReplyDestinationMode.DIRECT_MESSAGE in ReplyDestinationMode.entries)
        assertTrue(ReplyDestinationMode.PUBLIC_COMMENT in ReplyDestinationMode.entries)

        assertEquals(5, CropAspectPreset.entries.size)
        assertNull(CropAspectPreset.FREE.ratio)
        assertEquals(1f, CropAspectPreset.SQUARE_1_1.ratio)
        assertEquals(3f / 4f, CropAspectPreset.PORTRAIT_3_4.ratio ?: 0f, 0.001f)
        assertEquals(4f / 5f, CropAspectPreset.PORTRAIT_4_5.ratio ?: 0f, 0.001f)
        assertEquals(4f / 3f, CropAspectPreset.LANDSCAPE_4_3.ratio ?: 0f, 0.001f)

        val defaultCrop = CropAdjustParams()
        assertEquals(0, defaultCrop.rotationDegrees)
        assertFalse(defaultCrop.flipHorizontal)
        assertEquals(CropAspectPreset.FREE, defaultCrop.aspectPreset)
        assertEquals(1f, defaultCrop.zoom, 0.001f)
        assertEquals(0f, defaultCrop.brightness, 0.001f)
        assertEquals(1f, defaultCrop.contrast, 0.001f)
    }

    @Test
    fun `crop editor state opens, dismisses, and replaces slot bitmap cleanly`() = runTest(testDispatcher) {
        val viewModel = PhotoboothViewModel()
        viewModel.selectLayout(BoothLayout.SINGLE_PHOTO)
        viewModel.setCountdownDuration(0)

        val initialBitmap = stubBitmap()
        viewModel.startShootingSequence {
            viewModel.onPhotoCaptured(initialBitmap)
        }
        advanceTimeBy(350L)
        runCurrent()

        assertNull(viewModel.uiState.value.pendingCropSlotIndex)
        assertEquals(1, viewModel.uiState.value.capturedPhotos.size)

        viewModel.openCropEditor(0)
        assertEquals(0, viewModel.uiState.value.pendingCropSlotIndex)

        viewModel.dismissCropEditor()
        assertNull(viewModel.uiState.value.pendingCropSlotIndex)

        viewModel.openCropEditor(0)
        assertEquals(0, viewModel.uiState.value.pendingCropSlotIndex)

        val editedBitmap = stubBitmap()
        viewModel.applyEditedPhotoForSlot(0, editedBitmap)
        assertNull(viewModel.uiState.value.pendingCropSlotIndex)
        assertTrue(viewModel.uiState.value.capturedPhotos.first() === editedBitmap)
    }

    private fun stubBitmap(): Bitmap {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, Bitmap::class.java) as Bitmap
    }
}
