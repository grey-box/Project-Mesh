package com.greybox.projectmesh.navigation

import android.content.SharedPreferences
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
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.user.UserRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.compose.withDI
import org.kodein.di.instance
import timber.log.Timber
import com.greybox.projectmesh.util.isIpv4
import com.greybox.projectmesh.data.SettingsRepository
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.greybox.projectmesh.R

@Composable
fun AppNavHost(
    di: DI,
    startDestination: String,
    onScreenChanged: (String) -> Unit,         
) = withDI(di)
{
    val settings: SettingsRepository by di.instance()
    val appServer: AppServer by di.instance()
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
            composable(BottomNavItem.Home.route) { 
                val deviceName by settings.deviceName.collectAsState()
                HomeScreen(deviceName = deviceName) }

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
            composable(BottomNavItem.Receive.route) { ReceiveScreen(
                onAutoFinishChange = { settings.setAutoFinish(it) }
            ) }
            composable(BottomNavItem.Log.route) {
                LogScreen()
            }
            composable(BottomNavItem.Settings.route) {
                // Retrieve required instances from DI with explicit types
                val settingsPrefs: SharedPreferences by di.instance<SharedPreferences>(tag = "settings")
                val userRepository: UserRepository by di.instance<UserRepository>()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                SettingsScreen(
                    onThemeChange = settings::setTheme,
                    onLanguageChange = settings::setLanguage,
                    onRestartServer = {
                        scope.launch {
                            appServer.restart()
                            Toast.makeText(context,
                                        context.getString(R.string.server_restart_complete),
                                        Toast.LENGTH_SHORT).show() }
                     },

                    onDeviceNameChange = { newDeviceName -> settings.setDeviceName(newDeviceName)
                        Timber.tag("BottomNavApp").d("Device name changed to: $newDeviceName")
                        // Retrieve the local UUID from SharedPreferences
                        val localUuid = settingsPrefs.getString("UUID", null)
                        if (localUuid != null) {
                            // Update the local user info and broadcast in an IO coroutine
                            CoroutineScope(Dispatchers.IO).launch {
                                // 1. Update the local user in the database
                                userRepository.insertOrUpdateUser(
                                    uuid = localUuid,
                                    name = newDeviceName,
                                    address = appServer.localVirtualAddr.hostAddress  // <-- local IP here
                                )
                                Timber.tag("BottomNavApp").d("Updated local user with new name:$newDeviceName")

                                // 2. Retrieve all connected users (those with a non-null address)
                                val connectedUsers = userRepository.getAllConnectedUsers()
                                connectedUsers.forEach { user ->
                                    user.address?.let { ip ->
                                        try {
                                            val remoteAddr = InetAddress.getByName(ip)
                                            Timber.tag("BottomNavApp").d("Broadcasting updated info to: $ip")
                                            appServer.pushUserInfoTo(remoteAddr)
                                        } catch (e: Exception) {
                                            Timber.tag("BottomNavApp").e(e, "Error processing IP " +
                                                    "address:$ip")
                                        }
                                    }
                                }
                            }
                        } else {
                            Timber.tag("BottomNavApp").e("Local UUID not found; cannot update user")
                        }
                    },
                    onAutoFinishChange = { settings.setAutoFinish(it) },
                    onSaveToFolderChange = settings::setSaveToFolder,
                )
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