package com.arcshield.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Industrial UI: high-contrast dark scheme suitable for a factory floor.
private val DarkColors = darkColorScheme(
    primary          = Color(0xFF4FC3F7),   // light blue — active state / CTAs
    onPrimary        = Color(0xFF003544),
    primaryContainer = Color(0xFF004D61),
    secondary        = Color(0xFF80CBC4),   // teal accent
    background       = Color(0xFF0D1117),   // near-black
    surface          = Color(0xFF161B22),
    onSurface        = Color(0xFFE6EDF3),
    error            = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF0277BD),
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    secondary        = Color(0xFF00796B),
    background       = Color(0xFFF5F7FA),
    surface          = Color.White,
    onSurface        = Color(0xFF1C1B1F),
    error            = Color(0xFFB00020),
)

@Composable
fun ArcShieldTheme(
    darkTheme: Boolean = true,   // factory floor default: dark
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content     = content,
    )
}
