package com.cs312.drafty.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.cs312.drafty.core.auth.AuthSession
import com.cs312.drafty.core.auth.EmailConfirmationRequiredException
import com.cs312.drafty.data.local.PendingVoiceNoteEntity
import com.cs312.drafty.ui.DraftyViewModel
import com.cs312.drafty.ui.IdeaCard
import com.cs312.drafty.ui.RecordingState
import com.cs312.drafty.ui.UserProfileState
import com.cs312.drafty.ui.components.AmbientIdeaBubblesLayer
import com.cs312.drafty.ui.components.DraftyMascot
import com.cs312.drafty.ui.components.GlassButton
import com.cs312.drafty.ui.components.GlassIconButton
import com.cs312.drafty.ui.components.GlassToastHost
import com.cs312.drafty.ui.components.HapticChoreographer
import com.cs312.drafty.ui.components.LiquidGlassRecordingOverlay
import com.cs312.drafty.ui.components.MascotState
import com.cs312.drafty.ui.components.PixelCheckIcon
import com.cs312.drafty.ui.components.PixelCloseIcon
import com.cs312.drafty.ui.components.PixelEyeIcon
import com.cs312.drafty.ui.components.PixelEyeOffIcon
import com.cs312.drafty.ui.components.PixelIdeasIcon
import com.cs312.drafty.ui.components.PixelQueueIcon
import com.cs312.drafty.ui.components.PixelStopIcon
import com.cs312.drafty.ui.components.PixelSyncIcon
import com.cs312.drafty.ui.components.PixelTrashIcon
import com.cs312.drafty.ui.components.PixelUserIcon
import com.cs312.drafty.ui.components.PlatformLogoBadge
import com.cs312.drafty.ui.components.SoftCard
import com.cs312.drafty.ui.components.SoftChip
import com.cs312.drafty.ui.components.buildAmbientIdeaPrompts
import com.cs312.drafty.ui.components.canonicalPlatform
import com.cs312.drafty.ui.components.rememberGlassToastState
import com.cs312.drafty.ui.components.rememberHapticChoreographer
import com.cs312.drafty.ui.components.springPressScale
import com.cs312.drafty.ui.theme.AppBackground
import com.cs312.drafty.ui.theme.CardShape
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.InnerTileShape
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.ShadowAmbient
import com.cs312.drafty.ui.theme.SheetTopShape
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusAmber
import com.cs312.drafty.ui.theme.StatusGreen
import com.cs312.drafty.ui.theme.StatusRed
import com.cs312.drafty.ui.theme.SubtleDivider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Screen A: Minimal voice-first Home canvas with top-right profile button & 3-item glassmorphic navbar. */
@Composable
fun HomeScreen(vm: DraftyViewModel, onOpenContainer: (String) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHapticChoreographer()
    val toastState = rememberGlassToastState()
    val cancelThresholdPx = with(density) { -96.dp.toPx() }
    val lockThresholdPx = with(density) { 84.dp.toPx() }

    val ideas by vm.recentIdeas.collectAsState()
    val pendingNotes by vm.pendingNotes.collectAsState()
    val profile by vm.userProfile.collectAsState()
    val sheetVisible by vm.sheetVisible.collectAsState()

    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showIdeasSheet by rememberSaveable { mutableStateOf(false) }
    var seededPrompt by rememberSaveable { mutableStateOf<String?>(null) }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var isRecordingLocked by remember { mutableStateOf(false) }
    var celebratingContainerId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            toastState.show("Mic enabled! Hold Drafty to record.")
        } else {
            toastState.show("Microphone access is needed to record voice notes.")
        }
    }

    LaunchedEffect(sheetVisible) {
        if (!sheetVisible) {
            isRecordingLocked = false
            dragOffsetX = 0f
            dragOffsetY = 0f
        }
    }

    LaunchedEffect(vm) {
        vm.recordingState.collect { s ->
            if (s !is RecordingState.Recording) {
                isRecordingLocked = false
            }
            if (s is RecordingState.Success) {
                celebratingContainerId = s.containerId
                seededPrompt = null
                haptics.onSuccess()
                delay(650L)
                val targetId = s.containerId
                celebratingContainerId = null
                vm.resetRecording()
                onOpenContainer(targetId)
            }
        }
    }

    val overlayVisible = sheetVisible || celebratingContainerId != null
    val chromeAlpha by animateFloatAsState(
        targetValue = if (overlayVisible) 0f else 1f,
        label = "navbarSideChromeAlpha",
    )
    val backgroundBlur by animateDpAsState(
        targetValue = if (overlayVisible) 18.dp else 0.dp,
        label = "homeBackgroundBlur",
    )
    val cancelDragProgress = (dragOffsetY / cancelThresholdPx).coerceIn(0f, 1f)
    val lockDragProgress = if (isRecordingLocked) 1f else (abs(dragOffsetX) / lockThresholdPx).coerceIn(0f, 1f)
    val ambientPrompts = remember(ideas, profile) { buildAmbientIdeaPrompts(ideas, profile) }

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground)) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp
        val fontScale = density.fontScale
        val maxBubbles = when {
            isLandscape || maxHeight < 580.dp -> 0
            maxHeight < 680.dp || fontScale > 1.2f -> 2
            else -> 3
        }

        // 1 & 2) Underlying Home content (responsive ambient bubbles + Profile + centered greeting), blurred on Android 12+ while recording
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && backgroundBlur > 0.5.dp) {
                        Modifier.blur(backgroundBlur)
                    } else {
                        Modifier
                    },
                ),
        ) {

            // Focused radial pink -> orange ombré behind the Drafty button
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val origin = Offset(w * 0.5f, h - 92.dp.toPx())
                val radius = h * 0.34f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color(0x82FF5CA8), // luminous pink core behind Drafty
                        0.38f to Color(0x5EFF7E79), // pink-to-coral transition
                        0.72f to Color(0x36FF9652), // warm orange outer edge
                        1.00f to Color.Transparent,
                        center = origin,
                        radius = radius,
                    ),
                    radius = radius,
                    center = origin,
                )
            }

            // Responsive ambient background speech-bubble idea prompts (tap to seed recording prompt)
            AmbientIdeaBubblesLayer(
                prompts = ambientPrompts,
                maxBubbles = maxBubbles,
                onBubbleTap = { prompt ->
                    seededPrompt = prompt
                },
            )

            GlassIconButton(
                onClick = { showProfileSheet = true },
                size = 44.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 20.dp, top = 12.dp),
            ) {
                PixelUserIcon(tint = InkBlack, size = 24.dp)
            }

            // 2) Optical upper-middle editorial greeting + crisp pixel '{ Drafty }' wordmark + quick-resume pill
            Column(
                modifier = Modifier
                    .align(BiasAlignment(0f, if (isLandscape) -0.45f else -0.18f))
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "hey, ${profile.brandName.ifBlank { "Drafter" }}",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                    ),
                    color = MutedGray,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "{",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 44.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = InkBlack,
                    )
                    PixelDraftyWordmark(
                        color = InkBlack,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = "}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 44.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = InkBlack,
                    )
                }

                // Contextual Quick-Resume Pill (omitted when both lists are empty)
                val latestIdea = ideas.firstOrNull()
                if (pendingNotes.isNotEmpty() || latestIdea != null) {
                    Spacer(Modifier.height(20.dp))
                    val pillShape = RoundedCornerShape(50)
                    val isQueuePill = pendingNotes.isNotEmpty()
                    val pillLabel = if (isQueuePill) {
                        val n = pendingNotes.size
                        "$n offline ${if (n == 1) "note" else "notes"} ready to sync"
                    } else {
                        "Latest: ${latestIdea!!.idea.title}"
                    }
                    Surface(
                        onClick = {
                            if (isQueuePill) showQueueSheet = true
                            else onOpenContainer(latestIdea!!.idea.id)
                        },
                        shape = pillShape,
                        color = Color(0xCCFFFFFF),
                        border = BorderStroke(1.dp, Color.White),
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .shadow(4.dp, pillShape, ambientColor = ShadowAmbient, spotColor = Color(0x14000000)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isQueuePill) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(StatusRed),
                                )
                            }
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = InkBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                color = Color(0xFF8E8E93),
                            )
                        }
                    }
                }
            }
        }

        // 3) Full-viewport frosted liquid-glass RGB screen-edge overlay (zIndex 1)
        LiquidGlassRecordingOverlay(
            visible = overlayVisible,
            stateFlow = vm.recordingState,
            amplitudeFlow = vm.amplitude,
            cancelDragProgress = cancelDragProgress,
            isCancelZone = !isRecordingLocked && dragOffsetY < cancelThresholdPx,
            isCelebratingSave = celebratingContainerId != null,
            onDismissError = vm::resetRecording,
            activePrompt = seededPrompt,
            lockDragProgress = lockDragProgress,
            isLocked = isRecordingLocked,
            modifier = Modifier.zIndex(1f),
        )

        // 4) Smooth, symmetric notched center-cradle liquid-glass bottom navbar (zero overlap, zIndex 2)
        val dockShape = remember(density) {
            val cradleR = with(density) { 54.dp.toPx() }
            val circleCenterY = with(density) { (-13).dp.toPx() }
            val shoulder = with(density) { 14.dp.toPx() }
            GenericShape { size, _ ->
                val w = size.width
                val h = size.height
                val r = h / 2f
                val cx = w / 2f

                // Exact angle where cradle circle crosses y = 0, inset slightly for smooth tangent shoulders
                val dy = (0f - circleCenterY).coerceIn(0f, cradleR - 1f)
                val baseAngleDeg = (asin((dy / cradleR).coerceIn(-1f, 1f)) * 180.0 / PI).toFloat()
                val cutAngleDeg = (baseAngleDeg + 8f).coerceAtMost(45f)
                val cutRad = (cutAngleDeg * PI / 180.0)

                // Exact symmetric arc endpoints on left and right
                val arcOffsetX = (cradleR * cos(cutRad)).toFloat()
                val arcY = (circleCenterY + cradleR * sin(cutRad)).toFloat()
                val lx = (cx - arcOffsetX).coerceAtLeast(r + shoulder)
                val rx = (cx + arcOffsetX).coerceAtMost(w - r - shoulder)

                // Start at top-left after left pill cap
                moveTo(r, 0f)
                // Smooth symmetric left shoulder landing on exact arc start point (lx, arcY)
                lineTo(lx - shoulder, 0f)
                quadraticTo(lx - shoulder * 0.28f, 0f, lx, arcY)
                // Semicircular cradle hugging the bottom of Drafty
                arcTo(
                    rect = Rect(
                        left = cx - cradleR,
                        top = circleCenterY - cradleR,
                        right = cx + cradleR,
                        bottom = circleCenterY + cradleR,
                    ),
                    startAngleDegrees = 180f - cutAngleDeg,
                    sweepAngleDegrees = -(180f - 2f * cutAngleDeg),
                    forceMoveTo = false,
                )
                // Mirror-symmetric right shoulder rising smoothly from (rx, arcY) to y = 0
                quadraticTo(rx + shoulder * 0.28f, 0f, rx + shoulder, 0f)
                // Top-right edge to right pill cap
                lineTo(w - r, 0f)
                arcTo(
                    rect = Rect(w - 2f * r, 0f, w, h),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                // Bottom straight edge to left pill cap
                lineTo(r, h)
                arcTo(
                    rect = Rect(0f, 0f, 2f * r, h),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }
        }
        val dockBrush = Brush.linearGradient(
            listOf(
                Color(0x9EFFFFFF).copy(alpha = 0.62f * chromeAlpha),
                Color(0x42FFFFFF).copy(alpha = 0.28f * chromeAlpha),
                Color(0x70FFFFFF).copy(alpha = 0.44f * chromeAlpha),
            ),
        )
        val dockBorder = Brush.linearGradient(
            listOf(
                Color(0xFAFFFFFF).copy(alpha = 0.96f * chromeAlpha),
                Color(0x4DFFFFFF).copy(alpha = 0.32f * chromeAlpha),
                Color(0xB8FFFFFF).copy(alpha = 0.72f * chromeAlpha),
            ),
        )
        val spriteAlpha by animateFloatAsState(
            targetValue = if (overlayVisible) 0f else 1f,
            label = "bottomDraftySpriteAlpha",
        )
        val spriteScale by animateFloatAsState(
            targetValue = if (overlayVisible) 0.55f else 1f,
            label = "bottomDraftySpriteScale",
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .zIndex(2f)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!seededPrompt.isNullOrBlank()) {
                val chipShape = RoundedCornerShape(50)
                Surface(
                    shape = chipShape,
                    color = Color(0xD9FFFFFF),
                    border = BorderStroke(1.dp, Color.White),
                    modifier = Modifier
                        .padding(bottom = 10.dp, start = 28.dp, end = 28.dp)
                        .alpha(chromeAlpha)
                        .shadow(4.dp, chipShape, ambientColor = ShadowAmbient, spotColor = Color(0x14000000)),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Prompt: $seededPrompt",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = InkBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            onClick = { seededPrompt = null },
                            shape = CircleShape,
                            color = SoftWell,
                        ) {
                            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                PixelCloseIcon(tint = InkBlack, size = 8.dp)
                            }
                        }
                    }
                }
            }
            Text(
                text = "Hold Drafty to talk · Slide up to cancel",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = Color(0xFF8E8E93),
                modifier = Modifier
                    .padding(bottom = if (isLandscape) 24.dp else 42.dp)
                    .alpha(chromeAlpha),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isLandscape) Modifier.widthIn(max = 460.dp) else Modifier),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Compact frosted glassmorphic pill base (64.dp tall, narrower horizontal span)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 44.dp)
                        .height(64.dp)
                        .shadow(
                            elevation = if (overlayVisible) 0.dp else 10.dp,
                            shape = dockShape,
                            ambientColor = ShadowAmbient,
                            spotColor = Color(0x18000000),
                        ),
                    shape = dockShape,
                    color = Color.Transparent,
                    border = if (chromeAlpha > 0.05f) BorderStroke(1.dp, dockBorder) else null,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(dockShape)
                            .background(dockBrush)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left — Offline Queue button (44dp touch target, 22dp pixel glyph + 8dp coral dot)
                        Box(
                            contentAlignment = Alignment.TopEnd,
                            modifier = Modifier.alpha(chromeAlpha),
                        ) {
                            Surface(
                                onClick = { if (!overlayVisible) showQueueSheet = true },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PixelQueueIcon(tint = InkBlack, size = 22.dp)
                                }
                            }
                            if (pendingNotes.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StatusRed),
                                )
                            }
                        }

                        // Right — Recent Ideas button (44dp touch target, 22dp pixel glyph)
                        Surface(
                            onClick = { if (!overlayVisible) showIdeasSheet = true },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(44.dp)
                                .alpha(chromeAlpha),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PixelIdeasIcon(tint = InkBlack, size = 22.dp)
                            }
                        }
                    }
                }

                // Enlarged protruding overlapping 94dp x 94dp frosted-white glassmorphic Drafty trigger
                val padBrush = Brush.linearGradient(
                    listOf(
                        Color(0xFCFFFFFF), // bright pure-white top-left specular shine (~99%)
                        Color(0xE8FFFFFF), // clean frosted white core (~91%) preserving white identity
                        Color(0xF5FFFFFF), // soft white glass bottom-right reflection (~96%)
                    ),
                )
                val padBorder = Brush.linearGradient(
                    listOf(
                        Color.White,
                        Color(0xE6FFFFFF),
                        Color(0x2E000000),
                    ),
                )
                Box(
                    modifier = Modifier
                        .offset(y = (-30).dp)
                        .size(94.dp)
                        .springPressScale(0.94f)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                if (isRecordingLocked) return@awaitEachGesture
                                val hasMic = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.RECORD_AUDIO,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasMic) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@awaitEachGesture
                                }

                                haptics.onPressDown()
                                val pressStartMs = SystemClock.elapsedRealtime()
                                dragOffsetY = 0f
                                dragOffsetX = 0f
                                var wasInCancelZone = false
                                vm.startRecording()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) break
                                    val delta = change.positionChange()
                                    dragOffsetY += delta.y
                                    dragOffsetX += delta.x
                                    change.consume()

                                    val nowInCancelZone = dragOffsetY < cancelThresholdPx
                                    if (nowInCancelZone && !wasInCancelZone) {
                                        haptics.onThresholdCross()
                                    }
                                    wasInCancelZone = nowInCancelZone

                                    // Horizontal slide past threshold engages hands-free Lock-to-Record
                                    if (!nowInCancelZone && abs(dragOffsetX) >= lockThresholdPx) {
                                        haptics.onLockEngaged()
                                        isRecordingLocked = true
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        break
                                    }
                                }
                                if (!isRecordingLocked && vm.recordingState.value is RecordingState.Recording) {
                                    val heldMs = SystemClock.elapsedRealtime() - pressStartMs
                                    if (dragOffsetY < cancelThresholdPx || heldMs < 700L) {
                                        haptics.onThresholdCross()
                                        vm.cancelRecording()
                                    } else {
                                        haptics.onReleaseDraft()
                                        vm.stopRecordingAndProcess(vm.userId)
                                    }
                                }
                                dragOffsetY = 0f
                                dragOffsetX = 0f
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Tactile frosted glass thumb anchor ring pulsing with voice amplitude while holding (hidden when locked)
                    ThumbAnchorRing(
                        visible = overlayVisible && !isRecordingLocked,
                        amplitudeFlow = vm.amplitude,
                        isCancelZone = dragOffsetY < cancelThresholdPx,
                    )

                    // Idle elevated circular Drafty button (fades out cleanly while holding)
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(
                                elevation = if (overlayVisible) 0.dp else 20.dp,
                                shape = CircleShape,
                                ambientColor = Color(0x24000000),
                                spotColor = Color(0x36000000),
                            )
                            .graphicsLayer {
                                alpha = spriteAlpha
                                scaleX = spriteScale
                                scaleY = spriteScale
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, padBorder),
                        shadowElevation = 0.dp,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(padBrush)
                                .padding(15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            DraftyMascot(MascotState.IDLE, Modifier.size(60.dp))
                        }
                    }
                }
            }
        }
        // 5) Hands-Free Locked Recording Controls (Stop & Draft + Cancel, zIndex 3)
        AnimatedVisibility(
            visible = isRecordingLocked && overlayVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(3f)
                .navigationBarsPadding()
                .padding(bottom = 30.dp, start = 24.dp, end = 24.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = {
                        haptics.onThresholdCross()
                        isRecordingLocked = false
                        vm.cancelRecording()
                    },
                    size = 52.dp,
                ) {
                    PixelCloseIcon(tint = InkBlack, size = 14.dp)
                }
                GlassButton(
                    text = "Stop & Draft",
                    dark = true,
                    minHeight = 52.dp,
                    horizontalPadding = 22.dp,
                    leadingIcon = { PixelStopIcon(tint = CardSurface, size = 13.dp) },
                    onClick = {
                        haptics.onReleaseDraft()
                        isRecordingLocked = false
                        vm.stopRecordingAndProcess(vm.userId)
                    },
                )
            }
        }

        // 6) Floating frosted-glass toast host (replaces system Android Toasts, zIndex 4)
        GlassToastHost(
            state = toastState,
            bottomPadding = 128.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(4f),
        )
    }

    val authSession by vm.authSession.collectAsState()

    if (showProfileSheet) {
        CreatorProfileSheet(
            profile = profile,
            authSession = authSession,
            onDismiss = { showProfileSheet = false },
            onSignOut = {
                showProfileSheet = false
                vm.signOut()
                haptics.onPressDown()
            },
            onSave = { email, brandName, brandVoice, onFinished ->
                vm.saveUserProfile(email, brandName, brandVoice) { res ->
                    res.onSuccess {
                        onFinished(null)
                        haptics.onSuccess()
                        toastState.show("Creator profile saved!")
                        showProfileSheet = false
                    }.onFailure { err ->
                        onFinished(err.message ?: "Couldn't save your profile right now.")
                    }
                }
            },
        )
    }

    if (showQueueSheet) {
        OfflineQueueSheet(
            notes = pendingNotes,
            onDismiss = { showQueueSheet = false },
            onRetryAll = vm::retryOfflineQueue,
            onDiscard = vm::discardPendingNote,
        )
    }

    if (showIdeasSheet) {
        RecentIdeasSheet(
            ideas = ideas,
            haptics = haptics,
            onDismiss = { showIdeasSheet = false },
            onOpenContainer = { id ->
                showIdeasSheet = false
                onOpenContainer(id)
            },
            onDeleteIdea = { id ->
                vm.deleteContainer(id) {}
            },
        )
    }
}

