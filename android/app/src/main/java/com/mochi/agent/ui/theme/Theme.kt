package com.mochi.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val MochiBlack = Color(0xFF000000)
val MochiDark = Color(0xFF0B0F1A)
val MochiPanel = Color(0xFF111827)
val MochiBlue = Color(0xFF2A6CFB)
val MochiBlueSoft = Color(0xFF7AA5FF)
val MochiBlueDeep = Color(0xFF1E3A8A)
val MochiWhite = Color(0xFFFFFFFF)
val MochiMuted = Color(0xFF8B93A7)

private val MochiColorScheme = darkColorScheme(
    primary = MochiBlue,
    onPrimary = MochiWhite,
    primaryContainer = MochiBlueDeep,
    onPrimaryContainer = MochiWhite,
    secondary = MochiBlueSoft,
    onSecondary = MochiBlack,
    background = MochiBlack,
    onBackground = MochiWhite,
    surface = MochiDark,
    onSurface = MochiWhite,
    surfaceVariant = MochiPanel,
    onSurfaceVariant = MochiMuted,
    error = Color(0xFFFF5C5C),
)

private val MochiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun MochiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MochiColorScheme,
        shapes = MochiShapes,
        content = content,
    )
}