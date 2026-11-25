package com.cs312.drafty.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.SweepGradient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs312.drafty.ui.ProcessingStep
import com.cs312.drafty.ui.RecordingState
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusGreen
import com.cs312.drafty.ui.theme.StatusRed
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Luminous RGB prismatic rainbow stops for the screen-edge liquid wave
private val RainbowColorsArgb = intArrayOf(
    Color(0xFF38BDF8).toArgb(), // Cyan
    Color(0xFFA855F7).toArgb(), // Violet
    Color(0xFFF43F5E).toArgb(), // Rose-Magenta
    Color(0xFFFB923C).toArgb(), // Warm Amber
    Color(0xFF34D399).toArgb(), // Mint
    Color(0xFF38BDF8).toArgb(), // Wrap back to Cyan
)

private val CancelTintColorsArgb = intArrayOf(
    Color(0xFFFF3B30).toArgb(),
    Color(0xFFF43F5E).toArgb(),
    Color(0xFFFB923C).toArgb(),
    Color(0xFFFF3B30).toArgb(),
)

private val RainbowBloomColors = listOf(
    Color(0xFF38BDF8),
    Color(0xFFA855F7),
    Color(0xFFF43F5E),
    Color(0xFFFB923C),
    Color(0xFF34D399),
)

/**
 * Full-viewport frosted liquid-glass overlay framed by an Apple Intelligence–style
 * screen-edge RGB iridescent wave that undulates dynamically with microphone amplitude.
 *
 * Collects [stateFlow] and [amplitudeFlow] internally so HomeScreen does not recompose per tick.
 */
