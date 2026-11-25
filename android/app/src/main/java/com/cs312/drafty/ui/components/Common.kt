package com.cs312.drafty.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cs312.drafty.ui.theme.CardShape
import com.cs312.drafty.ui.theme.CardSurface
import com.cs312.drafty.ui.theme.InkBlack
import com.cs312.drafty.ui.theme.MutedGray
import com.cs312.drafty.ui.theme.PillShape
import com.cs312.drafty.ui.theme.ShadowAmbient
import com.cs312.drafty.ui.theme.ShadowSpot
import com.cs312.drafty.ui.theme.SoftWell
import com.cs312.drafty.ui.theme.StatusRed
import kotlin.math.floor

private val LightGlassBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xF0FFFFFF), // luminous top-left specular shine (~94%)
        Color(0xCCFFFFFF), // rich frosted glass center (~80%) — blends ambient tint while staying crisp over text
        Color(0xE0FFFFFF), // soft bottom-right internal light bounce (~88%)
    ),
)
private val LightGlassBorderBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xAAFFFFFF),
        Color(0xD9FFFFFF),
    ),
)
private val DarkGlassBrush = Brush.linearGradient(
    colors = listOf(Color(0xEB222224), Color(0xD9111111)),
)
private val DestructiveGlassBrush = Brush.verticalGradient(
    listOf(StatusRed, Color(0xFFD92D20)),
)
private val GlassSpotShadow = Color(0x1C000000)

/**
 * Apple-inspired borderless pure-white card with soft ambient elevation shadow.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = CardSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardMod = modifier.shadow(
        elevation = 6.dp,
        shape = CardShape,
        ambientColor = ShadowAmbient,
        spotColor = ShadowSpot,
    )
    val body: @Composable () -> Unit = { Column(Modifier.padding(20.dp), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardMod,
            shape = CardShape,
            color = color,
            border = null,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) { body() }
    } else {
        Surface(
            modifier = cardMod,
            shape = CardShape,
            color = color,
            border = null,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) { body() }
    }
}

/**
 * Borderless SoftWell pill chip with optional semantic status dot.
 */
@Composable
fun SoftChip(
    text: String,
    modifier: Modifier = Modifier,
    fillColor: Color = SoftWell,
    dotColor: Color? = null,
    textColor: Color = InkBlack,
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = fillColor,
        border = null,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                Box(Modifier.size(6.dp).background(dotColor, PillShape))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * Circular frosted glassmorphic icon button with 1dp luminous specular border
 * and 8dp soft ambient shadow.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = PillShape,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .shadow(8.dp, shape, ambientColor = ShadowAmbient, spotColor = GlassSpotShadow),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, LightGlassBorderBrush),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(LightGlassBrush),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

