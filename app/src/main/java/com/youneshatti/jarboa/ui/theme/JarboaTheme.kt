package com.youneshatti.jarboa.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarboaColors: ColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF252525),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFC8C8C8),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0D),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF555555),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun JarboaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarboaColors,
        typography = Typography(),
        content = content,
    )
}
