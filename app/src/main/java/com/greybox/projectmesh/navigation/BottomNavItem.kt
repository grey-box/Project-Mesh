package com.greybox.projectmesh.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a single item in the bottom navigation bar.
 *
 * Each item has a route (for navigation), a title (displayed as text),
 * and an icon (displayed visually in the bar).
 */
sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    /** Home tab item */
    data object Home : BottomNavItem("home", "Home", Icons.Default.Home)

    /** Network tab item */
    data object Network : BottomNavItem("network", "Network", Icons.Default.Wifi)

    /** Send tab item */
    data object Send : BottomNavItem("send", "Send", Icons.AutoMirrored.Filled.Send)

    /** Receive tab item */
    data object Receive : BottomNavItem("receive", "Receive", Icons.Default.Download)

    /** Log tab item */
    data object Log: BottomNavItem("log", "Log", Icons.Default.History)

    /** Settings tab item */
    data object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)

    /** Chat tab item */
    data object Chat : BottomNavItem("chat", "Chat", Icons.Default.ChatBubble)
}
