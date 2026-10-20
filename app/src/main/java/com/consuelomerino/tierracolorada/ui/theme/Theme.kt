package com.consuelomerino.tierracolorada.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SandBase = Color(0xFFB5523B)
val LeafDark = Color(0xFF1F4D2E)
val LeafLight = Color(0xFF4F8A3C)
val HeliconiaRed = Color(0xFFD7263D)
val HeliconiaYellow = Color(0xFFF2B705)
val PondTeal = Color(0xFF1B5E63)

private val ColorScheme = lightColorScheme(
    primary = LeafDark,
    secondary = LeafLight,
    tertiary = HeliconiaRed,
)

@Composable
fun TierraColoradaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
