package com.greybox.projectmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color

/**
 * Dark color scheme for the app.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC5),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

/**
 * Light color scheme for the app.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC5),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black
)

/**
 * Enum to represent the app's theme choice.
 */
enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

/**
 * Apply ProjectMesh theme with the chosen AppTheme.
 *
 * @param appTheme The selected theme (System, Light, Dark)
 * @param content The composable content to wrap with this theme
 */
@Composable
fun ProjectMeshTheme(
    appTheme: AppTheme,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme() // Follow system setting
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography, // Apply predefined typography
        content = content
    )
}