/**
 * Unified frosted glassmorphic pill button housing crisp monochrome 1-bit pixel icons.
 * - `dark = false`: Frosted translucent white gradient + specular white border.
 * - `dark = true`: Frosted charcoal glass + subtle white inner highlight.
 * - `destructive = true`: Frosted red glass for destructive confirmation actions.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
    horizontalPadding: Dp = 18.dp,
    shape: Shape = PillShape,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val bgBrush = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0xB3F0F0F3), Color(0x99E5E5EA)))
        destructive -> DestructiveGlassBrush
        dark -> DarkGlassBrush
        else -> LightGlassBrush
    }
    val border = when {
        !enabled -> BorderStroke(1.dp, Color(0x66FFFFFF))
        destructive -> BorderStroke(1.dp, Color(0x40FFFFFF))
        dark -> BorderStroke(1.dp, Color(0x33FFFFFF))
        else -> BorderStroke(1.dp, LightGlassBorderBrush)
    }
    val contentColor = when {
        !enabled -> MutedGray
        destructive || dark -> CardSurface
        else -> InkBlack
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .shadow(
                elevation = if (enabled) 8.dp else 2.dp,
                shape = shape,
                ambientColor = ShadowAmbient,
                spotColor = GlassSpotShadow,
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = border,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(bgBrush)
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Crisp 1-bit Pixel-Grid Micro-Icons (used inside glassmorphic buttons)
// ---------------------------------------------------------------------------

@Composable
private fun PixelIconGrid(pattern: List<String>, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val rows = pattern.size
        val cols = pattern.firstOrNull()?.length ?: 1
        val cell = floor(this.size.minDimension / maxOf(rows, cols)).coerceAtLeast(1f)
        val ox = floor((this.size.width - cell * cols) / 2f)
        val oy = floor((this.size.height - cell * rows) / 2f)
        for (y in 0 until rows) {
            val row = pattern[y]
            for (x in 0 until cols) {
                if (row[x] == '#') {
                    drawRect(tint, Offset(ox + x * cell, oy + y * cell), Size(cell, cell))
                }
            }
        }
    }
}

@Composable
fun PixelQueueIcon(tint: Color = InkBlack, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "..#####..",
            ".#.....#.",
            "#.......#",
            "#..###..#",
            "###...###",
            "#.......#",
            "#########",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelIdeasIcon(tint: Color = InkBlack, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "##.######",
            ".........",
            "##.######",
            ".........",
            "##.######",
            ".........",
            "##.######",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelMicIcon(tint: Color = CardSurface, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "...###...",
            "..#####..",
            "..#####..",
            "..#####..",
            "#..###..#",
            "#.#####.#",
            "..#####..",
            "....#....",
            "..#####..",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelStopIcon(tint: Color = CardSurface, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            ".#####.",
            ".#####.",
            ".#####.",
            ".#####.",
            ".#####.",
            ".#####.",
            ".#####.",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelEditIcon(tint: Color = InkBlack, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "......##.",
            ".....###.",
            "....###..",
            "...###...",
            "..###....",
            ".###.....",
            ".##......",
            "##.......",
            "#########",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelSendIcon(tint: Color = CardSurface, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "....#....",
            "...###...",
            "..#####..",
            ".##.#.##.",
            "....#....",
            "....#....",
            "....#....",
            "..#####..",
            ".........",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelCheckIcon(tint: Color = InkBlack, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "........#",
            ".......##",
            "......##.",
            "#....##..",
            "##..##...",
            ".####....",
            "..##.....",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelTrashIcon(tint: Color = InkBlack, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "...###...",
            ".#######.",
            ".........",
            "..#####..",
            "..#.#.#..",
            "..#.#.#..",
            "..#.#.#..",
            "..#####..",
            ".........",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelBackIcon(tint: Color = InkBlack, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "....#....",
            "...##....",
            "..##.....",
            ".########",
            "..##.....",
            "...##....",
            "....#....",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelSyncIcon(tint: Color = CardSurface, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "..#####..",
            ".##...##.",
            "##.....##",
            "........#",
            "#........",
            "##.....##",
            ".##...##.",
            "..#####..",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelCloseIcon(tint: Color = InkBlack, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "#.....#",
            ".#...#.",
            "..#.#..",
            "...#...",
            "..#.#..",
            ".#...#.",
            "#.....#",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelUserIcon(tint: Color = InkBlack, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "....######....",
            "..##......##..",
            ".#..........#.",
            ".#...####...#.",
            "#...######...#",
            "#...######...#",
            "#...######...#",
            "#....####....#",
            "#............#",
            "#...######...#",
            ".#.########.#.",
            ".#.########.#.",
            "..##########..",
            "....######....",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelShareIcon(tint: Color = InkBlack, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "....#....",
            "...###...",
            "..#.###..",
            "....#....",
            "....#....",
            "#...#...#",
            "#.......#",
            "#.......#",
            "#########",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelCopyIcon(tint: Color = InkBlack, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "..######.",
            "..#....#.",
            "######.#.",
            "#....#.#.",
            "#....###.",
            "#....#...",
            "#....#...",
            "######...",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

@Composable
fun PixelSparkleIcon(tint: Color = InkBlack, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    PixelIconGrid(
        pattern = listOf(
            "....#....",
            "....#....",
            "...###...",
            ".#######.",
            "...###...",
            "....#....",
            "....#....",
        ),
        tint = tint,
        size = size,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Crisp Vector Social Media Logo Badges (Instagram, TikTok, YouTube, etc.)
// ---------------------------------------------------------------------------

/**
 * Normalizes verbose/sub-format platform labels (e.g. "Instagram Stories", "Instagram Reels",
 * "Instagram Post", "YouTube Shorts") into mainstream canonical platform names so cards and
 * filters never show redundant duplicates.
 */
