package com.greybox.projectmesh.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Component dimensions (05 · Spacing, Size, Shape, Elevation). Access via MeshTheme.size.
// These are NOT spacing: never draw them from the spacing scale.
@Immutable
data class MeshSize(
    // Icons
    val iconXs: Dp = 16.dp,          // attachment icon inside a message bubble
    val iconSm: Dp = 20.dp,          // small inline icons
    val iconMd: Dp = 24.dp,          // DEFAULT icon size: signal bars, list trailing icons
    val iconLg: Dp = 32.dp,          // file-type icons
    val iconXl: Dp = 40.dp,          // icon-button content
    val iconEmptyState: Dp = 72.dp,  // empty-state illustration

    // Avatars (MeshAvatar picks the initial's text style from these)
    val avatarSm: Dp = 40.dp,        // UserStatusBar in ChatScreen -> titleMedium initial
    val avatarMd: Dp = 48.dp,        // ConversationItem -> titleLarge initial

    // Status dots (named by context, both sizes are intentional)
    val statusDot: Dp = 12.dp,       // overlaid on an avatar
    val statusDotInline: Dp = 8.dp,  // sitting inline with text

    // Interactive
    val touchTargetMin: Dp = 48.dp,  // accessibility floor for anything tappable
    val buttonHeight: Dp = 48.dp,    // replaces 50.dp; also satisfies the touch target
    val buttonMinWidth: Dp = 120.dp,

    // Messaging
    val bubbleMaxWidth: Dp = 280.dp, // fixed cap, not a fraction (phones only, minSdk 26)

    // Onboarding
    val illustrationPanel: Dp = 120.dp,       // hero illustration panel behind the page icon
    val pageIndicatorDot: Dp = 6.dp,          // inactive page dot, and the height of both
    val pageIndicatorDotActive: Dp = 16.dp,   // active dot stretches into a pill
)

// static: sizes never change after theme setup.
val LocalMeshSize = staticCompositionLocalOf { MeshSize() }