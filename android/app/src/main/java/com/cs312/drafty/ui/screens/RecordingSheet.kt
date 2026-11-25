package com.cs312.drafty.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cs312.drafty.ui.ProcessingStep
import com.cs312.drafty.ui.RecordingState
import com.cs312.drafty.ui.components.DraftyMascot
import com.cs312.drafty.ui.components.GlassButton
import com.cs312.drafty.ui.components.MascotState
import com.cs312.drafty.ui.components.PixelCheckIcon
import com.cs312.drafty.ui.components.SoftCard
import com.cs312.drafty.ui.components.WaveformVisualizer
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusGreen
import com.cs312.drafty.ui.theme.StatusRed
import kotlinx.coroutines.flow.StateFlow

/**
 * Full-screen frosted floating overlay shown while holding Drafty to record,
 * while Whisper + GPT-4o mini process the memo, or briefly when saved.
 * Collects [stateFlow] and [amplitudeFlow] internally so HomeScreen does not recompose per tick.
 */
@Composable
fun RecordingOverlay(
    visible: Boolean,
    stateFlow: StateFlow<RecordingState>,
    amplitudeFlow: StateFlow<Int>,
    isCancelZone: Boolean,
    isCelebratingSave: Boolean,
    onDismissError: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val state by stateFlow.collectAsState()
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xE6F7F7F9))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isCelebratingSave -> CelebratingSaveContent()
                state is RecordingState.Recording -> RecordingContent(
                    elapsed = (state as RecordingState.Recording).elapsedSeconds,
                    amplitudeFlow = amplitudeFlow,
                    isCancelZone = isCancelZone,
                )
                state is RecordingState.Processing -> ProcessingContent((state as RecordingState.Processing).step)
                state is RecordingState.Error -> ErrorContent((state as RecordingState.Error).message, onDismissError)
                else -> Unit
            }
        }
    }
}

@Composable
private fun RecordingContent(
    elapsed: Int,
    amplitudeFlow: StateFlow<Int>,
    isCancelZone: Boolean,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DraftyMascot(
            state = MascotState.LISTENING,
            modifier = Modifier.size(160.dp),
            amplitudeFlow = amplitudeFlow,
        )
        Text(
            text = if (isCancelZone) "Release to cancel" else "Listening…",
            style = MaterialTheme.typography.headlineSmall,
            color = if (isCancelZone) StatusRed else InkBlack,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "%d:%02d".format(elapsed / 60, elapsed % 60),
            style = MaterialTheme.typography.headlineMedium,
            color = MutedGray,
            modifier = Modifier.padding(top = 4.dp),
        )
        WaveformVisualizer(
            amplitudeFlow = amplitudeFlow,
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .height(84.dp),
        )
        Text(
            text = if (isCancelZone) "Drop to discard recording" else "Release to draft · Slide up to cancel",
            style = MaterialTheme.typography.labelMedium,
            color = if (isCancelZone) StatusRed else MutedGray,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun ProcessingContent(step: ProcessingStep) {
    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DraftyMascot(MascotState.THINKING, Modifier.size(144.dp))
            Text(
                "Drafting your idea…",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                Modifier.padding(top = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepRow(
                    label = "Whisper transcribing",
                    done = step == ProcessingStep.STRUCTURING,
                    active = step == ProcessingStep.TRANSCRIBING,
                )
                StepRow(
                    label = "GPT-4o mini structuring",
                    done = false,
                    active = step == ProcessingStep.STRUCTURING,
                )
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SoftWell),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> PixelCheckIcon(tint = StatusGreen, size = 12.dp)
                active -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = InkBlack)
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (done || active) InkBlack else MutedGray,
        )
    }
}

@Composable
private fun CelebratingSaveContent() {
    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DraftyMascot(MascotState.CELEBRATING, Modifier.size(152.dp))
            Text(
                "Saved!",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Opening your structured draft…",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), color = CardSurface) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DraftyMascot(MascotState.IDLE, Modifier.size(132.dp))
            Text(
                "Hmm, that didn't work",
                style = MaterialTheme.typography.headlineSmall,
                color = InkBlack,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            GlassButton(
                text = "Close",
                onClick = onDismiss,
                dark = true,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            )
        }
    }
}
