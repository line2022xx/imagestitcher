package com.linexx.imagestitcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF1A1C1E)
private val Brand = Color(0xFF3A5BFF)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF001452),
    secondary = Color(0xFF5A5E72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E3F5),
    onSecondaryContainer = Color(0xFF171A2C),
    background = Color(0xFFF7F8FA),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFF4),
    onSurfaceVariant = Color(0xFF5A5E66),
    outline = Color(0xFFD3D7E0),
    error = Color(0xFFD3302F),
    onError = Color.White,
)

@Composable
fun ImageStitcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content,
    )
}
