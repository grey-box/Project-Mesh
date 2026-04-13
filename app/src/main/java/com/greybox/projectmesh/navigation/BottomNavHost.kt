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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.greybox.projectmesh.R
import org.kodein.di.android.BuildConfig

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

//Preview is to show the bottom navigation bar in the preview and notice what it looks like
@Preview(showBackground = true)
@Composable
fun BottomNavigationBarPreview() {
    val navController = rememberNavController()
    BottomNavigationBar(navController = navController)
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
    NavigationBar {
        val currentRoute = navController.currentDestination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                //Modify label Sizes to fit the screen of multiple devices
                label = {
                    Text(
                        item.label,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                },
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