package com.cs312.drafty.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cs312.drafty.ui.theme.InkBlack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.floor
import kotlin.math.sqrt

enum class MascotState { IDLE, LISTENING, THINKING, CELEBRATING }

private const val GRID = 24

/**
 * Minimal 1-bit monochrome (#111111 / transparent) Finder-style ':' + ')'
 * pixel-art Drafty sprite rendered via ImageBitmap with FilterQuality.None.
 */
@Composable
fun DraftyMascot(
    state: MascotState,
    modifier: Modifier = Modifier,
    amplitudeFlow: StateFlow<Int>? = null,
) {
    var frame by remember(state) { mutableIntStateOf(0) }
    var isBlinking by remember(state) { mutableStateOf(false) }
    var liveAmpScale by remember(state) { mutableFloatStateOf(0f) }

    // Subtle ±3.dp breathing float for IDLE
    val infiniteTransition = rememberInfiniteTransition(label = "mascotFloat")
    val floatDp by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val rhythmPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "listenPulse",
    )

    // Collect live audio amplitude during LISTENING for responsive scale pulse
    LaunchedEffect(state, amplitudeFlow) {
        if (state == MascotState.LISTENING && amplitudeFlow != null) {
            amplitudeFlow.collect { amp ->
                liveAmpScale = sqrt((amp / 32767f).coerceIn(0f, 1f)) * 0.08f
            }
        } else {
            liveAmpScale = 0f
        }
    }

    // State frame & blink timers
    LaunchedEffect(state) {
        when (state) {
            MascotState.IDLE -> {
                while (true) {
                    delay(3500L)
                    isBlinking = true
                    delay(140L)
                    isBlinking = false
                }
            }
            MascotState.LISTENING -> {
                while (true) {
                    delay(280L)
                    frame = (frame + 1) % 2
                }
            }
            MascotState.THINKING -> {
                while (true) {
                    delay(220L)
                    frame = (frame + 1) % 4
                }
            }
            MascotState.CELEBRATING -> {
                while (true) {
                    delay(280L)
                    frame = (frame + 1) % 2
                }
            }
        }
    }

    // Gentle pop-in scale for CELEBRATING (calm twinkle, no jumping up and down)
    var celebratePopped by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        celebratePopped = (state == MascotState.CELEBRATING)
    }
    val celebrateScale by animateFloatAsState(
        targetValue = if (celebratePopped) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "celebrateScale",
    )

    val yOffsetDp = when (state) {
        MascotState.IDLE -> floatDp
        else -> 0f
    }
    val scale = when (state) {
        MascotState.LISTENING -> if (amplitudeFlow != null) 1.0f + liveAmpScale else rhythmPulse
        MascotState.CELEBRATING -> celebrateScale
        else -> 1.0f
    }

    val bitmap = remember(state, frame, isBlinking) {
        buildFinderSpriteBitmap(state, frame, isBlinking)
    }

    Canvas(
        modifier
            .offset(y = yOffsetDp.dp)
            .scale(scale)
            .aspectRatio(1f),
    ) {
        val cell = floor(size.minDimension / GRID).toInt().coerceAtLeast(1)
        val total = cell * GRID
        val ox = floor((size.width - total) / 2f).toInt()
        val oy = floor((size.height - total) / 2f).toInt()
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(ox, oy),
            dstSize = IntSize(total, total),
            filterQuality = FilterQuality.None,
        )
    }
}

