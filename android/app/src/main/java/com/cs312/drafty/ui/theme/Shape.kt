package com.cs312.drafty.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Outer borderless cards: smooth 20dp corners. */
val CardShape = RoundedCornerShape(20.dp)

/** Inner hook, shot, waveform & info tiles: smooth 12dp corners. */
val InnerTileShape = RoundedCornerShape(12.dp)

/** Status chips & segmented tab bar: full pill. */
val PillShape = CircleShape

/** Modal bottom sheet top corners. */
val SheetTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** Interactive pixel-art buttons only: crisp 4dp stepped corner. */
val PixelButtonShape = RoundedCornerShape(4.dp)

val DraftyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = InnerTileShape,
    medium = CardShape,
    large = CardShape,
    extraLarge = SheetTopShape,
)