/**
 * Crisp 1-bit pixel-art "Drafty" wordmark rendered with snapped integer pixel cells
 * to match the retro '{ Drafty }' Home screen header.
 */
@Composable
internal fun PixelDraftyWordmark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    // 33x9 crisp 1px-stroke retro pixel-art "Drafty" wordmark
    val rows = remember {
        arrayOf(
            "XXXX..............XX..X..........", // 0
            "X...X............X....X..........", // 1
            "X...X.X.XX...XXX.XXXX.XXXX.X...X.", // 2
            "X...X.XX..X....X..X....X...X...X.", // 3
            "X...X.X.....XXXX..X....X...X...X.", // 4
            "X...X.X....X...X..X....X.X.X...X.", // 5
            "XXXX..X.....XXXX..X.....X...XXXX.", // 6
            "...............................X.", // 7
            "............................XXX..", // 8
        )
    }
    val cols = rows[0].length
    val rowCount = rows.size
    val pixelSize = 4.6.dp
    Canvas(modifier.size(width = pixelSize * cols, height = pixelSize * rowCount)) {
        val px = size.width / cols
        for (r in 0 until rowCount) {
            val row = rows[r]
            for (c in 0 until cols) {
                if (row[c] == 'X') {
                    drawRect(
                        color = color,
                        topLeft = Offset(c * px, r * px),
                        size = Size(px + 0.4f, px + 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbAnchorRing(
    visible: Boolean,
    amplitudeFlow: StateFlow<Int>,
    isCancelZone: Boolean,
) {
    val ringAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "thumbAnchorRingAlpha",
    )
    if (!visible && ringAlpha <= 0.01f) return

    var rawLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(amplitudeFlow, visible) {
        if (!visible) {
            rawLevel = 0f
            return@LaunchedEffect
        }
        amplitudeFlow.collect { amp ->
            rawLevel = sqrt((amp / 32767f).coerceIn(0f, 1f))
        }
    }
    val pulseScale by animateFloatAsState(
        targetValue = 1f + 0.12f * rawLevel,
        label = "thumbAnchorPulse",
    )

    val ringBorderColor = if (isCancelZone) {
        Color(0xFFE53935).copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    val ringFillColor = if (isCancelZone) {
        Color(0x28E53935)
    } else {
        Color(0x52FFFFFF)
    }

    Surface(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                alpha = ringAlpha
                scaleX = pulseScale
                scaleY = pulseScale
            },
        shape = CircleShape,
        color = ringFillColor,
        border = BorderStroke(2.dp, ringBorderColor),
        shadowElevation = 0.dp,
    ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineQueueSheet(
    notes: List<PendingVoiceNoteEntity>,
    onDismiss: () -> Unit,
    onRetryAll: () -> Unit,
    onDiscard: (PendingVoiceNoteEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        shape = SheetTopShape,
        dragHandle = { SoftSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Offline Queue (${notes.size})", style = MaterialTheme.typography.headlineSmall, color = InkBlack)
                    Text(
                        "Voice memos cached locally while offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray,
                    )
                }
                if (notes.isNotEmpty()) {
                    GlassButton(
                        text = "Sync",
                        onClick = onRetryAll,
                        dark = true,
                        minHeight = 40.dp,
                        horizontalPadding = 14.dp,
                        leadingIcon = { PixelSyncIcon(tint = CardSurface, size = 12.dp) },
                    )
                }
            }
            if (notes.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(InnerTileShape)
                        .background(SoftWell)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "All voice memos are synced.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedGray,
                    )
                }
            } else {
                HorizontalDivider(color = SubtleDivider, thickness = 1.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notes.forEach { note ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(InnerTileShape)
                                .background(SoftWell)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SoftChip(
                                text = note.status.lowercase().replaceFirstChar { it.uppercase() },
                                fillColor = CardSurface,
                                dotColor = if (note.status == PendingVoiceNoteEntity.STATUS_SYNCING) StatusGreen else StatusRed,
                            )
                            Text(
                                text = note.lastError ?: note.clientTimestamp.substringBefore('T'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedGray,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                            )
                            Surface(
                                onClick = { onDiscard(note) },
                                shape = CircleShape,
                                color = CardSurface,
                            ) {
                                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                    PixelCloseIcon(tint = InkBlack, size = 10.dp)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(bottom = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentIdeasSheet(
    ideas: List<IdeaCard>,
    haptics: HapticChoreographer,
    onDismiss: () -> Unit,
    onOpenContainer: (String) -> Unit,
    onDeleteIdea: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val sheetToastState = rememberGlassToastState()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var pendingDeleteIds by remember { mutableStateOf(setOf<String>()) }
    val pendingDeleteJobs = remember { mutableMapOf<String, Job>() }

    // If the user dismisses the sheet while a delete undo timer is still active, commit it cleanly.
    DisposableEffect(Unit) {
        onDispose {
            val remaining = pendingDeleteIds.toList()
            pendingDeleteJobs.values.forEach { it.cancel() }
            pendingDeleteJobs.clear()
            remaining.forEach { id -> onDeleteIdea(id) }
        }
    }

    val visibleIdeas = remember(ideas, pendingDeleteIds) {
        ideas.filterNot { it.idea.id in pendingDeleteIds }
    }

    val filterOptions = remember(visibleIdeas) {
        val platforms = visibleIdeas
            .flatMap { it.idea.metadata.targetPlatforms }
            .map(::canonicalPlatform)
            .distinct()
            .take(6)
        listOf("All", "Synced", "Drafts") + platforms
    }

    val filteredIdeas = remember(visibleIdeas, query, selectedFilter) {
        val q = query.trim().lowercase()
        visibleIdeas.filter { card ->
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Synced" -> card.isPushed
                "Drafts" -> !card.isPushed
                else -> card.idea.metadata.targetPlatforms.any {
                    canonicalPlatform(it).equals(selectedFilter, ignoreCase = true)
                }
            }
            val matchesQuery = q.isEmpty() ||
                card.idea.title.lowercase().contains(q) ||
                card.idea.summary.lowercase().contains(q) ||
                card.idea.metadata.contentPillar.lowercase().contains(q) ||
                card.idea.metadata.targetPlatforms.any {
                    it.lowercase().contains(q) || canonicalPlatform(it).lowercase().contains(q)
                }
            matchesFilter && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppBackground,
        shape = SheetTopShape,
        dragHandle = { SoftSheetDragHandle() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .navigationBarsPadding(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent Ideas", style = MaterialTheme.typography.headlineSmall, color = InkBlack)
                }

                // Drafted-style monospace counter bar: TOTAL · SYNCED · DRAFTS
                if (visibleIdeas.isNotEmpty()) {
                    val syncedCount = remember(visibleIdeas) { visibleIdeas.count { it.isPushed } }
                    val draftCount = visibleIdeas.size - syncedCount
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftWell,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = "${visibleIdeas.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = InkBlack,
                                )
                                Text(
                                    text = "IDEAS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedGray,
                                )
                            }
                            Text("·", style = MaterialTheme.typography.labelMedium, color = MutedGray)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = "$syncedCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen,
                                )
                                Text(
                                    text = "SYNCED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedGray,
                                )
                            }
                            Text("·", style = MaterialTheme.typography.labelMedium, color = MutedGray)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = "$draftCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber,
                                )
                                Text(
                                    text = "DRAFTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedGray,
                                )
                            }
                        }
                    }
                }

                if (visibleIdeas.isNotEmpty()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search ideas, platforms, pillars…", color = MutedGray) },
                        singleLine = true,
                        shape = InnerTileShape,
                        colors = softFieldColors(),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                Surface(
                                    onClick = { query = "" },
                                    shape = CircleShape,
                                    color = CardSurface,
                                ) {
                                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                        PixelCloseIcon(tint = InkBlack, size = 9.dp)
                                    }
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filterOptions.forEach { option ->
                            val active = option == selectedFilter
                            Surface(
                                onClick = { selectedFilter = option },
                                shape = RoundedCornerShape(50),
                                color = if (active) InkBlack else CardSurface,
                                border = if (active) null else BorderStroke(1.dp, SubtleDivider),
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (active) CardSurface else InkBlack,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }

                if (visibleIdeas.isEmpty()) {
                    Box(Modifier.padding(24.dp)) {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Text("No ideas yet", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                            Text(
                                "Hold Drafty on the home screen and speak your idea.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedGray,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                } else if (filteredIdeas.isEmpty()) {
                    Box(Modifier.padding(24.dp)) {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Text("No matching ideas", style = MaterialTheme.typography.titleMedium, color = InkBlack)
                            Text(
                                "Try clearing your search or switching the filter chip.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedGray,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filteredIdeas, key = { it.idea.id }) { card ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        val targetId = card.idea.id
                                        haptics.onThresholdCross()
                                        pendingDeleteIds = pendingDeleteIds + targetId
                                        pendingDeleteJobs[targetId]?.cancel()
                                        pendingDeleteJobs[targetId] = scope.launch {
                                            delay(4100L)
                                            pendingDeleteJobs.remove(targetId)
                                            pendingDeleteIds = pendingDeleteIds - targetId
                                            onDeleteIdea(targetId)
                                        }
                                        sheetToastState.show(
                                            message = "Idea deleted",
                                            actionLabel = "Undo",
                                            onAction = {
                                                pendingDeleteJobs.remove(targetId)?.cancel()
                                                pendingDeleteIds = pendingDeleteIds - targetId
                                                haptics.onPressDown()
                                            },
                                        )
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CardShape)
                                            .background(Color(0xFFFFEBEB))
                                            .padding(horizontal = 22.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                text = "Delete",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFE53935),
                                            )
                                            PixelTrashIcon(tint = Color(0xFFE53935), size = 18.dp)
                                        }
                                    }
                                },
                            ) {
                                IdeaFeedCard(card, onClick = { onOpenContainer(card.idea.id) })
                            }
                        }
                    }
                }
            }

            GlassToastHost(
                state = sheetToastState,
                bottomPadding = 16.dp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorProfileSheet(
    profile: UserProfileState,
    authSession: AuthSession?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSave: (email: String, brandName: String, brandVoice: String, onFinished: (String?) -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accountEmail = authSession?.email?.ifBlank { null } ?: profile.email
    var brandName by remember(profile) { mutableStateOf(profile.brandName) }
    var brandVoice by remember(profile) { mutableStateOf(profile.brandVoicePrompt) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurface,
        shape = SheetTopShape,
        dragHandle = { SoftSheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Drafted Account",
                    style = MaterialTheme.typography.headlineSmall,
                    color = InkBlack,
                )
                SoftChip(
                    text = "Connected",
                    fillColor = Color(0xFFEAF7EE),
                    textColor = Color(0xFF157A42),
                    dotColor = StatusGreen,
                )
            }

            Text(
                "Signed in as $accountEmail. Your scheduled ideas sync directly to Drafted.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGray,
            )
            OutlinedTextField(
                value = brandName,
                onValueChange = {
                    brandName = it
                    errorMessage = null
                },
                label = { Text("Brand / Creator Name") },
                placeholder = { Text("e.g. Alex Creates") },
                singleLine = true,
                shape = InnerTileShape,
                modifier = Modifier.fillMaxWidth(),
                colors = softFieldColors(),
            )
            OutlinedTextField(
                value = brandVoice,
                onValueChange = {
                    brandVoice = it
                    errorMessage = null
                },
                label = { Text("Brand Voice Prompt (optional)") },
                placeholder = { Text("e.g. Energetic student tech creator, concise & witty") },
                minLines = 2,
                shape = InnerTileShape,
                modifier = Modifier.fillMaxWidth(),
                colors = softFieldColors(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassButton(
                    text = "Sign Out",
                    dark = false,
                    onClick = onSignOut,
                    modifier = Modifier.weight(0.42f),
                )
                GlassButton(
                    text = if (busy) "Saving…" else "Save Profile",
                    dark = true,
                    enabled = !busy && brandName.isNotBlank(),
                    onClick = {
                        busy = true
                        errorMessage = null
                        onSave(accountEmail, brandName, brandVoice) { err ->
                            busy = false
                            errorMessage = err
                        }
                    },
                    leadingIcon = if (!busy) {
                        { PixelCheckIcon(tint = CardSurface, size = 14.dp) }
                    } else null,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }
}

@Composable
private fun IdeaFeedCard(card: IdeaCard, onClick: () -> Unit) {
    val idea = card.idea
    val platforms = remember(idea.metadata.targetPlatforms) {
        idea.metadata.targetPlatforms.map(::canonicalPlatform).distinct().take(3)
    }
    val previewHook = remember(idea.assets.hookOptions, idea.summary) {
        idea.assets.hookOptions.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    SoftCard(Modifier.fillMaxWidth(), color = CardSurface, onClick = onClick) {
        // Drafted-style top metadata row: status + platforms on left, monospace slot on right
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.isPushed) {
                    SoftChip(
                        text = "Synced",
                        fillColor = Color(0xFFEAF7EE),
                        textColor = Color(0xFF157A42),
                        dotColor = StatusGreen,
                    )
                } else {
                    SoftChip(
                        text = "Draft",
                        fillColor = SoftWell,
                        textColor = MutedGray,
                        dotColor = InkBlack,
                    )
                }
                platforms.forEach { platform ->
                    PlatformLogoBadge(platform = platform, size = 20.dp)
                }
            }
            Text(
                text = if (card.isPushed) {
                    formatDate(idea.schedule.recommendedDate)
                } else {
                    formatRelativeCreatedDate(card.createdAt)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MutedGray,
            )
        }

        // Editorial bold title
        Text(
            idea.title,
            style = MaterialTheme.typography.titleLarge,
            color = InkBlack,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )

        // Drafted-style SoftWell preview box ("Hook: ...")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(InnerTileShape)
                .background(SoftWell)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = if (previewHook != null) "Hook: \"$previewHook\"" else idea.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatRelativeCreatedDate(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Today"
    val now = java.util.Calendar.getInstance()
    val created = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val sameYear = now.get(java.util.Calendar.YEAR) == created.get(java.util.Calendar.YEAR)
    val dayDiff = now.get(java.util.Calendar.DAY_OF_YEAR) - created.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today"
        sameYear && dayDiff == 1 -> "Yesterday"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date(epochMillis))
    }
}

@Composable
internal fun SoftSheetDragHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(SubtleDivider),
    )
}

@Composable
internal fun softFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SoftWell,
    unfocusedContainerColor = SoftWell,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = InkBlack,
)
