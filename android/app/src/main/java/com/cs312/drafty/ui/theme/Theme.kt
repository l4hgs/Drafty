package com.cs312.drafty.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Drafty. Apple-inspired borderless soft UI theme paired with monochrome 1-bit pixel sprites.
 */
private val DraftyColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = CardSurface,
    secondary = SoftWell,
    onSecondary = InkBlack,
    tertiary = StatusGreen,
    onTertiary = CardSurface,
    error = StatusRed,
    onError = CardSurface,
    background = AppBackground,
    onBackground = InkBlack,
    surface = CardSurface,
    onSurface = InkBlack,
    surfaceVariant = SoftWell,
    onSurfaceVariant = MutedGray,
    outline = SubtleDivider,
    outlineVariant = SubtleDivider,
    primaryContainer = SoftWell,
    onPrimaryContainer = InkBlack,
    secondaryContainer = SoftWell,
    onSecondaryContainer = InkBlack,
    tertiaryContainer = SoftWell,
    onTertiaryContainer = InkBlack,
)

@Composable
fun DraftyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DraftyColorScheme,
        typography = Typography,
        shapes = DraftyShapes,
        content = content,
    )
}
