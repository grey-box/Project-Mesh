package com.greybox.projectmesh.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Caps the font scale for content (e.g. fixed-slot bars); dp sizes are unaffected
@Composable
fun CappedFontScale(max: Float = 1.15f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(max))
    ) { content() }
}