fun canonicalPlatform(raw: String): String {
    val key = raw.trim().lowercase()
    return when {
        key.contains("instagram") || key.contains("reels") || key == "ig" -> "Instagram"
        key.contains("tiktok") || key.contains("tik tok") -> "TikTok"
        key.contains("youtube") || key.contains("shorts") || key == "yt" -> "YouTube"
        key.contains("facebook") || key == "fb" -> "Facebook"
        key == "x" || key.contains("twitter") -> "X"
        key.contains("linkedin") -> "LinkedIn"
        key.contains("threads") -> "Threads"
        else -> raw.trim().ifEmpty { "Social" }
    }
}

/**
 * Compact circular/squircle social media brand logo badge replacing verbose text pills.
 */
@Composable
fun PlatformLogoBadge(
    platform: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    val key = platform.trim().lowercase()
    when {
        key.contains("instagram") || key.contains("reels") || key == "ig" -> {
            Canvas(modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val r = w * 0.28f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFEDA75),
                            Color(0xFFFA7E1E),
                            Color(0xFFD62976),
                            Color(0xFF962FBF),
                            Color(0xFF4F5BD5),
                        ),
                        start = Offset(0f, h),
                        end = Offset(w, 0f),
                    ),
                    cornerRadius = CornerRadius(r, r),
                )
                val stroke = w * 0.08f
                val inset = w * 0.21f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(inset, inset),
                    size = Size(w - 2f * inset, h - 2f * inset),
                    cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.145f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = Color.White,
                    radius = w * 0.045f,
                    center = Offset(w * 0.67f, h * 0.33f),
                )
            }
        }

        key.contains("tiktok") || key.contains("tik tok") -> {
            Canvas(modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                drawCircle(color = Color(0xFF111111))

                fun drawNote(dx: Float, dy: Float, color: Color) {
                    val stroke = w * 0.105f
                    // Vertical stem
                    drawLine(
                        color = color,
                        start = Offset(w * 0.54f + dx, h * 0.27f + dy),
                        end = Offset(w * 0.54f + dx, h * 0.63f + dy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    // Top right flag hook
                    drawLine(
                        color = color,
                        start = Offset(w * 0.54f + dx, h * 0.29f + dy),
                        end = Offset(w * 0.70f + dx, h * 0.37f + dy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    // Bottom note circle
                    drawCircle(
                        color = color,
                        radius = w * 0.135f,
                        center = Offset(w * 0.42f + dx, h * 0.63f + dy),
                        style = Stroke(width = stroke),
                    )
                }

                drawNote(-w * 0.035f, -h * 0.025f, Color(0xFF25F4EE))
                drawNote(w * 0.035f, h * 0.025f, Color(0xFFFE2C55))
                drawNote(0f, 0f, Color.White)
            }
        }

        key.contains("youtube") || key.contains("shorts") || key == "yt" -> {
            Canvas(modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val r = w * 0.28f
                drawRoundRect(
                    color = Color(0xFFFF0000),
                    cornerRadius = CornerRadius(r, r),
                )
                val tri = Path().apply {
                    moveTo(w * 0.40f, h * 0.31f)
                    lineTo(w * 0.70f, h * 0.50f)
                    lineTo(w * 0.40f, h * 0.69f)
                    close()
                }
                drawPath(path = tri, color = Color.White)
            }
        }

        key.contains("facebook") || key == "fb" -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color(0xFF1877F2)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "f",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        key == "x" || key.contains("twitter") -> {
            Canvas(modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                drawCircle(color = Color(0xFF111111))
                val stroke = w * 0.095f
                drawLine(Color.White, Offset(w * 0.32f, h * 0.30f), Offset(w * 0.68f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.68f, h * 0.30f), Offset(w * 0.32f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }

        key.contains("linkedin") -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0A66C2)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "in",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }

        key.contains("threads") -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "@",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        else -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(SoftWell),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = platform.trim().take(1).uppercase().ifEmpty { "•" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkBlack,
                )
            }
        }
    }
}
