package com.greybox.projectmesh

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.greybox.projectmesh.debug.CrashHandler
import com.greybox.projectmesh.debug.CrashScreenActivity
import com.greybox.projectmesh.navigation.BottomNavItem
import com.greybox.projectmesh.navigation.BottomNavigationBar
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import com.greybox.projectmesh.viewModel.SharedUriViewModel
import com.greybox.projectmesh.messaging.ui.screens.ChatScreen
import com.greybox.projectmesh.views.HomeScreen
import com.greybox.projectmesh.views.SettingsScreen
import com.greybox.projectmesh.views.NetworkScreen
import com.greybox.projectmesh.views.PingScreen
import com.greybox.projectmesh.views.ReceiveScreen
import com.greybox.projectmesh.views.SelectDestNodeScreen
import com.greybox.projectmesh.views.SendScreen
import com.greybox.projectmesh.views.OnboardingScreen
import com.greybox.projectmesh.testing.TestDeviceService
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.compose.withDI
import org.kodein.di.instance
import java.io.File
import java.util.Locale
import java.net.InetAddress
import com.greybox.projectmesh.messaging.ui.screens.ChatNodeListScreen
import com.greybox.projectmesh.messaging.ui.screens.ConversationsHomeScreen
import com.greybox.projectmesh.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.lifecycleScope
import com.greybox.projectmesh.logging.ReleaseTree
import kotlinx.coroutines.launch
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.ui.viewmodels.ChatScreenViewModel
import com.greybox.projectmesh.views.LogScreen


import com.greybox.projectmesh.views.RequestPermissionsScreen
import org.kodein.di.android.BuildConfig
import org.kodein.di.compose.localDI

import timber.log.Timber

class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ProjectMesh)
        super.onCreate(savedInstanceState)

        // Init Timber for Debug Build
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }

        Timber.d("Timber init in ${BuildConfig.BUILD_TYPE}")

        // crash screen
        CrashHandler.init(applicationContext, CrashScreenActivity::class.java)
        val settingPref: SharedPreferences by di.instance(tag = "settings")
        val appServer: AppServer by di.instance()
        // Run this task asynchronously (default directory creation)
        lifecycleScope.launch(Dispatchers.IO) {
            ensureDefaultDirectory()
        }
        //Initialize test device:
        TestDeviceService.initialize()
        Timber.tag("MainActivity").d("Test device initialized")

        setContent {
            val meshPrefs = getSharedPreferences("project_mesh_prefs", MODE_PRIVATE)
            var hasRunBefore by rememberSaveable {
                mutableStateOf(meshPrefs.getBoolean("hasRunBefore", false))
            }
            // Check if the app was launched from a notification
            val launchedFromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
            // Request all permission in order
            RequestPermissionsScreen(skipPermissions = launchedFromNotification)
            var appTheme by remember {
                mutableStateOf(AppTheme.valueOf(
                    settingPref.getString("app_theme", AppTheme.SYSTEM.name) ?:
                    AppTheme.SYSTEM.name))
            }
            var languageCode by remember {
                mutableStateOf(settingPref.getString(
                    "language", "en") ?: "en")
            }
            var restartServerKey by remember {mutableStateOf(0)}
            var deviceName by remember {
                mutableStateOf(settingPref.getString("device_name", Build.MODEL) ?: Build.MODEL)
            }

            var autoFinish by remember {
                mutableStateOf(settingPref.getBoolean("auto_finish", false))
            }

            var saveToFolder by remember {
                mutableStateOf(
                    settingPref.getString("save_to_folder", null)
                        ?: "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Project Mesh"
                )
            }

            // State to trigger recomposition when locale changes
            var localeState by rememberSaveable { mutableStateOf(Locale.getDefault()) }

            // Remember the current screen across recompositions
            var currentScreen by rememberSaveable { mutableStateOf(BottomNavItem.Home.route) }
            LaunchedEffect(intent?.getStringExtra("navigateTo")) {
                if (intent?.getStringExtra("navigateTo") == BottomNavItem.Receive.route) {
                    currentScreen = BottomNavItem.Receive.route
                }
            }

            //check for special navigation intents
            val action = intent.action
            if (action == "OPEN_CHAT_SCREEN") {
                val ip = intent.getStringExtra("ip")
                if (ip != null) {
                    try {
                        // Try to create InetAddress to validate it first
                        InetAddress.getByName(ip)
                        // If that succeeds, navigate to chat screen with this IP
                        val route = "chatScreen/$ip"
                        currentScreen = route
                    } catch (e: Exception) {
                        Timber.tag("MainActivity").e(e, "Invalid IP address in intent: %s", ip)
                        // Fall back to home screen
                        currentScreen = BottomNavItem.Home.route
                    }
                }
            } else if (action == "OPEN_CHAT_CONVERSATION") {
                val conversationId = intent.getStringExtra("conversationId")
                if (conversationId != null) {
                    // Navigate to chat screen with this conversation ID
                    val route = "chatScreen/$conversationId"
                    currentScreen = route
                }
            }

            LaunchedEffect(restartServerKey) {
                if (restartServerKey > 0){
                    appServer.restart()
                    Toast.makeText(this@MainActivity, "Server restart complete", Toast.LENGTH_SHORT).show()
                }
            }
            // Observe language changes and apply locale
            LaunchedEffect(languageCode) {
                localeState = updateLocale(languageCode)
            }
            key(localeState) {
                ProjectMeshTheme(appTheme = appTheme) {
                    if (!hasRunBefore) {
                        OnboardingScreen(
                            onComplete = {meshPrefs.edit().putBoolean("hasRunBefore", true).apply()
                                hasRunBefore = true }
                        )
                    }
                    else{
                        BottomNavApp(
                            di,
                            startDestination = currentScreen,
                            onThemeChange = { selectedTheme -> appTheme = selectedTheme},
                            onLanguageChange = { selectedLanguage ->  languageCode = selectedLanguage},
                            onNavigateToScreen = {screen ->
                                currentScreen = screen },
                            onRestartServer = {restartServerKey++},
                            onDeviceNameChange = {deviceName = it},
                            deviceName = deviceName,
                            onAutoFinishChange = {autoFinish = it},
                            onSaveToFolderChange = {saveToFolder = it}
                        )
                    }
                }
            }
        }
        Timber.tag("BuildCheck").d("Build Type is: ${BuildConfig.BUILD_TYPE}")
    }

    private fun ensureDefaultDirectory() {
        val defaultDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Project Mesh"
        )
        if (!defaultDirectory.exists()) {
            // Create the directory if it doesn't exist
            if (defaultDirectory.mkdirs()) {
                Timber.tag("DirectoryCheck").d("Default directory created: ${defaultDirectory
                    .absolutePath}")
            }
            else {
                Timber.tag("DirectoryCheck").e("Failed to create default directory: %s", defaultDirectory.absolutePath)
            }
        }
        else {
            Timber.tag("DirectoryCheck").d("Default directory already exists: ${defaultDirectory
                .absolutePath}")
        }
    }

    private fun updateLocale(languageCode: String): Locale {
        val locale = Locale(languageCode)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        return locale
    }
}

