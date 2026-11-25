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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs312.drafty.ui.IdeaCard
import com.cs312.drafty.ui.UserProfileState
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
 * Low-contrast ambient pixel speech bubble with stepped 1-bit pixel corners,
 * a stepped speech tail, and a subtle pixel drop-shadow offset.
 */
@Composable
fun PixelSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    tailOnRight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bubbleFill = Color(0xFFFAFAFC)
    val bubbleBorder = Color(0xFFDCDCE2)
    val bubbleShadow = Color(0x14000000)
    val textColor = Color(0xFF8E8E93)

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(clickMod)
            .widthIn(min = 124.dp, max = 192.dp)
            .drawBehind {
                val tailH = 7.dp.toPx()
                val w = size.width
                val bodyH = (size.height - tailH).coerceAtLeast(24.dp.toPx())
                val radius = 16.dp.toPx().coerceAtMost(bodyH * 0.48f)
                val cr = CornerRadius(radius, radius)
                val borderW = 1.2.dp.toPx()
                val shadowOffset = 2.dp.toPx()

                fun buildTailPath(dx: Float, dy: Float): Path {
                    val baseX = if (tailOnRight) w - 24.dp.toPx() else 18.dp.toPx()
                    val tipX = if (tailOnRight) baseX + 8.dp.toPx() else baseX - 8.dp.toPx()
                    return Path().apply {
                        moveTo(baseX + dx, bodyH - 1.5.dp.toPx() + dy)
                        quadraticTo(
                            baseX + dx,
                            bodyH + tailH * 0.65f + dy,
                            tipX + dx,
                            bodyH + tailH + dy,
                        )
                        quadraticTo(
                            (if (tailOnRight) baseX - 2.dp.toPx() else baseX + 2.dp.toPx()) + dx,
                            bodyH + tailH * 0.65f + dy,
                            (if (tailOnRight) baseX - 10.dp.toPx() else baseX + 10.dp.toPx()) + dx,
                            bodyH - 1.5.dp.toPx() + dy,
                        )
                        close()
                    }
                }

                // 1) Soft ambient drop shadow
                drawRoundRect(
                    color = bubbleShadow,
                    topLeft = Offset(0f, shadowOffset),
                    size = Size(w, bodyH),
                    cornerRadius = cr,
                )
                drawPath(buildTailPath(0f, shadowOffset), color = bubbleShadow)

                // 2) Rounded speech bubble fill + subtle border
                drawRoundRect(
                    color = bubbleFill,
                    topLeft = Offset.Zero,
                    size = Size(w, bodyH),
                    cornerRadius = cr,
                )
                drawRoundRect(
                    color = bubbleBorder,
                    topLeft = Offset.Zero,
                    size = Size(w, bodyH),
                    cornerRadius = cr,
                    style = Stroke(width = borderW),
                )

                // 3) Smooth rounded speech tail seamlessly joined to body
                val tailPath = buildTailPath(0f, 0f)
                drawPath(tailPath, color = bubbleBorder, style = Stroke(width = borderW))
                drawPath(tailPath, color = bubbleFill)
            }
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
) {
    if (prompts.isEmpty()) return

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

        // Lower-left ambient bubble (below greeting & quick-resume pill, clear of bottom navbar)
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
