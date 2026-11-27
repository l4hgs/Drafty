package com.cs312.drafty.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cs312.drafty.ui.DraftyViewModel
import com.cs312.drafty.ui.components.DraftyMascot
import com.cs312.drafty.ui.components.GlassButton
import com.cs312.drafty.ui.components.GlassToastHost
import com.cs312.drafty.ui.components.MascotState
import com.cs312.drafty.ui.components.PixelCheckIcon
import com.cs312.drafty.ui.components.SoftCard
import com.cs312.drafty.ui.components.SoftChip
import com.cs312.drafty.ui.components.rememberGlassToastState
import com.cs312.drafty.ui.theme.AppBackground
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.InnerTileShape
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.SubtleDivider
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * Airy, minimal first-launch onboarding flow:
 * 1) { Drafty } hero welcome matching the Home screen
 * 2) LISTENING state — hold to talk, slide up to cancel
 * 3) THINKING state — structured Script, Creatives & Schedule
 * 4) Creator Profile setup (email, brand name, brand voice)
 */
@Composable
fun OnboardingScreen(
    vm: DraftyViewModel,
    onFinished: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastState = rememberGlassToastState()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    var email by rememberSaveable { mutableStateOf("") }
    var brandName by rememberSaveable { mutableStateOf("") }
    var brandVoice by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        onFinished()
    }

    fun finishWithMicPrompt() {
        val hasMic = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            onFinished()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val auraColor by animateColorAsState(
        targetValue = when (pagerState.currentPage) {
            0 -> Color(0xFFFF6BA9) // Pink — Welcome { Drafty }
            1 -> Color(0xFF38BDF8) // Blue — Hold & Listen
            2 -> Color(0xFF34D399) // Green — Structured Brief
            else -> Color(0xFFFF9652) // Warm Orange — Profile Setup
        },
        animationSpec = tween(420),
        label = "onboardingAura",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 560.dp
        // Subtle, airy ambient glow behind the bottom-center area
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val origin = Offset(w * 0.5f, h * 0.88f)
            val radius = h * 0.36f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.24f),
                        auraColor.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = origin,
                    radius = radius,
                ),
                radius = radius,
                center = origin,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Top bar: minimal Skip shortcut
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedGray,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(PAGE_COUNT - 1) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Spacer(Modifier.height(30.dp))
                }
            }

            // Swipeable pages with generous whitespace (adapts to side-by-side in landscape)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage(isLandscape = isLandscape)
                    1 -> OnboardingStatePage(
                        mascotState = MascotState.LISTENING,
                        stepLabel = "HOLD TO TALK",
                        title = "Speak your rough idea.",
                        description = "Press and hold Drafty to record. Slide up to cancel — even offline, your voice note is safely saved.",
                        chips = listOf("Hold to record", "Slide up to cancel"),
                        isLandscape = isLandscape,
                    )
                    2 -> OnboardingStatePage(
                        mascotState = MascotState.THINKING,
                        stepLabel = "STRUCTURED IN SECONDS",
                        title = "Ready to film.",
                        description = "Release Drafty to get 3 hooks, a script, shot list, and peak posting slot ready for Drafted.",
                        chips = listOf("Script", "Creatives", "Schedule"),
                        isLandscape = isLandscape,
                    )
                    else -> OnboardingProfilePage(
                        email = email,
                        onEmail = { email = it },
                        brandName = brandName,
                        onBrandName = { brandName = it },
                        brandVoice = brandVoice,
                        onBrandVoice = { brandVoice = it },
                        isLandscape = isLandscape,
                    )
                }
            }

            // Bottom progress indicator + clean CTA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = if (isLandscape) 12.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 20.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(PAGE_COUNT) { idx ->
                        val active = idx == pagerState.currentPage
                        val dotW by animateDpAsState(
                            targetValue = if (active) 22.dp else 7.dp,
                            label = "onboardingDotW",
                        )
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(dotW)
                                .clip(CircleShape)
                                .background(if (active) InkBlack else SubtleDivider),
                        )
                    }
                }

                val ctaModifier = if (isLandscape) Modifier.widthIn(max = 420.dp).fillMaxWidth() else Modifier.fillMaxWidth()
                if (pagerState.currentPage < PAGE_COUNT - 1) {
                    GlassButton(
                        text = if (pagerState.currentPage == PAGE_COUNT - 2) "Set Up Profile" else "Continue",
                        dark = true,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = ctaModifier,
                    )
                } else {
                    val canSave = email.isNotBlank() && brandName.isNotBlank() && !saving
                    GlassButton(
                        text = if (saving) "Setting up…" else "Get Started",
                        dark = true,
                        enabled = canSave,
                        leadingIcon = if (!saving) {
                            { PixelCheckIcon(tint = CardSurface, size = 14.dp) }
                        } else null,
                        onClick = {
                            saving = true
                            vm.saveUserProfile(email, brandName, brandVoice) { res ->
                                saving = false
                                res.onSuccess {
                                    finishWithMicPrompt()
                                }.onFailure {
                                    vm.saveProfileLocally(email, brandName, brandVoice)
                                    toastState.show("Profile saved on this device.")
                                    finishWithMicPrompt()
                                }
                            }
                        },
                        modifier = ctaModifier,
                    )
                    Text(
                        text = "Skip for now",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedGray,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = !saving) {
                                vm.completeOnboarding()
                                finishWithMicPrompt()
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }

        GlassToastHost(
            state = toastState,
            bottomPadding = 112.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Page 1: Ultra-minimal { Drafty } hero matching the Home screen aesthetic
 * with generous open whitespace.
 */
@Composable
private fun OnboardingWelcomePage(isLandscape: Boolean) {
    val content: @Composable () -> Unit = {
        Text(
            text = "welcome to",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            color = MutedGray,
        )
        Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Speak rough ideas.\nGet ready-to-film drafts.",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            color = MutedGray,
            textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center,
        )
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        ) {
            DraftyMascot(state = MascotState.IDLE, modifier = Modifier.size(84.dp))
            Column(horizontalAlignment = Alignment.Start) { content() }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DraftyMascot(state = MascotState.IDLE, modifier = Modifier.size(78.dp))
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
private fun OnboardingStatePage(
    mascotState: MascotState,
    stepLabel: String,
    title: String,
    description: String,
    chips: List<String>,
    isLandscape: Boolean,
) {
    val textBlock: @Composable (Alignment.Horizontal, TextAlign) -> Unit = { align, textAlign ->
        Column(horizontalAlignment = align) {
            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MutedGray,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isLandscape) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                color = InkBlack,
                textAlign = textAlign,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                color = MutedGray,
                textAlign = textAlign,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEach { label ->
                    SoftChip(text = label, fillColor = CardSurface)
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        ) {
            DraftyMascot(state = mascotState, modifier = Modifier.size(96.dp))
            Box(Modifier.widthIn(max = 420.dp)) {
                textBlock(Alignment.Start, TextAlign.Start)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DraftyMascot(state = mascotState, modifier = Modifier.size(104.dp))
            Spacer(Modifier.height(38.dp))
            textBlock(Alignment.CenterHorizontally, TextAlign.Center)
        }
    }
}

@Composable
private fun OnboardingProfilePage(
    email: String,
    onEmail: (String) -> Unit,
    brandName: String,
    onBrandName: (String) -> Unit,
    brandVoice: String,
    onBrandVoice: (String) -> Unit,
    isLandscape: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!isLandscape) {
            DraftyMascot(
                state = MascotState.CELEBRATING,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
        }
        Text(
            text = "Set up your voice",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (isLandscape) 22.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
            color = InkBlack,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tailor every hook and caption to your style.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (isLandscape) 14.dp else 28.dp))

        SoftCard(
            modifier = if (isLandscape) Modifier.widthIn(max = 520.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
            color = CardSurface,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmail,
                    label = { Text("Email address") },
                    placeholder = { Text("creator@drafty.app") },
                    singleLine = true,
                    shape = InnerTileShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = softFieldColors(),
                )
                OutlinedTextField(
                    value = brandName,
                    onValueChange = onBrandName,
                    label = { Text("Brand / Creator Name") },
                    placeholder = { Text("e.g. Alex Creates") },
                    singleLine = true,
                    shape = InnerTileShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = softFieldColors(),
                )
                OutlinedTextField(
                    value = brandVoice,
                    onValueChange = onBrandVoice,
                    label = { Text("Brand Voice (optional)") },
                    placeholder = { Text("e.g. Friendly storyteller, concise & witty") },
                    minLines = 2,
                    shape = InnerTileShape,
                    modifier = Modifier.fillMaxWidth(),
                    colors = softFieldColors(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Drafty needs mic access so you can hold to brain-dump anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
