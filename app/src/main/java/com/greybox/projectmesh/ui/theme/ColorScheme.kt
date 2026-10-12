package com.greybox.projectmesh.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme


// Maps the raw values in Color.kt to Material 3 roles. Should rarely change.

val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = textColor,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary10,
    inversePrimary = InversePrimaryLight,
    secondary = Secondary40,
    onSecondary = textColor,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary10,
    tertiary = Tertiary40,
    onTertiary = textColor,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary10,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    error = Error40,
    onError = textColor,
    errorContainer = Error90,
    onErrorContainer = Error10,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Scrim,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
)

val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    inversePrimary = InversePrimaryDark,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Scrim,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
)

// The light and dark MeshColors instances. Kept next to the schemes they pair with so a
// new role can't be added to one scheme and forgotten in the other.
//
// bubbleReceived/bubbleSent alias existing role values rather than duplicating hexes:
// received is surfaceVariant (which is what the old code already did, correctly) and sent
// is tertiaryContainer. Only the three status colors are genuinely new.
//
// Measured contrast, both schemes (WCAG 2.1, sRGB):
//   statusOnline/onStatusOnline       light  5.00:1   dark 5.59:1
//   bubbleSent/onBubbleSent           light 13.27:1   dark 7.26:1
//   bubbleReceived/onBubbleReceived   light  7.22:1   dark 5.48:1
// All clear 4.5:1. Changing a value here means re-measuring its partner.
val LightMeshColors = MeshColors(
    statusOnline = StatusOnlineLight,
    onStatusOnline = OnStatusOnlineLight,
    statusOffline = StatusOfflineLight,
    statusConnecting = StatusConnectingLight,
    bubbleSent = Tertiary90,
    onBubbleSent = Tertiary10,
    bubbleReceived = SurfaceVariantLight,
    onBubbleReceived = OnSurfaceVariantLight,
)

val DarkMeshColors = MeshColors(
    statusOnline = StatusOnlineDark,
    onStatusOnline = OnStatusOnlineDark,
    statusOffline = StatusOfflineDark,
    statusConnecting = StatusConnectingDark,
    bubbleSent = Tertiary30,
    onBubbleSent = Tertiary90,
    bubbleReceived = SurfaceVariantDark,
    onBubbleReceived = OnSurfaceVariantDark,
)