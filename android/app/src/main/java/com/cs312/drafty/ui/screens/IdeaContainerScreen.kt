package com.cs312.drafty.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cs312.drafty.data.remote.IdeaContainer
import com.cs312.drafty.data.remote.ShotItem
import com.cs312.drafty.ui.DraftyViewModel
import com.cs312.drafty.ui.SyncState
import com.cs312.drafty.ui.components.DraftyMascot
import com.cs312.drafty.ui.components.GlassButton
import com.cs312.drafty.ui.components.GlassIconButton
import com.cs312.drafty.ui.components.GlassToastHost
import com.cs312.drafty.ui.components.GlassToastState
import com.cs312.drafty.ui.components.HapticChoreographer
import com.cs312.drafty.ui.components.MascotState
import com.cs312.drafty.ui.components.PixelBackIcon
import com.cs312.drafty.ui.components.PixelCheckIcon
import com.cs312.drafty.ui.components.PixelCopyIcon
import com.cs312.drafty.ui.components.PixelEditIcon
import com.cs312.drafty.ui.components.PixelSendIcon
import com.cs312.drafty.ui.components.PixelShareIcon
import com.cs312.drafty.ui.components.PixelSparkleIcon
import com.cs312.drafty.ui.components.PixelTrashIcon
import com.cs312.drafty.ui.components.PlatformLogoBadge
import com.cs312.drafty.ui.components.SoftCard
import com.cs312.drafty.ui.components.SoftChip
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
import com.cs312.drafty.ui.theme.PillShape
import com.cs312.drafty.ui.theme.ShadowAmbient
import com.cs312.drafty.ui.theme.ShadowSpot
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusGreen
import com.cs312.drafty.ui.theme.SubtleDivider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private val TabTitles = listOf("Script", "Creatives", "Schedule")

internal fun formatDate(iso: String, pattern: String = "MMM d"): String = try {
    SimpleDateFormat(pattern, Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!)
} catch (e: Exception) {
    iso
}

