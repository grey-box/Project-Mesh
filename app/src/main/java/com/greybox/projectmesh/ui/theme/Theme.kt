package com.greybox.projectmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

// Purely additive: the signature is unchanged, so MainActivity, AppNavHost and every
// screen below them compile untouched. Screens migrate off raw values one at a time.
//
// No dynamic color (wallpaper theming on Android 12+), and this is not an oversight to be
// "fixed" later: it would let a user's home screen shift the colors that tell them
// whether a peer is online.
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

    // MeshColors is provided here rather than defaulted, because LocalMeshColors has no
    // sensible default and throws on read. Spacing, size and elevation are scheme-
    // independent, so they get their defaults; they are still provided explicitly so
    // there is one obvious place to swap them (a test, or a future density setting).
    CompositionLocalProvider(
        LocalMeshColors provides if (darkTheme) DarkMeshColors else LightMeshColors,
        LocalMeshSpacing provides MeshSpacing(),
        LocalMeshSize provides MeshSize(),
        LocalMeshElevation provides MeshElevation(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
            typography = Typography,
            shapes = MeshShapes,
        )
    }
}

// Wrap, don't extend: a plain singleton that proxies Material 3.
//
// colors / typography / shapes read straight through to MaterialTheme, so M3 components
// keep theming themselves. mesh / spacing / size / elevation come from the locals above.
//
// mesh is kept as its own property instead of being flattened in next to colors. If
// statusOnline sat on the same object as primary, nothing would stop a button reaching
// for it, and the state-vs-interaction split is the whole point of having two greens.
object MeshTheme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
    val mesh: MeshColors
        @Composable @ReadOnlyComposable get() = LocalMeshColors.current
    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val spacing: MeshSpacing
        @Composable @ReadOnlyComposable get() = LocalMeshSpacing.current
    val size: MeshSize
        @Composable @ReadOnlyComposable get() = LocalMeshSize.current
    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
    val elevation: MeshElevation
        @Composable @ReadOnlyComposable get() = LocalMeshElevation.current
}