private fun buildFinderSpriteBitmap(state: MascotState, frame: Int, isBlinking: Boolean): ImageBitmap {
    val g = Array(GRID) { CharArray(GRID) { '.' } }
    fun put(x: Int, y: Int, ch: Char) {
        if (x in 0 until GRID && y in 0 until GRID) g[y][x] = ch
    }
    fun rect(x: Int, y: Int, w: Int, h: Int, ch: Char) {
        for (yy in y until y + h) for (xx in x until x + w) put(xx, yy, ch)
    }

    // 1) Bold curved pixel arc ')' framing the right side of the face
    val rightArc = listOf(
        13 to 4, 14 to 4,
        15 to 5, 16 to 5,
        16 to 6, 17 to 6,
        17 to 7, 18 to 7,
        17 to 8, 18 to 8,
        18 to 9, 19 to 9,
        18 to 10, 19 to 10,
        18 to 11, 19 to 11,
        18 to 12, 19 to 12,
        18 to 13, 19 to 13,
        18 to 14, 19 to 14,
        17 to 15, 18 to 15,
        17 to 16, 18 to 16,
        16 to 17, 17 to 17,
        15 to 18, 16 to 18,
        13 to 19, 14 to 19,
    )
    for ((ax, ay) in rightArc) put(ax, ay, 'K')

    // 2) Eyes (two 2x4 vertical pixel blocks on left/center: x=5..6 and x=11..12, y=6..9)
    when (state) {
        MascotState.IDLE -> {
            if (isBlinking) {
                rect(5, 8, 2, 1, 'K')
                rect(11, 8, 2, 1, 'K')
            } else {
                rect(5, 6, 2, 4, 'K')
                rect(11, 6, 2, 4, 'K')
            }
        }
        MascotState.LISTENING -> {
            // Expressive wide/active 2x5 pixel eyes
            rect(5, 5, 2, 5, 'K')
            rect(11, 5, 2, 5, 'K')
            // Animated pixel sound-wave arcs radiating from the right ')' curve
            if (frame == 0) {
                rect(21, 9, 1, 6, 'K')
                put(20, 8, 'K')
                put(20, 15, 'K')
            } else {
                rect(21, 10, 1, 4, 'K')
                rect(23, 8, 1, 8, 'K')
                put(22, 7, 'K')
                put(22, 16, 'K')
            }
        }
        MascotState.THINKING -> {
            // Alternating animated pixel eyes + bouncing/orbital pixel dots
            when (frame % 4) {
                0 -> {
                    rect(5, 6, 2, 3, 'K')
                    rect(11, 7, 2, 3, 'K')
                }
                1 -> {
                    rect(5, 7, 2, 3, 'K')
                    rect(11, 6, 2, 3, 'K')
                }
                2 -> {
                    rect(5, 6, 2, 4, 'K')
                    rect(11, 8, 2, 1, 'K')
                }
                else -> {
                    rect(5, 8, 2, 1, 'K')
                    rect(11, 6, 2, 4, 'K')
                }
            }
            // Orbital/bouncing pixel dots above face
            val active = frame % 3
            for (i in 0..2) {
                val dx = 6 + i * 3
                val dy = if (i == active) 1 else 2
                rect(dx, dy, 2, 2, 'K')
            }
        }
        MascotState.CELEBRATING -> {
            // Happy upward-curved pixel eyes '^ ^'
            put(4, 8, 'K'); put(5, 7, 'K'); put(6, 7, 'K'); put(7, 8, 'K')
            put(10, 8, 'K'); put(11, 7, 'K'); put(12, 7, 'K'); put(13, 8, 'K')
            // Crisp pixel sparkle & checkmark accent on top-left / top-right
            if (frame == 1) {
                put(2, 3, 'K'); put(1, 4, 'K'); put(3, 4, 'K'); put(2, 5, 'K')
            } else {
                put(2, 4, 'K')
            }
            put(19, 4, 'K'); put(20, 5, 'K'); put(21, 4, 'K'); put(22, 3, 'K')
        }
    }

    // 3) Mouth / smile below eyes (6x2 stepped pixel arc around x=6..12, y=14..16)
    when (state) {
        MascotState.THINKING -> {
            rect(7, 15, 4, 2, 'K')
        }
        MascotState.CELEBRATING -> {
            put(5, 13, 'K'); put(12, 13, 'K')
            put(5, 14, 'K'); put(6, 14, 'K'); put(11, 14, 'K'); put(12, 14, 'K')
            rect(6, 15, 6, 2, 'K')
        }
        else -> {
            put(5, 14, 'K'); put(6, 14, 'K')
            put(11, 14, 'K'); put(12, 14, 'K')
            rect(6, 15, 6, 2, 'K')
        }
    }

    val black = InkBlack.toArgb()
    val pixels = IntArray(GRID * GRID)
    for (y in 0 until GRID) {
        val row = g[y]
        for (x in 0 until GRID) {
            pixels[y * GRID + x] = if (row[x] == 'K') black else 0x00000000
        }
    }
    val bmp = android.graphics.Bitmap.createBitmap(
        pixels,
        GRID,
        GRID,
        android.graphics.Bitmap.Config.ARGB_8888,
    )
    return bmp.asImageBitmap()
}
