package com.greybox.projectmesh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Rules:
//  - statusOnline means STATE only. Never use it for something tappable.
//  - Primary green (MaterialTheme.colorScheme.primary) means INTERACTION.
//  - Always set the matching on* color explicitly; never rely on inherited content color.
//  - statusOnline/onStatusOnline and bubbleSent/onBubbleSent must each meet 4.5:1 contrast.
//
// The light and dark instances live in ColorScheme.kt.
@Immutable
data class MeshColors(
    val statusOnline: Color,
    val onStatusOnline: Color,
    val statusOffline: Color,
    val statusConnecting: Color,
    val bubbleSent: Color,
    val onBubbleSent: Color,
    val bubbleReceived: Color,   // = surfaceVariant (keep the M3 role)
    val onBubbleReceived: Color, // = onSurfaceVariant
)

// compositionLocalOf (not static): the value changes when the user switches light/dark.
// No sensible default exists, so reading it outside ProjectMeshTheme fails loudly.
val LocalMeshColors = compositionLocalOf<MeshColors> {
    error("No MeshColors provided. Wrap your content in ProjectMeshTheme.")
}