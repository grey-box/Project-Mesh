package com.greybox.projectmesh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shadow depth (05 · Spacing, Size, Shape, Elevation). Access via MeshTheme.elevation.
//
// Prefer M3 tonal elevation (the surfaceContainer* roles) over drop shadows wherever
// there's a choice. Shadows read poorly on dark backgrounds, which is likely why the old
// gradient buttons looked heavy in dark: they paired an 8dp shadow with a 12dp radius.
//
// Four levels, not six. The scale is 0/1/3/6 because that's what the app actually needs;
// M3's levels 4 and 5 have no use here and would only be picked by accident.
@Immutable
data class MeshElevation(
    val level0: Dp = 0.dp,  // flat surfaces
    val level1: Dp = 1.dp,  // resting cards, message bubbles
    val level2: Dp = 3.dp,  // raised cards
    val level3: Dp = 6.dp,  // dialogs, pressed buttons
)

// static: elevation never changes after theme setup.
val LocalMeshElevation = staticCompositionLocalOf { MeshElevation() }
