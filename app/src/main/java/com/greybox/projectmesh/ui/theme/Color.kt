package com.greybox.projectmesh.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

//Keep above colors once nothing references them, then delete.

//For Primary, Secondary, Tertiary, Error:
//    Tone 40 represents main color
//    Tone 90 represents the container
//    Tone 10 represents content on containers
// Primary (green). Numbers are tone steps: 10 = darkest, 100 = white.
// Dark scheme flips these: 80 main, 30 container, 90 content on container, 20 content on main (Text).

// Primary
val Primary10 = Color(0xFF002204)
val Primary20 = Color(0xFF003908)
val Primary30 = Color(0xFF005312)
val Primary40 = Color(0xFF006E1C)
val Primary80 = Color(0xFF81DE80)
val Primary90 = Color(0xFF9CFB99)

// Secondary
val Secondary10 = Color(0xFF0C1F0C)
val Secondary20 = Color(0xFF213420)
val Secondary30 = Color(0xFF374B35)
val Secondary40 = Color(0xFF4E634A)
val Secondary80 = Color(0xFFB4CDAD)
val Secondary90 = Color(0xFFD0E9C8)

// Tertiary (mesh / network accent)
val Tertiary10 = Color(0xFF001F26)
val Tertiary20 = Color(0xFF00363F)
val Tertiary30 = Color(0xFF004E5A)

val Tertiary40 = Color(0xFF006876)
val Tertiary80 = Color(0xFF6FD8EB)
val Tertiary90 = Color(0xFFA9EDFF)

// Error
val Error10 = Color(0xFF410002)
val Error20 = Color(0xFF690005)
val Error30 = Color(0xFF93000A)
val Error40 = Color(0xFFBA1A1A)
val Error80 = Color(0xFFFFB4AB)
val Error90 = Color(0xFFFFDAD6)

// ---- Everything else is named by role ----

// Light scheme
val BackgroundLight = Color(0xFFFCFDF6)
val OnBackgroundLight = Color(0xFF1A1C19)
val SurfaceLight = Color(0xFFFCFDF6)
val OnSurfaceLight = Color(0xFF1A1C19)
val SurfaceVariantLight = Color(0xFFDEE5D9)
val OnSurfaceVariantLight = Color(0xFF424940)
val SurfaceContainerLowLight = Color(0xFFF6F7F1)
val SurfaceContainerLight = Color(0xFFF0F1EB)
val SurfaceContainerHighLight = Color(0xFFEAEBE5)
val OutlineLight = Color(0xFF72796F)
val OutlineVariantLight = Color(0xFFC2C9BD)
val InverseSurfaceLight = Color(0xFF2F312D)
val InverseOnSurfaceLight = Color(0xFFF0F1EB)
val InversePrimaryLight = Color(0xFF81DE80)
val textColor = Color(0xFFFFFFFF)

// Dark scheme
val BackgroundDark = Color(0xFF1A1C19)
val OnBackgroundDark = Color(0xFFE2E3DD)
val SurfaceDark = Color(0xFF1A1C19)
val OnSurfaceDark = Color(0xFFE2E3DD)
val SurfaceVariantDark = Color(0xFF424940)
val OnSurfaceVariantDark = Color(0xFFC2C9BD)
val SurfaceContainerLowDark = Color(0xFF1F211D)
val SurfaceContainerDark = Color(0xFF232521)
val SurfaceContainerHighDark = Color(0xFF2E302C)
val OutlineDark = Color(0xFF8C9388)
val OutlineVariantDark = Color(0xFF424940)
val InverseSurfaceDark = Color(0xFFE2E3DD)
val InverseOnSurfaceDark = Color(0xFF1A1C19)
val InversePrimaryDark = Color(0xFF006E1C)

// Same in both schemes
val Scrim = Color(0xFF000000)