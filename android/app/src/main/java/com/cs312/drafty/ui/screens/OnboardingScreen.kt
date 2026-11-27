package com.cs312.drafty.ui.screens

import android.Manifest
import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.cs312.drafty.core.auth.EmailConfirmationRequiredException
import com.cs312.drafty.ui.DraftyViewModel
import com.cs312.drafty.ui.components.DraftyMascot
import com.cs312.drafty.ui.components.GlassButton
import com.cs312.drafty.ui.components.GlassToastHost
import com.cs312.drafty.ui.components.MascotState
import com.cs312.drafty.ui.components.PixelCheckIcon
import com.cs312.drafty.ui.components.PixelEyeIcon
import com.cs312.drafty.ui.components.PixelEyeOffIcon
import com.cs312.drafty.ui.components.SoftCard
import com.cs312.drafty.ui.components.SoftChip
import com.cs312.drafty.ui.components.rememberGlassToastState
import com.cs312.drafty.ui.theme.AppBackground
import com.cs312.drafty.ui.theme.CardShape
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.InnerTileShape
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusGreen
import com.cs312.drafty.ui.theme.SubtleDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 5

/**
 * Airy, minimal 5-step first-launch onboarding flow:
 * 1) { Drafty } hero welcome with larger animated mascot
 * 2) LISTENING state — hold to talk, slide up to cancel
 * 3) THINKING state — structured Script, Creatives & Schedule
 * 4) Unified Account (Sign In / Create Account + Continue with Google for Drafted sync)
 * 5) Creator Voice setup (Brand Name + optional Brand Voice + Mic prompt)
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
    val authSession by vm.authSession.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isSignUpMode by rememberSaveable { mutableStateOf(true) }
    var brandName by rememberSaveable { mutableStateOf("") }
    var brandVoice by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingVerifyEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var checkingVerify by remember { mutableStateOf(false) }
    var resendingEmail by remember { mutableStateOf(false) }

    // Smoothly advance to Page 5 (Voice Setup) once authenticated (via Google OAuth or email verification)
    LaunchedEffect(authSession) {
        val session = authSession ?: return@LaunchedEffect
        if (pendingVerifyEmail != null) {
            pendingVerifyEmail = null
        }
        if (brandName.isBlank()) {
            brandName = session.displayName
        }
        if (pagerState.currentPage == 3) {
            delay(180L)
            pagerState.animateScrollToPage(4)
        }
    }

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
            3 -> Color(0xFF818CF8) // Soft Indigo — Unified Account
            else -> Color(0xFFFF9652) // Warm Orange — Voice Setup
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
            // Top bar: minimal Skip shortcut (jumps to Account step on intro pages)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage < 3) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedGray,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(3) }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                } else {
                    Spacer(Modifier.height(30.dp))
                }
            }

            // Swipeable pages with generous whitespace (blocks swiping past Account page until signed in)
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = pagerState.currentPage < 3 || authSession != null,
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
                    3 -> OnboardingAccountPage(
                        email = email,
                        onEmail = { email = it },
                        password = password,
                        onPassword = { password = it },
                        isSignUpMode = isSignUpMode,
                        onToggleMode = { isSignUpMode = it },
                        isSignedIn = authSession != null,
                        signedInEmail = authSession?.email.orEmpty(),
                        busy = busy,
                        onContinueWithGoogle = {
                            if (!vm.isSupabaseAuthConfigured) {
                                toastState.show("Add Supabase keys to android/local.properties first.")
                            } else {
                                val uri = vm.authManager.buildGoogleOAuthUri()
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        },
                        isLandscape = isLandscape,
                    )
                    else -> OnboardingVoicePage(
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
                    .padding(horizontal = 28.dp, vertical = if (isLandscape) 12.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 16.dp),
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
                when (pagerState.currentPage) {
                    0, 1, 2 -> {
                        GlassButton(
                            text = if (pagerState.currentPage == 2) "Connect Account" else "Continue",
                            dark = true,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = ctaModifier,
                        )
                    }
                    3 -> {
                        if (authSession != null) {
                            GlassButton(
                                text = "Next: Set Up Voice",
                                dark = true,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(4) }
                                },
                                modifier = ctaModifier,
                            )
                        } else {
                            val canAuth = !busy && email.isNotBlank() && password.length >= 6
                            GlassButton(
                                text = when {
                                    busy -> "Please wait…"
                                    isSignUpMode -> "Create Account & Continue"
                                    else -> "Sign In"
                                },
                                dark = true,
                                enabled = canAuth,
                                onClick = {
                                    busy = true
                                    if (isSignUpMode) {
                                        val fallbackName = email.substringBefore("@").ifBlank { "Creator" }
                                        vm.signUpWithEmail(email, password, fallbackName, "") { res ->
                                            busy = false
                                            res.onSuccess {
                                                brandName = it.displayName
                                                toastState.show("Account created! Personalize your voice next.")
                                                scope.launch { pagerState.animateScrollToPage(4) }
                                            }.onFailure { err ->
                                                if (err is EmailConfirmationRequiredException) {
                                                    pendingVerifyEmail = err.email.ifBlank { email.trim() }
                                                } else {
                                                    toastState.show(err.message ?: "Couldn't create account.")
                                                }
                                            }
                                        }
                                    } else {
                                        vm.signInWithEmail(email, password) { res ->
                                            busy = false
                                            res.onSuccess {
                                                toastState.show("Welcome back!")
                                                finishWithMicPrompt()
                                            }.onFailure { err ->
                                                toastState.show(err.message ?: "Couldn't sign in.")
                                            }
                                        }
                                    }
                                },
                                modifier = ctaModifier,
                            )
                        }
                    }
                    else -> {
                        val effectiveEmail = authSession?.email?.ifBlank { null } ?: email.trim()
                        val canSave = !busy && brandName.isNotBlank()
                        GlassButton(
                            text = if (busy) "Setting up…" else "Get Started",
                            dark = true,
                            enabled = canSave,
                            leadingIcon = if (!busy) {
                                { PixelCheckIcon(tint = CardSurface, size = 14.dp) }
                            } else null,
                            onClick = {
                                busy = true
                                if (effectiveEmail.isNotBlank()) {
                                    vm.saveUserProfile(effectiveEmail, brandName, brandVoice) { res ->
                                        busy = false
                                        res.onSuccess {
                                            finishWithMicPrompt()
                                        }.onFailure {
                                            vm.saveProfileLocally(effectiveEmail, brandName, brandVoice)
                                            toastState.show("Profile saved on this device.")
                                            finishWithMicPrompt()
                                        }
                                    }
                                } else {
                                    busy = false
                                    vm.saveProfileLocally("", brandName, brandVoice)
                                    finishWithMicPrompt()
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
                                .clickable(enabled = !busy) {
                                    vm.completeOnboarding()
                                    finishWithMicPrompt()
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        pendingVerifyEmail?.let { targetEmail ->
            EmailVerificationWaitingModal(
                email = targetEmail,
                checking = checkingVerify,
                resending = resendingEmail,
                onCheckVerified = {
                    checkingVerify = true
                    vm.signInWithEmail(targetEmail, password) { res ->
                        checkingVerify = false
                        res.onSuccess { session ->
                            pendingVerifyEmail = null
                            brandName = session.displayName.ifBlank { brandName }
                            toastState.show("Email verified! Personalize your voice next.")
                            scope.launch { pagerState.animateScrollToPage(4) }
                        }.onFailure {
                            toastState.show("Not verified yet — tap the link in your inbox first.")
                        }
                    }
                },
                onResend = {
                    resendingEmail = true
                    vm.resendSignupEmail(targetEmail) { res ->
                        resendingEmail = false
                        res.onSuccess {
                            toastState.show("Verification email resent!")
                        }.onFailure { err ->
                            toastState.show(err.message ?: "Couldn't resend email right now.")
                        }
                    }
                },
                onDismiss = { pendingVerifyEmail = null },
            )
        }

        GlassToastHost(
            state = toastState,
            bottomPadding = 112.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun EmailVerificationWaitingModal(
    email: String,
    checking: Boolean,
    resending: Boolean,
    onCheckVerified: () -> Unit,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
) {
    var cooldownSeconds by remember(email) { mutableStateOf(60) }
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        containerColor = CardSurface,
        title = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DraftyMascot(state = MascotState.THINKING, modifier = Modifier.size(84.dp))
                Text(
                    text = "Check your inbox",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkBlack,
                    textAlign = TextAlign.Center,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "We sent a verification link to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedGray,
                    textAlign = TextAlign.Center,
                )
                SoftChip(text = email, fillColor = SoftWell, textColor = InkBlack)
                Text(
                    text = "Tap the link in your email to confirm your account — you'll return right back to Drafty. Already confirmed on another device? Tap below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray,
                    textAlign = TextAlign.Center,
                )
                if (checking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = InkBlack)
                        Text("Checking verification…", style = MaterialTheme.typography.labelSmall, color = MutedGray)
                    }
                }
            }
        },
        confirmButton = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassButton(
                    text = if (checking) "Checking…" else "I've Verified · Continue",
                    onClick = onCheckVerified,
                    dark = true,
                    enabled = !checking,
                    minHeight = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassButton(
                        text = when {
                            resending -> "Resending…"
                            cooldownSeconds > 0 -> "Resend (${cooldownSeconds}s)"
                            else -> "Resend Email"
                        },
                        onClick = {
                            cooldownSeconds = 60
                            onResend()
                        },
                        dark = false,
                        enabled = !resending && !checking && cooldownSeconds == 0,
                        minHeight = 42.dp,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "Close",
                        onClick = onDismiss,
                        dark = false,
                        minHeight = 42.dp,
                        modifier = Modifier.weight(0.75f),
                    )
                }
            }
        },
    )
}

/**
 * Page 1: Ultra-minimal { Drafty } hero with a larger animated Drafty mascot.
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
            DraftyMascot(state = MascotState.IDLE, modifier = Modifier.size(116.dp))
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
            DraftyMascot(state = MascotState.IDLE, modifier = Modifier.size(124.dp))
            Spacer(Modifier.height(28.dp))
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
            DraftyMascot(state = mascotState, modifier = Modifier.size(116.dp))
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
            DraftyMascot(state = mascotState, modifier = Modifier.size(128.dp))
            Spacer(Modifier.height(32.dp))
            textBlock(Alignment.CenterHorizontally, TextAlign.Center)
        }
    }
}

/**
 * Page 4: Low-friction Unified Account step (Sign In / Create Account + Google OAuth)
 * so the user's account is ready to sync directly to Drafted.
 */
