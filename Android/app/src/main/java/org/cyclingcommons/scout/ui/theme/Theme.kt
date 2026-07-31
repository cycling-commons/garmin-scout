package org.cyclingcommons.scout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand red from Scout-logo-white.jpg */
val ScoutRed = Color(0xFFE30613)
val ScoutMuted = Color(0xFF9A9A9A)

private val scheme = darkColorScheme(
    primary = ScoutRed,
    onPrimary = Color.White,
    secondary = Color(0xFF2A2A2A),
    onSecondary = Color.White,
    background = Color(0xFF0E0E0E),
    surface = Color(0xFF161616),
    onSurface = Color.White,
    outline = Color(0xFF3A3A3A),
)

@Composable
fun ScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
