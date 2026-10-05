package com.greybox.projectmesh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Spacing scale (05 · Spacing, Size, Shape, Elevation). Access via MeshTheme.spacing.
// The scale comes from usage frequency: 8, 16 and 4 dp were 74 of ~123 raw literals.

@Immutable
data class MeshSpacing(
    val none: Dp = 0.dp,  // explicit zero where a default would otherwise apply
    val xs: Dp = 4.dp,    // icon-to-label gap; tight vertical rhythm inside a row
    val sm: Dp = 8.dp,    // padding inside compact components; closely related items
    val md: Dp = 16.dp,   // DEFAULT: screen gutters, card padding, list row padding
    val lg: Dp = 24.dp,   // gap between distinct sections in a scrolling column
    val xl: Dp = 32.dp,   // major section breaks; empty-state padding
    val xxl: Dp = 48.dp,  // top-of-screen hero spacing
)

// static: spacing never changes after theme setup, so read tracking buys nothing.
val LocalMeshSpacing = staticCompositionLocalOf { MeshSpacing() }