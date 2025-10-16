package com.greybox.projectmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6A4F5),
    onPrimary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    secondary = Color(0xFFAAAAAA),
    onSecondary = Color.Black,
    outline = Color(0xFF444444)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6A5AE0), // vibrant purple (used for buttons, etc.)
    onPrimary = Color.White,
    background = Color(0xFFF9F9F9), // soft white background
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    secondary = Color(0xFFB0B0B0), // soft gray
    onSecondary = Color.Black,
    outline = Color(0xFFE0E0E0) // subtle border color
)


@Composable
fun ProjectMeshTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}