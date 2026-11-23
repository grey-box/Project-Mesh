package com.greybox.projectmesh.navigation

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.greybox.projectmesh.R

import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.CappedFontScale
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

//Preview is to show the bottom navigation bar in the preview and notice what it looks like
@Preview(showBackground = true, name = "Light")
@Composable
fun BottomNavigationBarPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
        BottomNavigationBar(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
fun BottomNavigationBarDarkPreview() {
    ProjectMeshTheme(appTheme = AppTheme.DARK) {
        BottomNavigationBar(navController = rememberNavController())
    }
}

@Preview(showBackground = true, name = "Large font", fontScale = 2f)
@Composable
fun BottomNavigationBarLargeFontPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
        BottomNavigationBar(navController = rememberNavController())
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val context = LocalContext.current
    val isDebuggable = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val items = buildList {
        add(NavigationItem(BottomNavItem.Home.route, stringResource(id = R.string.home), BottomNavItem.Home.icon))
        add(NavigationItem(BottomNavItem.Network.route, stringResource(id = R.string.network), BottomNavItem.Network.icon))
        add(NavigationItem(BottomNavItem.Send.route, stringResource(id = R.string.send), BottomNavItem.Send.icon))
        add(NavigationItem(BottomNavItem.Receive.route, stringResource(id = R.string.receive), BottomNavItem.Receive.icon))
        add(NavigationItem(BottomNavItem.Chat.route, stringResource(id = R.string.chat), BottomNavItem.Chat.icon))

        // ✅ Only add the log tab if it's a DEBUG build
        if (isDebuggable) {
            add(NavigationItem(BottomNavItem.Log.route, stringResource(id = R.string.log), BottomNavItem.Log.icon))
        }

        add(NavigationItem(BottomNavItem.Settings.route, stringResource(id = R.string.settings), BottomNavItem.Settings.icon))
    }
    // Labels get fixed slots, so the bar only scales up to a cap
    CappedFontScale {
        NavigationBar {
            val currentRoute = navController.currentDestination?.route
            items.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    // Inside the 115% cap, a long label still shrinks to fit its tab
                    // rather than ellipsizing ("Settin…"). See NavLabel.
                    label = { NavLabel(item.label) },
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            // Avoid multiple copies of the same destination when reselecting the same item
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                            // Avoid multiple copies of the same destination when reselecting the same item
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

/**
 * A tab label that stays on one line and shrinks only as far as it must to fit its tab.
 *
 * Seven tabs (six in release) leave about 58dp per label on a typical phone, and that
 * runs out the moment the system text size goes above 100%. The two simple options
 * both failed: `Clip` cut words off ("Setti"), and letting the label wrap split them
 * mid-word ("Net / wor / k"), because single words have no break point.
 *
 * So each label is measured at the user's chosen size and scaled down to the tab
 * width, but never below its normal 100% size. Short labels ("Log", "Chat") keep the
 * full enlarged size; only the long ones shrink. This means nav labels do not reach
 * the full 200% that SC 1.4.4 asks for. That was a deliberate trade-off, made with the
 * team on 2026-11-08: a readable label beats a split one. The real fix is fewer tabs.
 *
 * The whole bar also sits inside CappedFontScale (115%), so the bar's height stays
 * bounded; this label then shrinks within that cap. The floor below is still "normal
 * size", because the fontScale read here is the capped one.
 * TalkBack is unaffected, because the label is also the icon's contentDescription.
 */
@Composable
private fun NavLabel(text: String) {
    // Zero tracking (from Tomas, 2026-11-10): labelSmall's default letter spacing costs a
    // few dp per label, which on seven tabs is the difference between fitting and shrinking.
    val style = MeshTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    val measurer = rememberTextMeasurer()
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val fitted = remember(text, style, constraints.maxWidth, fontScale) {
            if (!constraints.hasBoundedWidth) return@remember style
            val natural = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            if (natural <= constraints.maxWidth) return@remember style
            // 0.97 leaves room for rounding, so the label never lands one pixel over.
            val ratio = constraints.maxWidth * 0.97f / natural
            // fontSize is in sp, which already includes fontScale, so 1/fontScale is the
            // ratio that brings the label back to exactly its normal size.
            val floor = 1f / fontScale.coerceAtLeast(1f)
            val scale = ratio.coerceAtLeast(floor)
            // Letter spacing is in sp too and grows with the text setting, so it has to
            // shrink by the same factor or the label stays a few pixels too wide.
            style.copy(
                fontSize = style.fontSize * scale,
                letterSpacing = style.letterSpacing * scale
            )
        }
        Text(
            text = text,
            style = fitted,
            maxLines = 1,
            softWrap = false,
            // Reached only if a label is too long even at normal size, for example a long
            // translation. An ellipsis at least shows that something was cut.
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}