@Composable
private fun OnboardingAccountPage(
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    isSignUpMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    isSignedIn: Boolean,
    signedInEmail: String,
    busy: Boolean,
    onContinueWithGoogle: () -> Unit,
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
                state = MascotState.IDLE,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(
            text = if (isSignUpMode) "Create your account" else "Sign in to Drafty",
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
            text = "One account syncs your scheduled ideas straight into Drafted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (isLandscape) 12.dp else 22.dp))

        SoftCard(
            modifier = if (isLandscape) Modifier.widthIn(max = 520.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
            color = CardSurface,
        ) {
            if (isSignedIn) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftChip(
                        text = "Connected to Drafted",
                        fillColor = Color(0xFFEAF7EE),
                        textColor = Color(0xFF157A42),
                        dotColor = StatusGreen,
                    )
                    Text(
                        text = signedInEmail,
                        style = MaterialTheme.typography.titleMedium,
                        color = InkBlack,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Segmented Create Account / Sign In toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(SoftWell)
                            .padding(4.dp),
                    ) {
                        listOf(true to "Create Account", false to "Sign In").forEach { (signUp, label) ->
                            val active = isSignUpMode == signUp
                            Surface(
                                onClick = { onToggleMode(signUp) },
                                shape = RoundedCornerShape(50),
                                color = if (active) InkBlack else Color.Transparent,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (active) CardSurface else MutedGray,
                                    )
                                }
                            }
                        }
                    }

                    GlassButton(
                        text = "Continue with Google",
                        dark = false,
                        enabled = !busy,
                        onClick = onContinueWithGoogle,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HorizontalDivider(Modifier.weight(1f), color = SubtleDivider)
                        Text("OR EMAIL", style = MaterialTheme.typography.labelSmall, color = MutedGray)
                        HorizontalDivider(Modifier.weight(1f), color = SubtleDivider)
                    }

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
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPassword,
                        label = { Text("Password") },
                        placeholder = { Text("At least 6 characters") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Surface(
                                onClick = { passwordVisible = !passwordVisible },
                                shape = CircleShape,
                                color = Color.Transparent,
                            ) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    if (passwordVisible) {
                                        PixelEyeOffIcon(tint = InkBlack, size = 18.dp)
                                    } else {
                                        PixelEyeIcon(tint = MutedGray, size = 18.dp)
                                    }
                                }
                            }
                        },
                        shape = InnerTileShape,
                        modifier = Modifier.fillMaxWidth(),
                        colors = softFieldColors(),
                    )
                }
            }
        }
    }
}

/**
 * Page 5: Focused Brand Name & Voice personalization step (only 2 fields).
 */
@Composable
private fun OnboardingVoicePage(
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
                modifier = Modifier.size(92.dp),
            )
            Spacer(Modifier.height(18.dp))
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
        Spacer(Modifier.height(if (isLandscape) 14.dp else 24.dp))

        SoftCard(
            modifier = if (isLandscape) Modifier.widthIn(max = 520.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
            color = CardSurface,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