private fun formatTime(hhmm: String): String = try {
    SimpleDateFormat("h:mm a", Locale.US).format(SimpleDateFormat("HH:mm", Locale.US).parse(hhmm)!!)
} catch (e: Exception) {
    hhmm
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun formatScriptForCopy(idea: IdeaContainer, selectedHookIndex: Int = 0): String = buildString {
    appendLine(idea.title)
    appendLine()
    val hooks = idea.assets.hookOptions
    if (hooks.isNotEmpty()) {
        val primaryIdx = selectedHookIndex.coerceIn(0, hooks.lastIndex)
        appendLine("Primary Hook:")
        appendLine(hooks[primaryIdx])
        val others = hooks.filterIndexed { idx, _ -> idx != primaryIdx }
        if (others.isNotEmpty()) {
            appendLine()
            appendLine("Alternate Hooks:")
            others.forEachIndexed { idx, h -> appendLine("${idx + 1}. $h") }
        }
        appendLine()
    }
    appendLine(idea.assets.primaryCaption)
    if (idea.assets.cta.isNotBlank()) {
        appendLine()
        appendLine("CTA: ${idea.assets.cta}")
    }
    if (idea.assets.hashtags.isNotEmpty()) {
        appendLine()
        append(idea.assets.hashtags.joinToString(" "))
    }
}.trim()

private fun formatShotListForCopy(idea: IdeaContainer): String = buildString {
    appendLine("${idea.title} — Shot List (${idea.creativeDirection.visualType})")
    appendLine()
    idea.creativeDirection.shotList.forEachIndexed { idx, s ->
        appendLine("${idx + 1}. [${s.timestamp}] ${s.description}")
    }
    if (idea.creativeDirection.textOverlays.isNotEmpty()) {
        appendLine()
        appendLine("Text Overlays:")
        idea.creativeDirection.textOverlays.forEach { appendLine("• $it") }
    }
}.trim()

private fun formatScheduleSummary(idea: IdeaContainer, scheduledForOverride: String? = null): String {
    if (!scheduledForOverride.isNullOrBlank()) {
        val date = formatDate(scheduledForOverride.substringBefore('T'), "EEE, MMM d")
        val time = scheduledForOverride.substringAfter('T', "").takeIf { it.isNotBlank() }?.let(::formatTime)
        val slot = listOfNotNull(date, time).joinToString(" · ")
        if (slot.isNotBlank()) return "Scheduled for $slot"
    }
    val dateWithDay = formatDate(idea.schedule.recommendedDate, "EEE, MMM d")
    val timePart = formatTime(idea.schedule.recommendedTime)
    return "Scheduled for $dateWithDay · $timePart"
}

private fun openOrCopyForDrafted(
    ctx: Context,
    idea: IdeaContainer,
    selectedHookIndex: Int,
    haptics: HapticChoreographer,
    toastState: GlassToastState,
    scheduledForOverride: String? = null,
) {
    val pm = ctx.packageManager
    val candidatePackages = listOf("com.example.drafted", "com.cs312.drafted", "com.drafted.app", "app.drafted")
    val launchIntent = candidatePackages.firstNotNullOfOrNull { pkg ->
        pm.getLaunchIntentForPackage(pkg)
    } ?: Intent(Intent.ACTION_VIEW, Uri.parse("drafted://calendar")).takeIf { intent ->
        intent.resolveActivity(pm) != null
    }

    if (launchIntent != null) {
        haptics.onSuccess()
        ctx.startActivity(launchIntent)
        return
    }

    val summaryText = buildString {
        appendLine(formatScheduleSummary(idea, scheduledForOverride) + " in Drafted")
        appendLine()
        append(formatScriptForCopy(idea, selectedHookIndex))
    }.trim()
    copyToClipboard(ctx, "Drafted Schedule Summary", summaryText)
    haptics.onSuccess()
    toastState.show("Copied schedule summary for Drafted!")
}

private fun shareIdeaBrief(ctx: Context, idea: IdeaContainer, selectedHookIndex: Int = 0) {
    val body = buildString {
        appendLine(idea.title)
        appendLine(idea.summary)
        appendLine()
        appendLine(formatScriptForCopy(idea, selectedHookIndex))
        appendLine()
        appendLine(formatShotListForCopy(idea))
        appendLine()
        append("Recommended Schedule: ${idea.schedule.recommendedDay}, ${formatDate(idea.schedule.recommendedDate)} at ${formatTime(idea.schedule.recommendedTime)}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, idea.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share Drafty Brief"))
}

/** Screen C: Apple borderless soft UI cards + monochrome pixel action buttons. */
@Composable
fun IdeaContainerScreen(id: String, vm: DraftyViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastState = rememberGlassToastState()
    val haptics = rememberHapticChoreographer()
    val card by remember(id) { vm.observeIdea(id) }.collectAsState(initial = null)
    val sync by vm.syncState.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { TabTitles.size })
    var selectedHookIndex by rememberSaveable(id) { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var regeneratingHooks by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }
    var draftCaption by remember { mutableStateOf("") }
    var draftCta by remember { mutableStateOf("") }
    var draftShots by remember { mutableStateOf<List<ShotItem>>(emptyList()) }

    LaunchedEffect(sync) {
        when (val s = sync) {
            is SyncState.Error -> {
                toastState.show(s.message)
                vm.resetSync()
            }
            is SyncState.Synced -> haptics.onSuccess()
            else -> Unit
        }
    }
    DisposableEffect(Unit) { onDispose { vm.resetSync() } }

    val current = card
    val activeTab = pagerState.currentPage
    val patchPrimary by animateColorAsState(
        targetValue = when (activeTab) {
            0 -> Color(0xFF38BDF8) // Script — Blue
            1 -> Color(0xFFF43F5E) // Creatives — Red
            else -> Color(0xFF34D399) // Schedule — Green
        },
        animationSpec = tween(420),
        label = "tabPatchPrimary",
    )
    val patchSecondary by animateColorAsState(
        targetValue = when (activeTab) {
            0 -> Color(0xFF60A5FA) // Script — Soft Sky Blue
            1 -> Color(0xFFFB7185) // Creatives — Soft Rose Red
            else -> Color(0xFF4ADE80) // Schedule — Soft Mint Green
        },
        animationSpec = tween(420),
        label = "tabPatchSecondary",
    )

    val tabAccent by animateColorAsState(
        targetValue = when (activeTab) {
            0 -> Color(0xFF3B6790) // Script — Muted Steel Blue
            1 -> Color(0xFF9B4A55) // Creatives — Muted Dusty Rose
            else -> Color(0xFF3B6E57) // Schedule — Muted Forest Sage
        },
        animationSpec = tween(380),
        label = "tabAccent",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(AppBackground)) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp

        // Richer soft ambient color patches behind the container cards (Blue / Red / Green per tab)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Top-right soft color patch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        patchPrimary.copy(alpha = 0.34f),
                        patchPrimary.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.86f, h * 0.14f),
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = Offset(w * 0.86f, h * 0.14f),
            )
            // Mid-left soft color patch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        patchSecondary.copy(alpha = 0.30f),
                        patchSecondary.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.10f, h * 0.56f),
                    radius = w * 0.68f,
                ),
                radius = w * 0.68f,
                center = Offset(w * 0.10f, h * 0.56f),
            )
            // Bottom-right soft color patch behind the floating glass dock
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        patchPrimary.copy(alpha = 0.36f),
                        patchSecondary.copy(alpha = 0.14f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.82f, h * 0.92f),
                    radius = w * 0.76f,
                ),
                radius = w * 0.76f,
                center = Offset(w * 0.82f, h * 0.92f),
            )
        }

        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = InkBlack)
        } else {
            val idea = current.idea
            val synced = current.isPushed || sync is SyncState.Synced
            val safeHookIndex = selectedHookIndex.coerceIn(0, (idea.assets.hookOptions.size - 1).coerceAtLeast(0))

            val pagerContent: @Composable () -> Unit = {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    pageSpacing = 14.dp,
                ) { page ->
                    when (page) {
                        0 -> ScriptTab(
                            idea = idea,
                            editing = editing,
                            selectedHookIndex = safeHookIndex,
                            onSelectHook = { idx ->
                                selectedHookIndex = idx
                                haptics.onThresholdCross()
                            },
                            caption = draftCaption,
                            onCaption = { draftCaption = it },
                            cta = draftCta,
                            onCta = { draftCta = it },
                            regeneratingHooks = regeneratingHooks,
                            onRegenerateHooks = {
                                if (!regeneratingHooks) {
                                    regeneratingHooks = true
                                    vm.regenerateHooks(idea) { res ->
                                        regeneratingHooks = false
                                        res.onSuccess {
                                            selectedHookIndex = 0
                                            haptics.onSuccess()
                                            toastState.show("3 fresh hooks generated!")
                                        }.onFailure { err ->
                                            toastState.show(err.message ?: "Couldn't generate new hooks right now.")
                                        }
                                    }
                                }
                            },
                            onCopyScript = {
                                copyToClipboard(ctx, "Drafty Script", formatScriptForCopy(idea, safeHookIndex))
                                haptics.onPressDown()
                                toastState.show(
                                    if (idea.assets.hookOptions.isNotEmpty()) {
                                        "Copied with primary hook!"
                                    } else {
                                        "Script & hashtags copied!"
                                    },
                                )
                            },
                        )
                        1 -> CreativesTab(
                            idea = idea,
                            editing = editing,
                            draftShots = draftShots,
                            onShotDescriptionChange = { index, newDesc ->
                                draftShots = draftShots.mapIndexed { idx, item ->
                                    if (idx == index) item.copy(description = newDesc) else item
                                }
                            },
                            onCopyShotList = {
                                copyToClipboard(ctx, "Drafty Shot List", formatShotListForCopy(idea))
                                haptics.onPressDown()
                                toastState.show("Shot list copied!")
                            },
                        )
                        else -> ScheduleTab(
                            idea = idea,
                            isSynced = synced,
                            scheduledForOverride = (sync as? SyncState.Synced)?.scheduledFor,
                            onUpdateSchedule = { newDate, newDay, newTime ->
                                vm.updateContainer(
                                    idea.copy(
                                        schedule = idea.schedule.copy(
                                            recommendedDate = newDate,
                                            recommendedDay = newDay,
                                            recommendedTime = newTime,
                                        ),
                                    ),
                                )
                            },
                            onViewInDrafted = {
                                openOrCopyForDrafted(
                                    ctx = ctx,
                                    idea = idea,
                                    selectedHookIndex = safeHookIndex,
                                    haptics = haptics,
                                    toastState = toastState,
                                    scheduledForOverride = (sync as? SyncState.Synced)?.scheduledFor,
                                )
                            },
                        )
                    }
                }
            }

            if (isLandscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 68.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TitleCard(idea, editing, draftTitle) { draftTitle = it }
                        TabRow(
                            selected = pagerState.currentPage,
                            pageOffsetFraction = pagerState.currentPageOffsetFraction,
                            activeColor = tabAccent,
                            onSelect = { target ->
                                haptics.onThresholdCross()
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                        )
                    }
                    Column(
                        Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        pagerContent()
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 116.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    TitleCard(idea, editing, draftTitle) { draftTitle = it }
                    TabRow(
                        selected = pagerState.currentPage,
                        pageOffsetFraction = pagerState.currentPageOffsetFraction,
                        activeColor = tabAccent,
                        onSelect = { target ->
                            haptics.onThresholdCross()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                    )
                    pagerContent()
                }
            }

            // Soft bokeh frosted diffusion veil behind the pinned top buttons
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(if (isLandscape) 80.dp else 112.dp)
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(14.dp) else Modifier)
                    .background(
                        Brush.verticalGradient(
                            0.0f to AppBackground.copy(alpha = 0.94f),
                            0.58f to AppBackground.copy(alpha = 0.72f),
                            1.0f to Color.Transparent,
                        ),
                    ),
            )

            // Pinned floating glassmorphic top bar (Back, Editing pill, Share & Delete)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                GlassIconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    PixelBackIcon(tint = InkBlack, size = 16.dp)
                }

                AnimatedVisibility(
                    visible = editing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Surface(
                        shape = PillShape,
                        color = Color(0xEBFFFFFF),
                        border = BorderStroke(1.dp, Color.White),
                        shadowElevation = 0.dp,
                        modifier = Modifier.shadow(6.dp, PillShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PixelEditIcon(tint = InkBlack, size = 11.dp)
                            Text(
                                text = "Editing",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = InkBlack,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassIconButton(
                        onClick = { shareIdeaBrief(ctx, idea, safeHookIndex) },
                    ) {
                        PixelShareIcon(tint = InkBlack, size = 17.dp)
                    }
                    GlassIconButton(onClick = { showDeleteDialog = true }) {
                        PixelTrashIcon(tint = InkBlack, size = 18.dp)
                    }
                }
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    shape = CardShape,
                    containerColor = CardSurface,
                    title = {
                        Text("Delete this idea?", style = MaterialTheme.typography.titleLarge, color = InkBlack)
                    },
                    text = {
                        Text(
                            "This will remove \"${idea.title}\" from your device and cloud workspace.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedGray,
                        )
                    },
                    confirmButton = {
                        GlassButton(
                            text = "Delete",
                            onClick = {
                                showDeleteDialog = false
                                vm.deleteContainer(idea.id) { onBack() }
                            },
                            destructive = true,
                            minHeight = 40.dp,
                            horizontalPadding = 16.dp,
                        )
                    },
                    dismissButton = {
                        GlassButton(
                            text = "Cancel",
                            onClick = { showDeleteDialog = false },
                            dark = false,
                            minHeight = 40.dp,
                            horizontalPadding = 14.dp,
                        )
                    },
                )
            }

            // Soft bokeh frosted diffusion veil behind the floating bottom Dock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (isLandscape) 96.dp else 136.dp)
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(16.dp) else Modifier)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.38f to AppBackground.copy(alpha = 0.74f),
                            0.85f to AppBackground.copy(alpha = 0.94f),
                            1.0f to AppBackground,
                        ),
                    ),
            )

            Dock(
                editing = editing,
                synced = synced,
                syncing = sync is SyncState.Syncing,
                onEdit = {
                    if (editing) {
                        vm.updateContainer(
                            idea.copy(
                                title = draftTitle.ifBlank { idea.title },
                                assets = idea.assets.copy(primaryCaption = draftCaption, cta = draftCta),
                                creativeDirection = idea.creativeDirection.copy(
                                    shotList = draftShots.ifEmpty { idea.creativeDirection.shotList },
                                ),
                            ),
                        )
                        editing = false
                        haptics.onSuccess()
                        toastState.show("Changes saved!")
                    } else {
                        draftTitle = idea.title
                        draftCaption = idea.assets.primaryCaption
                        draftCta = idea.assets.cta
                        draftShots = idea.creativeDirection.shotList
                        editing = true
                        haptics.onPressDown()
                    }
                },
                onCancelEdit = {
                    draftTitle = idea.title
                    draftCaption = idea.assets.primaryCaption
                    draftCta = idea.assets.cta
                    draftShots = idea.creativeDirection.shotList
                    editing = false
                    haptics.onPressDown()
                },
                onSchedule = { vm.scheduleToDrafted(idea.id, safeHookIndex) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isLandscape) Modifier.widthIn(max = 520.dp) else Modifier),
            )

            (sync as? SyncState.Synced)?.let { syncedState ->
                CelebrationOverlay(
                    idea = idea,
                    scheduledFor = syncedState.scheduledFor,
                    onViewInDrafted = {
                        openOrCopyForDrafted(
                            ctx = ctx,
                            idea = idea,
                            selectedHookIndex = safeHookIndex,
                            haptics = haptics,
                            toastState = toastState,
                            scheduledForOverride = syncedState.scheduledFor,
                        )
                    },
                    onDone = vm::resetSync,
                )
            }
        }

        GlassToastHost(
            state = toastState,
            bottomPadding = 104.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TitleCard(idea: IdeaContainer, editing: Boolean, draftTitle: String, onTitle: (String) -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        // Drafted-style metadata top row: platform badges + pillar on left, monospace slot on right
        val platforms = remember(idea.metadata.targetPlatforms) {
            idea.metadata.targetPlatforms.map(::canonicalPlatform).distinct()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                platforms.forEach { PlatformLogoBadge(platform = it, size = 22.dp) }
                SoftChip(
                    text = idea.metadata.contentPillar,
                    fillColor = SoftWell,
                    dotColor = InkBlack,
                    textColor = InkBlack,
                )
            }
            Text(
                text = formatTime(idea.schedule.recommendedTime),
                style = MaterialTheme.typography.labelMedium,
                color = MutedGray,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (editing) {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = onTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                shape = InnerTileShape,
                colors = softFieldColors(),
            )
        } else {
            Text(idea.title, style = MaterialTheme.typography.headlineSmall, color = InkBlack)
        }
        Text(
            idea.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TabRow(
    selected: Int,
    pageOffsetFraction: Float,
    activeColor: Color,
    onSelect: (Int) -> Unit,
) {
    // Drafted-style segmented pill control: SoftWell capsule track with solid jet-black active pill
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, PillShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
            .clip(PillShape)
            .background(Color(0xF0FFFFFF))
            .border(1.dp, SubtleDivider, PillShape)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / TabTitles.size
        val indicatorPosition = (selected + pageOffsetFraction).coerceIn(0f, (TabTitles.size - 1).toFloat())

        // Sliding solid jet-black indicator pill synchronized with HorizontalPager
        Box(
            Modifier
                .offset(x = tabWidth * indicatorPosition)
                .width(tabWidth)
                .height(38.dp)
                .clip(PillShape)
                .background(InkBlack),
        )

        Row(Modifier.fillMaxWidth()) {
            TabTitles.forEachIndexed { i, title ->
                val isSel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(PillShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSel) CardSurface else MutedGray,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        color = MutedGray,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ScriptTab(
    idea: IdeaContainer,
    editing: Boolean,
    selectedHookIndex: Int,
    onSelectHook: (Int) -> Unit,
    caption: String,
    onCaption: (String) -> Unit,
    cta: String,
    onCta: (String) -> Unit,
    regeneratingHooks: Boolean,
    onRegenerateHooks: () -> Unit,
    onCopyScript: () -> Unit,
) {
    val scriptAccent = Color(0xFF3B6790)
    val scriptPill = Color(0xFFDFE8F0)

    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HOOK OPTIONS", style = MaterialTheme.typography.labelSmall, color = MutedGray)
            Surface(
                onClick = onRegenerateHooks,
                enabled = !regeneratingHooks,
                shape = PillShape,
                color = scriptPill,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (regeneratingHooks) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.6.dp, color = scriptAccent)
                    } else {
                        PixelSparkleIcon(tint = scriptAccent, size = 12.dp)
                    }
                    Text(
                        text = if (regeneratingHooks) "Refreshing…" else "Regenerate",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scriptAccent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            idea.assets.hookOptions.forEachIndexed { i, hook ->
                val isSelected = i == selectedHookIndex
                Surface(
                    onClick = { onSelectHook(i) },
                    shape = InnerTileShape,
                    color = if (isSelected) CardSurface else SoftWell,
                    border = if (isSelected) BorderStroke(1.5.dp, InkBlack) else null,
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .springPressScale(0.98f),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isSelected) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = PillShape,
                                    color = InkBlack,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        PixelCheckIcon(tint = CardSurface, size = 9.dp)
                                        Text(
                                            text = "Primary",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CardSurface,
                                        )
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                "${i + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) InkBlack else scriptAccent,
                                modifier = Modifier.padding(end = 10.dp, top = 1.dp),
                            )
                            Text(
                                hook,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = InkBlack,
                            )
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CAPTION / SCRIPT", style = MaterialTheme.typography.labelSmall, color = MutedGray)
            Surface(
                onClick = onCopyScript,
                shape = PillShape,
                color = SoftWell,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    PixelCopyIcon(tint = InkBlack, size = 12.dp)
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = InkBlack,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (editing) {
            OutlinedTextField(
                value = caption,
                onValueChange = onCaption,
                modifier = Modifier.fillMaxWidth(),
                shape = InnerTileShape,
                colors = softFieldColors(),
                minLines = 5,
            )
        } else {
            Text(idea.assets.primaryCaption, style = MaterialTheme.typography.bodyLarge, color = InkBlack)
        }

        HorizontalDivider(Modifier.padding(top = 14.dp), color = SubtleDivider, thickness = 1.dp)
        SectionLabel("Call to Action")
        if (editing) {
            OutlinedTextField(
                value = cta,
                onValueChange = onCta,
                modifier = Modifier.fillMaxWidth(),
                shape = InnerTileShape,
                colors = softFieldColors(),
            )
        } else {
            Text(
                idea.assets.cta,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = InkBlack,
            )
        }
        if (idea.assets.hashtags.isNotEmpty()) {
            Text(
                idea.assets.hashtags.joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = scriptAccent,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun CreativesTab(
    idea: IdeaContainer,
    editing: Boolean,
    draftShots: List<ShotItem>,
    onShotDescriptionChange: (Int, String) -> Unit,
    onCopyShotList: () -> Unit,
) {
    val cd = idea.creativeDirection
    val creativeAccent = Color(0xFF9B4A55)
    val creativeWell = Color(0xFFF3EAEB)
    val creativePill = Color(0xFFEBDDDF)
    val shotsToDisplay = if (editing && draftShots.isNotEmpty()) draftShots else cd.shotList

    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        Text("Visual Style", style = MaterialTheme.typography.labelMedium, color = MutedGray)
        SoftChip(
            text = cd.visualType,
            fillColor = creativePill,
            textColor = creativeAccent,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shot List", style = MaterialTheme.typography.labelMedium, color = MutedGray)
            Surface(
                onClick = onCopyShotList,
                shape = PillShape,
                color = SoftWell,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    PixelCopyIcon(tint = InkBlack, size = 12.dp)
                    Text(
                        text = "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = InkBlack,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shotsToDisplay.forEachIndexed { i, shot ->
                Surface(
                    shape = InnerTileShape,
                    color = creativeWell,
                    border = null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(24.dp).background(creativeAccent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = CardSurface)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(shot.timestamp, style = MaterialTheme.typography.titleSmall, color = InkBlack)
                            if (editing) {
                                OutlinedTextField(
                                    value = shot.description,
                                    onValueChange = { onShotDescriptionChange(i, it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    shape = InnerTileShape,
                                    colors = softFieldColors(),
                                    minLines = 2,
                                )
                            } else {
                                Text(
                                    shot.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedGray,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (cd.textOverlays.isNotEmpty()) {
            SectionLabel("Text Overlays")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cd.textOverlays.forEach { overlay ->
                    Text("•  $overlay", style = MaterialTheme.typography.bodyMedium, color = InkBlack)
                }
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    idea: IdeaContainer,
    isSynced: Boolean,
    scheduledForOverride: String?,
    onUpdateSchedule: (date: String, day: String, time: String) -> Unit,
    onViewInDrafted: () -> Unit,
) {
    val ctx = LocalContext.current
    val s = idea.schedule

    val openDatePicker = {
        val cal = Calendar.getInstance()
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.recommendedDate)?.let { cal.time = it }
        }
        DatePickerDialog(
            ctx,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(picked.time)
                val weekday = SimpleDateFormat("EEEE", Locale.US).format(picked.time)
                onUpdateSchedule(isoDate, weekday, s.recommendedTime)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    val openTimePicker = {
        val parts = s.recommendedTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull() ?: 18
        val initMin = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            ctx,
            { _, hourOfDay, minute ->
                val hhmm = "%02d:%02d".format(Locale.US, hourOfDay, minute)
                onUpdateSchedule(s.recommendedDate, s.recommendedDay, hhmm)
            },
            initHour,
            initMin,
            false,
        ).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isSynced) {
            DraftedHandoffCard(
                idea = idea,
                scheduledForOverride = scheduledForOverride,
                onViewInDrafted = onViewInDrafted,
            )
        }

        SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoTile(
                    label = "Date",
                    value = formatDate(s.recommendedDate, "MMM d"),
                    editable = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = openDatePicker,
                )
                InfoTile(
                    label = "Day",
                    value = formatDate(s.recommendedDate, "EEE").takeIf { it != s.recommendedDate }
                        ?: s.recommendedDay.take(3),
                    editable = false,
                    modifier = Modifier.weight(0.85f).fillMaxHeight(),
                )
                InfoTile(
                    label = "Time",
                    value = formatTime(s.recommendedTime),
                    editable = true,
                    modifier = Modifier.weight(1.1f).fillMaxHeight(),
                    onClick = openTimePicker,
                )
            }
            Text(
                "Tap Date or Time to customize your posting slot.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedGray,
                modifier = Modifier.padding(top = 10.dp),
            )
            HorizontalDivider(Modifier.padding(top = 12.dp), color = SubtleDivider, thickness = 1.dp)
            SectionLabel("Why this slot")
            Text(s.peakWindowReason, style = MaterialTheme.typography.bodyMedium, color = InkBlack)
        }
    }
}

@Composable
private fun DraftedHandoffCard(
    idea: IdeaContainer,
    scheduledForOverride: String?,
    onViewInDrafted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassBrush = Brush.linearGradient(
        listOf(Color(0xF2FFFFFF), Color(0xD9FFFFFF), Color(0xEBFFFFFF)),
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, InnerTileShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot),
        shape = InnerTileShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .clip(InnerTileShape)
                .background(glassBrush)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x2234C759)),
                    contentAlignment = Alignment.Center,
                ) {
                    PixelCheckIcon(tint = StatusGreen, size = 12.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Ready in Drafted",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = InkBlack,
                    )
                    Text(
                        text = formatScheduleSummary(idea, scheduledForOverride),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedGray,
                    )
                }
            }
            Surface(
                onClick = onViewInDrafted,
                shape = PillShape,
                color = InkBlack,
                modifier = Modifier
                    .fillMaxWidth()
                    .springPressScale(0.97f),
            ) {
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "View in Drafted →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CardSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTile(
    label: String,
    value: String,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val scheduleWell = Color(0xFFE9F0EC)
    val scheduleAccent = Color(0xFF3B6E57)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = scheduleAccent)
                if (editable) PixelEditIcon(tint = scheduleAccent, size = 10.dp)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = InkBlack,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = InnerTileShape,
            color = scheduleWell,
            border = null,
            modifier = modifier,
        ) { body() }
    } else {
        Surface(
            shape = InnerTileShape,
            color = scheduleWell,
            border = null,
            modifier = modifier,
        ) { body() }
    }
}

@Composable
private fun Dock(
    editing: Boolean,
    synced: Boolean,
    syncing: Boolean,
    onEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockShape = PillShape
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, dockShape, ambientColor = ShadowAmbient, spotColor = ShadowSpot),
        shape = dockShape,
        color = Color(0xF7FFFFFF),
        border = BorderStroke(1.dp, SubtleDivider),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .clip(dockShape)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (editing) {
                GlassButton(
                    text = "Cancel",
                    onClick = onCancelEdit,
                    modifier = Modifier.weight(1f),
                    dark = false,
                    minHeight = 48.dp,
                    horizontalPadding = 16.dp,
                )
                GlassButton(
                    text = "Save",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    dark = true,
                    minHeight = 48.dp,
                    horizontalPadding = 16.dp,
                    leadingIcon = { PixelCheckIcon(tint = CardSurface, size = 14.dp) },
                )
            } else {
                GlassButton(
                    text = "Edit",
                    onClick = onEdit,
                    dark = false,
                    minHeight = 48.dp,
                    horizontalPadding = 16.dp,
                    leadingIcon = { PixelEditIcon(tint = InkBlack, size = 14.dp) },
                )
                GlassButton(
                    text = when {
                        synced -> "Scheduled"
                        syncing -> "Syncing…"
                        else -> "Schedule to Drafted"
                    },
                    onClick = onSchedule,
                    modifier = Modifier.weight(1f),
                    dark = !synced,
                    enabled = !synced && !syncing,
                    minHeight = 48.dp,
                    horizontalPadding = 16.dp,
                    leadingIcon = {
                        when {
                            synced -> PixelCheckIcon(tint = InkBlack, size = 14.dp)
                            !syncing -> PixelSendIcon(tint = CardSurface, size = 14.dp)
                        }
                    },
                )
            }
        }
    }
}

private class PixelConfetti(val x: Float, val delay: Float, val speed: Float, val color: Color, val sizePx: Float)

@Composable
private fun CelebrationOverlay(
    idea: IdeaContainer,
    scheduledFor: String?,
    onViewInDrafted: () -> Unit,
    onDone: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val palette = listOf(InkBlack, MutedGray, SubtleDivider, StatusGreen)
    val pieces = remember {
        List(44) {
            PixelConfetti(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.4f,
                speed = 0.6f + Random.nextFloat() * 0.6f,
                color = palette.random(),
                sizePx = listOf(8f, 12f, 16f).random(),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        SoftCard(Modifier.padding(28.dp).fillMaxWidth(), color = CardSurface) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                DraftyMascot(MascotState.CELEBRATING, Modifier.size(168.dp))
                Text(
                    "Scheduled to Drafted!",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkBlack,
                    textAlign = TextAlign.Center,
                )
                DraftedHandoffCard(
                    idea = idea,
                    scheduledForOverride = scheduledFor,
                    onViewInDrafted = onViewInDrafted,
                    modifier = Modifier.padding(top = 14.dp),
                )
                GlassButton(
                    text = "Done",
                    onClick = onDone,
                    dark = false,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            pieces.forEach { p ->
                val t = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (t > 0f && t < 1f) {
                    val x = floor(p.x * size.width + sin(t * 8f + p.x * 10f) * 20f)
                    val y = floor(-20f + t * p.speed * size.height * 1.3f)
                    drawRect(p.color, Offset(x, y), Size(p.sizePx, p.sizePx))
                }
            }
        }
    }
}
