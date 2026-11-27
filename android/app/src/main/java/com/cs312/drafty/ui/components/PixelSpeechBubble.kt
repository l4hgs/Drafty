package com.cs312.drafty.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs312.drafty.ui.IdeaCard
import com.cs312.drafty.ui.UserProfileState
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.ShadowAmbient
import com.cs312.drafty.ui.theme.ShadowSpot
import com.cs312.drafty.ui.theme.SubtleDivider
import kotlinx.coroutines.delay

/**
 * Generates 5-6 short, punchy 1-to-2-line idea prompts tailored to the user's
 * recent ideas, brand voice / creator name, and a creator inspiration fallback pool.
 */
fun buildAmbientIdeaPrompts(
    recentIdeas: List<IdeaCard>,
    profile: UserProfileState,
): List<String> {
    val prompts = mutableListOf<String>()

    val firstIdea = recentIdeas.firstOrNull()?.idea
    if (firstIdea != null) {
        val shortTitle = firstIdea.title.take(26).trimEnd()
        prompts += "Part 2 on \"$shortTitle\"?"
        val pillar = firstIdea.metadata.contentPillar.trim()
        if (pillar.isNotBlank()) {
            prompts += "15-sec hook twist on $pillar?"
        }
    }

    val brand = profile.brandName.trim()
    if (brand.isNotBlank()) {
        prompts += "Quick reel in your $brand voice?"
    }

    val fallbacks = listOf(
        "3 mistakes beginners make…",
        "Unpopular opinion in your niche?",
        "Behind-the-scenes in 20s?",
        "Myth vs. reality hook?",
        "One tool you can't live without?",
    )
    for (item in fallbacks) {
        if (prompts.size >= 6) break
        prompts += item
    }
    return prompts
}

/**
 * Clean, smooth modern chat bubble (rounded capsule with a subtle chat corner)
 * matching Drafty's crisp light-surface UI.
 */
@Composable
fun PixelSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tailOnRight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (tailOnRight) 18.dp else 4.dp,
        bottomEnd = if (tailOnRight) 4.dp else 18.dp,
    )

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .then(clickMod)
            .widthIn(min = 124.dp, max = 196.dp)
            .shadow(
                elevation = 4.dp,
                shape = bubbleShape,
                ambientColor = ShadowAmbient,
                spotColor = ShadowSpot,
            ),
        shape = bubbleShape,
        color = CardSurface,
        border = BorderStroke(1.dp, SubtleDivider),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MutedGray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * Ambient background layer rendering 3 softly drifting, low-contrast pixel speech bubbles
 * positioned around the upper/lower margins of the Home greeting, cycling prompts every ~6s.
 */
@Composable
fun AmbientIdeaBubblesLayer(
    prompts: List<String>,
    onBubbleTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxBubbles: Int = 3,
) {
    if (prompts.isEmpty() || maxBubbles <= 0) return

    var cycleIndex by remember(prompts) { mutableIntStateOf(0) }
    LaunchedEffect(prompts) {
        while (true) {
            delay(6000L)
            cycleIndex = (cycleIndex + 1) % prompts.size
        }
    }

    val infinite = rememberInfiniteTransition(label = "ambientBubbleFloat")
    val floatA by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubbleFloatA",
    )
    val floatB by infinite.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubbleFloatB",
    )
    val floatC by infinite.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bubbleFloatC",
    )

    val p0 = prompts[cycleIndex % prompts.size]
    val p1 = prompts[(cycleIndex + 1) % prompts.size]
    val p2 = prompts[(cycleIndex + 2) % prompts.size]

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // Upper-left ambient bubble (safely below status bar, clear of top-right profile button)
        Box(
            modifier = Modifier
                .align(BiasAlignment(-0.88f, -0.74f))
                .offset(y = floatA.dp)
                .alpha(0.72f),
        ) {
            AnimatedContent(
                targetState = p0,
                transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(500)) },
                label = "bubblePrompt0",
            ) { text ->
                PixelSpeechBubble(
                    text = text,
                    tailOnRight = false,
                    onClick = { onBubbleTap(text) },
                )
            }
        }

        // Upper-right ambient bubble (staggered below profile button, clear of center greeting)
        if (maxBubbles >= 2) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(0.90f, -0.50f))
                    .offset(y = floatB.dp)
                    .alpha(0.64f),
            ) {
                AnimatedContent(
                    targetState = p1,
                    transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(500)) },
                    label = "bubblePrompt1",
                ) { text ->
                    PixelSpeechBubble(
                        text = text,
                        tailOnRight = true,
                        onClick = { onBubbleTap(text) },
                    )
                }
            }
        }

        // Lower-left ambient bubble (below greeting & quick-resume pill, clear of bottom navbar)
        if (maxBubbles >= 3) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(-0.82f, 0.34f))
                    .offset(y = floatC.dp)
                    .alpha(0.66f),
            ) {
                AnimatedContent(
                    targetState = p2,
                    transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(500)) },
                    label = "bubblePrompt2",
                ) { text ->
                    PixelSpeechBubble(
                        text = text,
                        tailOnRight = false,
                        onClick = { onBubbleTap(text) },
                    )
                }
            }
        }
    }
}
