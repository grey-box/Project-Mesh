package com.greybox.projectmesh.navigation


import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.messaging.ui.screens.ChatScreen
import com.greybox.projectmesh.messaging.ui.screens.ConversationChatScreen
import com.greybox.projectmesh.messaging.ui.screens.ConversationsHomeScreen
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.viewModel.SharedUriViewModel
import com.greybox.projectmesh.views.HomeScreen
import com.greybox.projectmesh.views.LogScreen
import com.greybox.projectmesh.views.NetworkScreen
import com.greybox.projectmesh.views.PingScreen
import com.greybox.projectmesh.views.ReceiveScreen
import com.greybox.projectmesh.views.SelectDestNodeScreen
import com.greybox.projectmesh.views.SendScreen
import com.greybox.projectmesh.views.SettingsScreen
import java.net.InetAddress
import org.kodein.di.compose.withDI
import org.kodein.di.DI
import timber.log.Timber
import com.greybox.projectmesh.util.isIpv4


@Composable
fun AppNavHost(
    di: DI,
    startDestination: String,
    onScreenChanged: (String) -> Unit,         
) = withDI(di)
{
   // val appServer: AppServer by di.instance()
    val navController = rememberNavController()
    // Observe the current route directly through the back stack entry
    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null)

    LaunchedEffect(currentRoute.value?.destination?.route) {
        if(currentRoute.value?.destination?.route == BottomNavItem.Settings.route){
            currentRoute.value?.destination?.route?.let { route ->
                onScreenChanged(route)
            }
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ){ innerPadding ->
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding))
        {
            composable(BottomNavItem.Home.route) { HomeScreen() }


            composable(BottomNavItem.Network.route) {
                // Just call NetworkScreen with no click callback
                NetworkScreen(
                    onNodeClick = {ip -> navController.navigate("pingScreen/$ip")}
                )
            }
        

            composable("pingScreen/{ip}"){ entry ->
                val ip = entry.arguments?.getString("ip")
                    ?: throw IllegalArgumentException("Invalid address")
                PingScreen(
                    virtualAddress = InetAddress.getByName(ip)
                )
            }

            composable(BottomNavItem.Send.route) {
                val activity = LocalContext.current as ComponentActivity
                val sharedUrisViewModel: SharedUriViewModel = viewModel(activity)
                SendScreen(
                    onSwitchToSelectDestNode = { uris ->
                        Timber.tag("uri_track_nav_send").d("size: %s", uris.size.toString())
                        Timber.tag("uri_track_nav_send").d("List: $uris")
                        sharedUrisViewModel.setUris(uris)
                        navController.navigate("selectDestNode")
                    }
                )
            }
            composable("selectDestNode"){
                val activity = LocalContext.current as ComponentActivity
                val sharedUrisViewModel: SharedUriViewModel = viewModel(activity)
                val sendUris by sharedUrisViewModel.uris.collectAsState()
                Timber.tag("uri_track_nav_selectDestNode").d("size: %s", sendUris.size.toString())
                Timber.tag("uri_track_nav_selectDestNode").d("List: $sendUris")
                SelectDestNodeScreen(
                    uris = sendUris,
                    popBackWhenDone = {navController.popBackStack()},
                )
            }
            
            composable(BottomNavItem.Receive.route) { ReceiveScreen() }

            composable(BottomNavItem.Log.route) {
                LogScreen()
            }
            
            composable(BottomNavItem.Settings.route) {
                     SettingsScreen()
            }

            
            //I'm guessing I can put my Chat button here?
            composable(BottomNavItem.Chat.route) {
                //latest status of DeviceStatus manager
                val deviceStatusRefreshTrigger = DeviceStatusManager.deviceStatusMap.collectAsState().value
                // Show a list of nodes, let the user pick one
                ConversationsHomeScreen(
                    onConversationSelected = { userIdentifier ->
                        // userIdentifier will be either an IP address (for online users)
                        // or a conversation ID (for offline users)
                        Timber.tag("Navigation").d("Selected conversation/user: $userIdentifier")
                        //navigate to the chat screen with this identifier
                        navController.navigate("chatScreen/${userIdentifier}")
                    }
                )
                /*
                ChatNodeListScreen(
                    onNodeSelected = { ip ->
                        val remoteAddr = InetAddress.getByName(ip)
                        Timber.tag("ChatHandshake").d("Node selected with IP: $ip, remoteAddr:
                        $remoteAddr")

                        // Request remote user info
                        Timber.tag("ChatHandshake").d("Requesting remote user info from:
                        ${remoteAddr
                        .hostAddress}")
                        appServer.requestRemoteUserInfo(remoteAddr)

                        // Push local user info to remote node
                        Timber.tag("ChatHandshake").d("Pushing local user info to: ${remoteAddr
                        .hostAddress}")
                        appServer.pushUserInfoTo(remoteAddr)

                        // Navigate to the chat screen
                        navController.navigate("chatScreen/$ip")
                    }

                )
                */
            }

            //handle ip address chat screen properly
            composable("chatScreen/{ip}"){ entry ->
                val ipParam = entry.arguments?.getString("ip")
                if (ipParam == null) {
                    // Handle missing parameter
                    Text("Error: Missing address parameter")
                } else {
                    // Check if this is an IP address or conversation ID
                    val isValidIpAddress = isIpv4(ipParam)

                    val validatedAddress = remember(ipParam) {
                        if (isValidIpAddress) {
                            try {
                                InetAddress.getByName(ipParam)
                            } catch (e: Exception) {
                                Timber.tag("Navigation").e(e, "Error creating address from parameter:$ipParam")
                                null
                            }
                        } else {
                            null
                        }
                    }
                    if (isValidIpAddress && validatedAddress != null) {
                        // It's a valid IP address
                        ChatScreen(
                            virtualAddress = validatedAddress,
                            onClickButton = {
                                navController.navigate("pingScreen/${ipParam}")
                            }
                        )
                    } else {
                        // Handle as conversation ID
                        ConversationChatScreen(
                            conversationId = ipParam,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}