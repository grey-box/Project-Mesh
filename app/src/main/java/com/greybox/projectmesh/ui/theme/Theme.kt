package com.greybox.projectmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.ReadOnlyComposable

// Define the color schemes for light and dark themes
//private val DarkColorScheme = darkColorScheme(
//    primary = Color(0xFFBB86FC),
//    secondary = Color(0xFF03DAC5),
//    background = Color(0xFF121212),
//    surface = Color(0xFF121212),
//    onPrimary = Color.Black,
//    onSecondary = Color.Black,
//    onBackground = Color.White,
//    onSurface = Color.White
//)
//
//private val LightColorScheme = lightColorScheme(
//    primary = Color(0xFF6200EE),
//    secondary = Color(0xFF03DAC5),
//    background = Color(0xFFFFFFFF),
//    surface = Color(0xFFFFFFFF),
//    onPrimary = Color.White,
//    onSecondary = Color.Black,
//    onBackground = Color.Black,
//    onSurface = Color.Black
//)

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

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
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
        typography = Typography,
    )
}

// Wrap, don't extend: a plain singleton that proxies Material 3.
object MeshTheme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
}