@Composable
fun LiquidGlassRecordingOverlay(
    visible: Boolean,
    stateFlow: StateFlow<RecordingState>,
    amplitudeFlow: StateFlow<Int>,
    cancelDragProgress: Float,
    isCancelZone: Boolean,
    isCelebratingSave: Boolean,
    onDismissError: () -> Unit,
    activePrompt: String? = null,
    modifier: Modifier = Modifier,
) {
    val expandProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 280f),
        label = "liquidOverlayExpand",
    )

    if (!visible && expandProgress <= 0.01f) return

    val state by stateFlow.collectAsState()

    // Collect microphone amplitude internally to drive live perimeter ripples & mascot pulse
    var rawVoiceLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(amplitudeFlow, visible) {
        if (!visible) {
            rawVoiceLevel = 0f
            return@LaunchedEffect
        }
        amplitudeFlow.collect { amp ->
            rawVoiceLevel = sqrt((amp / 32767f).coerceIn(0f, 1f))
        }
    }

    val targetVoiceLevel = when {
        state is RecordingState.Recording -> rawVoiceLevel
        state is RecordingState.Processing -> 0.26f
        isCelebratingSave -> 0.32f
        else -> 0.12f
    }
    val voiceLevel by animateFloatAsState(
        targetValue = targetVoiceLevel,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f),
        label = "liquidVoiceLevel",
    )

    val infinite = rememberInfiniteTransition(label = "screenEdgeLiquidWave")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edgeWavePhase1",
    )
    val phase2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edgeWavePhase2",
    )
    val rainbowAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edgeRainbowAngle",
    )

    val blockBackgroundTaps = state !is RecordingState.Recording
    val contentAlpha = ((expandProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
    val contentScale = 0.92f + 0.08f * expandProgress

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (blockBackgroundTaps) {
                    Modifier.pointerInput(Unit) { detectTapGestures { } }
                } else {
                    Modifier
                },
            ),
    ) {
        // 1) Full-screen frosted glass fill + screen-edge RGB iridescent liquid wave + Drafty bottom bloom
        ScreenEdgeLiquidWaveCanvas(
            expandProgress = expandProgress,
            voiceLevel = voiceLevel,
            phase1 = phase1,
            phase2 = phase2,
            rainbowAngle = rainbowAngle,
            isCancelZone = isCancelZone,
            modifier = Modifier.fillMaxSize(),
        )

        // 2) Clean centered HUD above the bottom Drafty hold button
        // Retain the last active content key while contracting so closing mirrors the opening animation
        val liveKey = when {
            isCelebratingSave -> "celebrating"
            state is RecordingState.Recording -> "recording"
            state is RecordingState.Processing -> "processing"
            state is RecordingState.Error -> "error"
            else -> null
        }
        var displayKey by remember { mutableStateOf(liveKey ?: "recording") }
        var lastElapsed by remember { mutableFloatStateOf(0f) }
        var lastStep by remember { mutableStateOf(ProcessingStep.TRANSCRIBING) }
        var lastError by remember { mutableStateOf("Something went wrong.") }

        if (liveKey != null) {
            displayKey = liveKey
            when (val s = state) {
                is RecordingState.Recording -> lastElapsed = s.elapsedSeconds.toFloat()
                is RecordingState.Processing -> lastStep = s.step
                is RecordingState.Error -> lastError = s.message
                else -> Unit
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 36.dp, bottom = 128.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                    translationY = (1f - expandProgress) * 32f
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = displayKey,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
                label = "liquidOverlayContent",
            ) { key ->
                when (key) {
                    "celebrating" -> LiquidCelebratingContent()
                    "recording" -> {
                        val rec = state as? RecordingState.Recording
                        LiquidRecordingContent(
                            elapsed = rec?.elapsedSeconds ?: lastElapsed.toInt(),
                            amplitudeFlow = amplitudeFlow,
                            voiceLevel = voiceLevel,
                            cancelDragProgress = cancelDragProgress,
                            isCancelZone = isCancelZone,
                            activePrompt = activePrompt,
                        )
                    }
                    "processing" -> {
                        val proc = state as? RecordingState.Processing
                        LiquidProcessingContent(step = proc?.step ?: lastStep)
                    }
                    "error" -> {
                        val err = state as? RecordingState.Error
                        LiquidErrorContent(
                            message = err?.message ?: lastError,
                            onDismiss = onDismissError,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ScreenEdgeLiquidWaveCanvas(
    expandProgress: Float,
    voiceLevel: Float,
    phase1: Float,
    phase2: Float,
    rainbowAngle: Float,
    isCancelZone: Boolean,
    modifier: Modifier = Modifier,
) {
    val wideGlowPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val midGlowPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val bezelRimPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
        }
    }
    val shaderMatrix = remember { Matrix() }
    val innerWavePath = remember { Path() }
    val outerBezelPath = remember { Path() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // A) High-opacity full-viewport frosted liquid-glass diffusion base (zero text bleed-through)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xF5FFFFFF).copy(alpha = 0.96f * expandProgress),
                    Color(0xEBFAFAFC).copy(alpha = 0.92f * expandProgress),
                    Color(0xF5FFFFFF).copy(alpha = 0.96f * expandProgress),
                ),
            ),
        )

        val cx = w * 0.5f
        val cy = h * 0.5f
        val cornerRadius = min(w * 0.10f, 38.dp.toPx())

        // B) Balanced bottom-up iridescent light bloom rising from behind the Drafty hold button
        val bloomCenter = Offset(cx, h - 54.dp.toPx())
        val bloomRadius = w * (0.44f + 0.12f * voiceLevel) * expandProgress.coerceAtLeast(0.01f)
        val bloomIdx = ((rainbowAngle / 360f) * RainbowBloomColors.size).toInt() % RainbowBloomColors.size
        val nextBloomIdx = (bloomIdx + 1) % RainbowBloomColors.size
        val bloomFrac = ((rainbowAngle / 360f) * RainbowBloomColors.size) % 1f
        val primaryBloomColor = if (isCancelZone) {
            StatusRed
        } else {
            lerp(RainbowBloomColors[bloomIdx], RainbowBloomColors[nextBloomIdx], bloomFrac)
        }
        val secondaryBloomColor = if (isCancelZone) {
            Color(0xFFFB923C)
        } else {
            RainbowBloomColors[(bloomIdx + 2) % RainbowBloomColors.size]
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryBloomColor.copy(alpha = (0.22f + 0.14f * voiceLevel) * expandProgress),
                    secondaryBloomColor.copy(alpha = (0.09f + 0.07f * voiceLevel) * expandProgress),
                    Color.Transparent,
                ),
                center = bloomCenter,
                radius = bloomRadius,
            ),
            radius = bloomRadius,
            center = bloomCenter,
        )

        // C) Build outer bezel path and undulating inner liquid wave path around the screen perimeter
        val baseInset = (6.dp.toPx() + 11.dp.toPx() * voiceLevel) * expandProgress
        val waveAmp = (3.dp.toPx() + 9.dp.toPx() * voiceLevel) * expandProgress
        buildRoundedRectEdgePaths(
            outerPath = outerBezelPath,
            innerWavePath = innerWavePath,
            width = w,
            height = h,
            cornerRadius = cornerRadius,
            baseInset = baseInset,
            waveAmp = waveAmp,
            phase1 = phase1,
            phase2 = phase2,
        )

        val colors = if (isCancelZone) CancelTintColorsArgb else RainbowColorsArgb
        val sweepShader = SweepGradient(cx, cy, colors, null).apply {
            shaderMatrix.reset()
            shaderMatrix.setRotate(rainbowAngle, cx, cy)
            setLocalMatrix(shaderMatrix)
        }

        val androidInner = innerWavePath.asAndroidPath()
        val androidOuter = outerBezelPath.asAndroidPath()

        // D) Balanced prismatic RGB bezel glow strokes
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // 1. Soft inward iridescent aura
            wideGlowPaint.shader = sweepShader
            wideGlowPaint.strokeWidth = (22.dp.toPx() + 14.dp.toPx() * voiceLevel) * expandProgress
            wideGlowPaint.alpha = ((0.26f + 0.18f * voiceLevel) * expandProgress * 255f)
                .toInt().coerceIn(0, 255)
            wideGlowPaint.maskFilter = BlurMaskFilter(17.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            native.drawPath(androidInner, wideGlowPaint)

            // 2. Luminous liquid wave ribbon along the undulating inner edge
            midGlowPaint.shader = sweepShader
            midGlowPaint.strokeWidth = (8.dp.toPx() + 5.dp.toPx() * voiceLevel) * expandProgress
            midGlowPaint.alpha = ((0.52f + 0.24f * voiceLevel) * expandProgress * 255f)
                .toInt().coerceIn(0, 255)
            midGlowPaint.maskFilter = BlurMaskFilter(7.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            native.drawPath(androidInner, midGlowPaint)

            // 3. Crisp bezel-hugging outer rainbow rim
            bezelRimPaint.shader = sweepShader
            bezelRimPaint.strokeWidth = 5.dp.toPx() * expandProgress
            bezelRimPaint.alpha = (0.72f * expandProgress * 255f).toInt().coerceIn(0, 255)
            bezelRimPaint.maskFilter = BlurMaskFilter(3.5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            native.drawPath(androidOuter, bezelRimPaint)
        }

        // E) Crisp 1.2dp white specular glass inner highlight along the undulating liquid wave edge
        drawPath(
            path = innerWavePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xDDFFFFFF).copy(alpha = 0.78f * expandProgress),
                    Color(0x77FFFFFF).copy(alpha = 0.48f * expandProgress),
                    Color(0xCCFFFFFF).copy(alpha = 0.72f * expandProgress),
                ),
            ),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

/**
 * Samples a rounded-rectangle screen perimeter uniformly by arc length and constructs:
 * - [outerPath]: smooth bezel-hugging rounded rectangle
 * - [innerWavePath]: C1-continuous midpoint-quadratic wave displaced inward along each point's
 *   inward normal using multi-harmonic sine/cosine waves.
 */
private fun buildRoundedRectEdgePaths(
    outerPath: Path,
    innerWavePath: Path,
    width: Float,
    height: Float,
    cornerRadius: Float,
    baseInset: Float,
    waveAmp: Float,
    phase1: Float,
    phase2: Float,
    segments: Int = 96,
) {
    outerPath.reset()
    innerWavePath.reset()

    val r = cornerRadius.coerceAtMost(min(width, height) * 0.45f)
    val straightW = (width - 2f * r).coerceAtLeast(0f)
    val straightH = (height - 2f * r).coerceAtLeast(0f)
    val arcLen = (0.5 * PI * r).toFloat()
    val totalPerimeter = 2f * straightW + 2f * straightH + 4f * arcLen

    val outX = FloatArray(segments)
    val outY = FloatArray(segments)
    val inX = FloatArray(segments)
    val inY = FloatArray(segments)

    for (i in 0 until segments) {
        val t = i.toFloat() / segments.toFloat()
        var d = t * totalPerimeter

        var px: Float
        var py: Float
        var nx: Float
        var ny: Float

        when {
            // Top straight edge (left -> right)
            d < straightW -> {
                px = r + d
                py = 0f
                nx = 0f
                ny = 1f
            }
            // Top-right corner arc
            d < straightW + arcLen -> {
                val a = -PI.toFloat() / 2f + ((d - straightW) / arcLen) * (PI.toFloat() / 2f)
                px = (width - r) + cos(a) * r
                py = r + sin(a) * r
                nx = -cos(a)
                ny = -sin(a)
            }
            // Right straight edge (top -> bottom)
            d < straightW + arcLen + straightH -> {
                val seg = d - (straightW + arcLen)
                px = width
                py = r + seg
                nx = -1f
                ny = 0f
            }
            // Bottom-right corner arc
            d < straightW + 2f * arcLen + straightH -> {
                val a = ((d - (straightW + arcLen + straightH)) / arcLen) * (PI.toFloat() / 2f)
                px = (width - r) + cos(a) * r
                py = (height - r) + sin(a) * r
                nx = -cos(a)
                ny = -sin(a)
            }
            // Bottom straight edge (right -> left)
            d < 2f * straightW + 2f * arcLen + straightH -> {
                val seg = d - (straightW + 2f * arcLen + straightH)
                px = (width - r) - seg
                py = height
                nx = 0f
                ny = -1f
            }
            // Bottom-left corner arc
            d < 2f * straightW + 3f * arcLen + straightH -> {
                val a = PI.toFloat() / 2f + ((d - (2f * straightW + 2f * arcLen + straightH)) / arcLen) * (PI.toFloat() / 2f)
                px = r + cos(a) * r
                py = (height - r) + sin(a) * r
                nx = -cos(a)
                ny = -sin(a)
            }
            // Left straight edge (bottom -> top)
            d < 2f * straightW + 3f * arcLen + 2f * straightH -> {
                val seg = d - (2f * straightW + 3f * arcLen + straightH)
                px = 0f
                py = (height - r) - seg
                nx = 1f
                ny = 0f
            }
            // Top-left corner arc
            else -> {
                val seg = (d - (2f * straightW + 3f * arcLen + 2f * straightH)).coerceAtLeast(0f)
                val a = PI.toFloat() + (seg / arcLen) * (PI.toFloat() / 2f)
                px = r + cos(a) * r
                py = r + sin(a) * r
                nx = -cos(a)
                ny = -sin(a)
            }
        }

        val angle = t * (2.0 * PI).toFloat()
        val harmonic = 0.52f * sin(4f * angle + phase1) +
            0.32f * sin(7f * angle - phase2) +
            0.16f * cos(11f * angle + phase1 * 1.25f)
        // Subtle extra swell along the bottom edge where Drafty emits light
        val bottomBoost = if (ny < -0.5f) 1.15f else 1.0f
        val inset = (baseInset * bottomBoost + harmonic * waveAmp * bottomBoost).coerceAtLeast(2f)

        outX[i] = px
        outY[i] = py
        inX[i] = px + nx * inset
        inY[i] = py + ny * inset
    }

    // Build smooth closed paths via midpoint quadratic curves
    buildClosedSmoothPath(outerPath, outX, outY, segments)
    buildClosedSmoothPath(innerWavePath, inX, inY, segments)
}

private fun buildClosedSmoothPath(path: Path, xs: FloatArray, ys: FloatArray, count: Int) {
    val startMidX = (xs[count - 1] + xs[0]) * 0.5f
    val startMidY = (ys[count - 1] + ys[0]) * 0.5f
    path.moveTo(startMidX, startMidY)
    for (i in 0 until count) {
        val next = (i + 1) % count
        val midX = (xs[i] + xs[next]) * 0.5f
        val midY = (ys[i] + ys[next]) * 0.5f
        path.quadraticTo(xs[i], ys[i], midX, midY)
    }
    path.close()
}

@Composable
private fun LiquidRecordingContent(
    elapsed: Int,
    amplitudeFlow: StateFlow<Int>,
    voiceLevel: Float,
    cancelDragProgress: Float,
    isCancelZone: Boolean,
    activePrompt: String? = null,
) {
    val clampedCancel = cancelDragProgress.coerceIn(0f, 1f)
    val hintMuted = Color(0xFF8E8E93)
    val hintCoral = Color(0xFFE53935)
    val hintColor = lerp(hintMuted, hintCoral, clampedCancel)
    val mascotScale = 1f + 0.06f * voiceLevel

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!activePrompt.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(bottom = 18.dp, start = 28.dp, end = 28.dp)
                    .clip(CircleShape)
                    .background(SoftWell.copy(alpha = 0.88f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Prompt: $activePrompt",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkBlack,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DraftyMascot(
            state = MascotState.LISTENING,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = mascotScale
                    scaleY = mascotScale
                },
            amplitudeFlow = amplitudeFlow,
        )
        Text(
            text = if (isCancelZone) "Release to cancel" else "Listening…",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (isCancelZone) hintCoral else InkBlack,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "%d:%02d".format(elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
            color = MutedGray,
            modifier = Modifier.padding(top = 4.dp),
        )
        WaveformVisualizer(
            amplitudeFlow = amplitudeFlow,
            modifier = Modifier
                .padding(top = 24.dp, start = 32.dp, end = 32.dp)
                .fillMaxWidth()
                .height(76.dp),
        )
        // Spatial upward chevron + slide-to-cancel caption
        Column(
            modifier = Modifier.padding(top = 18.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "↑",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = hintColor,
                modifier = Modifier.graphicsLayer {
                    translationY = -10.dp.toPx() * clampedCancel
                    scaleX = 1f + 0.18f * clampedCancel
                    scaleY = 1f + 0.18f * clampedCancel
                },
            )
            Text(
                text = if (isCancelZone) "Release to cancel" else "Release to draft · Slide up to cancel",
                style = MaterialTheme.typography.labelMedium,
                color = if (isCancelZone) hintCoral else hintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LiquidProcessingContent(step: ProcessingStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DraftyMascot(MascotState.THINKING, Modifier.size(112.dp))
        Text(
            text = "Drafting your idea…",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = InkBlack,
            modifier = Modifier.padding(top = 14.dp),
        )
        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiquidStepRow(
                label = "Whisper transcribing",
                done = step == ProcessingStep.STRUCTURING,
                active = step == ProcessingStep.TRANSCRIBING,
            )
            LiquidStepRow(
                label = "GPT-4o mini structuring",
                done = false,
                active = step == ProcessingStep.STRUCTURING,
            )
        }
    }
}

@Composable
private fun LiquidStepRow(label: String, done: Boolean, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SoftWell.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> PixelCheckIcon(tint = StatusGreen, size = 12.dp)
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = InkBlack,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done || active) InkBlack else MutedGray,
        )
    }
}

@Composable
private fun LiquidCelebratingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DraftyMascot(MascotState.CELEBRATING, Modifier.size(116.dp))
        Text(
            text = "Saved!",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = InkBlack,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Opening your structured draft…",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LiquidErrorContent(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DraftyMascot(MascotState.IDLE, Modifier.size(104.dp))
        Text(
            text = "Hmm, that didn't work",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = InkBlack,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        GlassButton(
            text = "Close",
            onClick = onDismiss,
            dark = true,
            modifier = Modifier
                .padding(top = 20.dp)
                .fillMaxWidth(),
        )
    }
}