@Composable
fun BottomNavApp(di: DI,
                 startDestination: String,
                 onThemeChange: (AppTheme) -> Unit,
                 onLanguageChange: (String) -> Unit,
                 onNavigateToScreen: (String) -> Unit,
                 onRestartServer: () -> Unit,
                 onDeviceNameChange: (String) -> Unit,
                 deviceName: String,
                 onAutoFinishChange: (Boolean) -> Unit,
                 onSaveToFolderChange: (String) -> Unit
) = withDI(di)
{
    val appServer: AppServer by di.instance()
    val navController = rememberNavController()
    // Observe the current route directly through the back stack entry
    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = null)

    LaunchedEffect(currentRoute.value?.destination?.route) {
        if(currentRoute.value?.destination?.route == BottomNavItem.Settings.route){
            currentRoute.value?.destination?.route?.let { route ->
                onNavigateToScreen(route)
            }
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ){ innerPadding ->
        NavHost(navController, startDestination = startDestination, Modifier.padding(innerPadding))
        {
            composable(BottomNavItem.Home.route) { HomeScreen(deviceName = deviceName) }
            composable(BottomNavItem.Network.route) {
                // Just call NetworkScreen with no click callback
                NetworkScreen(
                    onNodeClick = {ip -> navController.navigate("pingScreen/$ip")}
                )
            }
            composable("chatScreen/{ip}"){ entry ->
                val ip = entry.arguments?.getString("ip")
                    ?: throw IllegalArgumentException("Invalid address")

                Timber.tag("Navigation").d("Navigating to chatScreen with parameter: $ip")

                //determine if this is an ip address
                val isValidIpAddress = ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$"))
                Timber.tag("Navigation").d("Is valid IP address: $isValidIpAddress")


                if(isValidIpAddress) {
                    Timber.tag("Navigation").d("Showing chat for IP address: $ip")
                    // This is a valid IP address, use normal chat screen
                    ChatScreen(
                        virtualAddress = InetAddress.getByName(ip),
                        onClickButton = {
                            navController.navigate("pingScreen/${ip}")
                        }
                    )
                } else {
                    Timber.tag("Navigation").d("Showing chat for conversation ID: $ip")
                    //conversation id: handle offline chat
                    ConversationChatScreen(
                        conversationId = ip,
                        onBackClick = {
                            Timber.tag("Navigation").d("Navigating back from conversation")
                            navController.popBackStack()
                        }
                    )
                }
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
                        Log.d("uri_track_nav_send", "size: " + uris.size.toString())
                        Log.d("uri_track_nav_send", "List: $uris")
                        sharedUrisViewModel.setUris(uris)
                        navController.navigate("selectDestNode")
                    }
                )
            }
            composable("selectDestNode"){
                val activity = LocalContext.current as ComponentActivity
                val sharedUrisViewModel: SharedUriViewModel = viewModel(activity)
                val sendUris by sharedUrisViewModel.uris.collectAsState()
                Log.d("uri_track_nav_selectDestNode", "size: " + sendUris.size.toString())
                Log.d("uri_track_nav_selectDestNode", "List: $sendUris")
                SelectDestNodeScreen(
                    uris = sendUris,
                    popBackWhenDone = {navController.popBackStack()},
                )
            }
            composable(BottomNavItem.Receive.route) { ReceiveScreen(
                onAutoFinishChange = onAutoFinishChange
            ) }
            composable(BottomNavItem.Log.route) {
                LogScreen()
            }
            composable(BottomNavItem.Settings.route) {
                // Retrieve required instances from DI with explicit types
                val settingsPrefs: SharedPreferences by di.instance<SharedPreferences>(tag = "settings")
                val userRepository: UserRepository by di.instance<UserRepository>()
                SettingsScreen(
                    onThemeChange = onThemeChange,
                    onLanguageChange = onLanguageChange,
                    onRestartServer = onRestartServer,
                    onDeviceNameChange = { newDeviceName ->
                        Log.d("BottomNavApp", "Device name changed to: $newDeviceName")
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
                                Log.d("BottomNavApp", "Updated local user with new name: $newDeviceName")

                                // 2. Retrieve all connected users (those with a non-null address)
                                val connectedUsers = userRepository.getAllConnectedUsers()
                                connectedUsers.forEach { user ->
                                    user.address?.let { ip ->
                                        try {
                                            val remoteAddr = InetAddress.getByName(ip)
                                            Log.d("BottomNavApp", "Broadcasting updated info to: $ip")
                                            appServer.pushUserInfoTo(remoteAddr)
                                        } catch (e: Exception) {
                                            Log.e("BottomNavApp", "Error processing IP address: $ip", e)
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.e("BottomNavApp", "Local UUID not found; cannot update user")
                        }
                    },
                    onAutoFinishChange = onAutoFinishChange,
                    onSaveToFolderChange = onSaveToFolderChange,
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
                        Log.d("Navigation", "Selected conversation/user: $userIdentifier")
                        //navigate to the chat screen with this identifier
                        navController.navigate("chatScreen/${userIdentifier}")
                    }
                )
                /*
                ChatNodeListScreen(
                    onNodeSelected = { ip ->
                        val remoteAddr = InetAddress.getByName(ip)
                        Log.d("ChatHandshake", "Node selected with IP: $ip, remoteAddr: $remoteAddr")

                        // Request remote user info
                        Log.d("ChatHandshake", "Requesting remote user info from: ${remoteAddr.hostAddress}")
                        appServer.requestRemoteUserInfo(remoteAddr)

                        // Push local user info to remote node
                        Log.d("ChatHandshake", "Pushing local user info to: ${remoteAddr.hostAddress}")
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
                    val isValidIpAddress = ipParam.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$"))

                    val validatedAddress = remember(ipParam) {
                        if (isValidIpAddress) {
                            try {
                                InetAddress.getByName(ipParam)
                            } catch (e: Exception) {
                                Log.e("Navigation", "Error creating address from parameter: $ipParam", e)
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


@Composable
fun ConversationChatScreen (
        conversationId: String,
        onBackClick: () -> Unit
){
    Log.d("ConversationChatScreen", "Starting to load conversation: $conversationId")

    var conversationState = remember { mutableStateOf<Conversation?>(null) }
    var isLoading = remember { mutableStateOf(true) }
    var errorMessage = remember { mutableStateOf<String?>(null) }
    //val conversation = conversationState.value
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    // Load the conversation data
    LaunchedEffect(conversationId) {
        Log.d("ConversationChatScreen", "LaunchedEffect triggered for ID: $conversationId")
        coroutineScope.launch {
            try {
                Log.d("ConversationChatScreen", "Attempting to fetch conversation")
                val result = GlobalApp.GlobalUserRepo.conversationRepository.getConversationById(conversationId)
                Log.d("ConversationChatScreen", "Fetch result: ${result != null}")
                //conversationState.value = result

                if (result == null) {
                    errorMessage.value = "Conversation not found"
                    isLoading.value = false
                    /*
                    Log.e("ConversationChatScreen", "Conversation not found: $conversationId")
                    Toast.makeText(
                        context,
                        "Conversation not found",
                        Toast.LENGTH_SHORT
                    ).show()
                    onBackClick()
                    */
                }else {
                    conversationState.value = result
                    isLoading.value = false
                    Log.d("ConversationChatScreen", "Loaded conversation: ${result.userName}, online=${result.isOnline}")
                }
            } catch (e: Exception) {
                Log.e("ConversationChatScreen", "Error loading conversation", e)
                errorMessage.value = e.message
                isLoading.value = false
                /*
                Toast.makeText(
                    context,
                    "Error loading conversation: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                onBackClick()
                 */
            }
        }
    }

    // Show loading state while fetching conversation
    if (isLoading.value) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    //show error if loading failed
    if (errorMessage.value != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error: ${errorMessage.value}")
                Button(onClick = onBackClick) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    //get the conversation from state
    val conversation = conversationState.value
    if (conversation == null) {
        Toast.makeText(context, "Conversation not found", Toast.LENGTH_SHORT).show()
        onBackClick()
        return
    }

    val shouldCheckDeviceStatus = remember {
        // Only check for devices with real IP addresses, not placeholder or offline devices
        conversation.userAddress != null &&
                conversation.userAddress != "0.0.0.0" &&
                conversation.userAddress != TestDeviceService.TEST_DEVICE_IP_OFFLINE
    }

    // Initialize isUserOnline based on conversation state first
    var isUserOnline by remember { mutableStateOf(conversation.isOnline) }

    // If we should check device status, observe DeviceStatusManager
    if (shouldCheckDeviceStatus) {
        val deviceStatusMap by DeviceStatusManager.deviceStatusMap.collectAsState()
        // Update isUserOnline based on DeviceStatusManager
        LaunchedEffect(deviceStatusMap) {
            conversation.userAddress?.let { ipAddress ->
                val statusFromManager = deviceStatusMap[ipAddress] ?: false
                if (isUserOnline != statusFromManager) {
                    Log.d("ConversationChatScreen", "Device status changed for ${conversation.userName}: ${if (statusFromManager) "online" else "offline"}")
                    isUserOnline = statusFromManager
                }
            }
        }
    }

    // Create a virtual address based on the conversation data
    val virtualAddress = if (conversation?.userAddress.isNullOrEmpty()) {
        //use offline test device address if this is the offline test conversation
        if (conversation.userName == TestDeviceService.TEST_DEVICE_NAME_OFFLINE) {
            Log.d("ConversationChatScreen", "Using offline test device address")
            InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP_OFFLINE)
        } else {
            //use a placeholder address for regular offline users
            Log.d("ConversationChatScreen", "Using placeholder address for offline user")
            InetAddress.getByName("0.0.0.0")
        }
    } else {
        //use the actual address if available
        Log.d("ConversationChatScreen", "Using actual address: ${conversation.userAddress}")
        InetAddress.getByName(conversation.userAddress)
    }

    Log.d("ConversationChatScreen", "Showing chat screen for: ${conversation.userName}")

    // Show the chat screen
    ChatScreen(
        virtualAddress = virtualAddress,
        userName = conversation.userName,
        isOffline = !conversation.isOnline,
        onClickButton = {
            // Disable ping for offline users
            Toast.makeText(
                context,
                "Cannot ping offline users",
                Toast.LENGTH_SHORT
            ).show()
        },
        viewModel = viewModel(
            factory = ViewModelFactory(
                di = localDI(),
                owner = LocalSavedStateRegistryOwner.current,
                vmFactory = { di, savedStateHandle ->
                    // Make sure to set the conversation ID in the savedStateHandle
                    savedStateHandle["conversationId"] = conversationId
                    ChatScreenViewModel(di, savedStateHandle)
                },
                defaultArgs = Bundle().apply {
                    putSerializable("virtualAddress", virtualAddress)
                    putString("conversationId", conversationId)
                }
            )
        )
    )
}

