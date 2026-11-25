package com.cs312.drafty.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cs312.drafty.ui.theme.InkBlack
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/**
 * Background-free minimal monochrome (#111111) audio waveform bars
 * floating cleanly directly over the frosted liquid-glass surface.
 */
@Composable
fun WaveformVisualizer(
    amplitudeFlow: StateFlow<Int>,
    modifier: Modifier = Modifier,
    barCount: Int = 28,
    barColor: Color = InkBlack,
) {
    val history = remember(barCount) {
        mutableStateListOf<Float>().apply { repeat(barCount) { add(MIN_BAR) } }
    }

    // Collected internally so only this canvas redraws on each 80ms sample.
    LaunchedEffect(amplitudeFlow) {
        amplitudeFlow.collect { amplitude ->
            val level = sqrt((amplitude / 32767f).coerceIn(0f, 1f)).coerceAtLeast(MIN_BAR)
            history.removeAt(0)
            history.add(level)
        }
    }

    Canvas(modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        val slot = size.width / barCount
        val barW = slot * 0.46f
        history.forEachIndexed { i, level ->
            val h = size.height * level
            drawRoundRect(
                color = barColor.copy(alpha = 0.25f + 0.75f * (i + 1) / barCount),
                topLeft = Offset(i * slot + (slot - barW) / 2f, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

private const val MIN_BAR = 0.08f
