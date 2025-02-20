package com.greybox.projectmesh

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
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

class MainActivity : ComponentActivity(), DIAware {
    override val di by closestDI()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingPref: SharedPreferences by di.instance(tag="settings")
        val appServer: AppServer by di.instance()

        //Initialize test device:
        TestDeviceService.initialize()
        Log.d("MainActivity", "Test device initialized")
        // 1) Check if this is the first launch
        val meshPrefs = getSharedPreferences("project_mesh_prefs", MODE_PRIVATE)
        val hasRunBefore = meshPrefs.getBoolean("hasRunBefore", false)

        // 2) Decide the initial route for the NavHost
        //    If it's first run, go to "onboarding"; else use the currentScreen
        val initialRoute = if (!hasRunBefore) {
            "onboarding"
        } else {
            // Could be whatever your "normal" start route is (e.g. Home)
            BottomNavItem.Home.route
        }
        setContent {
            // check if the default directory exist (Download/Project Mesh)
            val defaultDirectory = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS),
                "Project Mesh"
            )
            if (!defaultDirectory.exists()) {
                // Create the directory if it doesn't exist
                if (defaultDirectory.mkdirs()) {
                    Log.d("DirectoryCheck", "Default directory created: ${defaultDirectory.absolutePath}")
                }
                else {
                    Log.e("DirectoryCheck", "Failed to create default directory: ${defaultDirectory.absolutePath}")
                }
            }
            else {
                Log.d("DirectoryCheck", "Default directory already exists: ${defaultDirectory.absolutePath}")
            }
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
            var currentScreen by rememberSaveable { mutableStateOf(initialRoute) }
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
        // crash screen
        CrashHandler.init(applicationContext,CrashScreenActivity::class.java)
        if (!isBatteryOptimizationDisabled(this)) {
            promptDisableBatteryOptimization(this)
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
                NetworkScreen()
            }
            composable("chatScreen/{ip}"){ entry ->
                val ip = entry.arguments?.getString("ip")
                    ?: throw IllegalArgumentException("Invalid address")
                ChatScreen(
                    virtualAddress = InetAddress.getByName(ip),
                    onClickButton = {
                        navController.navigate("pingScreen/${ip}")
                    }
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
            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    onThemeChange = onThemeChange,
                    onLanguageChange = onLanguageChange,
                    onRestartServer = onRestartServer,
                    onDeviceNameChange = onDeviceNameChange,
                    onAutoFinishChange = onAutoFinishChange,
                    onSaveToFolderChange = onSaveToFolderChange
                )
            }

            composable("onboarding") {
                OnboardingScreen(
                    onComplete = {
                        // e.g. navigate to "home"
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }


            //I'm guessing I can put my Chat button here?
            composable(BottomNavItem.Chat.route) {
                // Show a list of nodes, let the user pick one
                ChatNodeListScreen(
                    onNodeSelected = { ip ->
                        // Once a node is tapped, navigate to the actual chat
                        navController.navigate("chatScreen/$ip")
                    }
                )
            }
        }
    }
}

/*fun isipvalid(theip:String): Boolean{//this is a function for checking if the IP address is valid, if this is redundant let me know and I'll make changes
    try{
        InetAddress.getByName(theip)
        return true
    }catch(e: Exception)
    { return false}
}*/

@SuppressLint("ServiceCast", "ObsoleteSdkInt")
fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true // Battery optimization doesn't apply below Android 6.0
    }
}

@SuppressLint("ObsoleteSdkInt")
fun promptDisableBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        AlertDialog.Builder(context)
            .setTitle("Disable Battery Optimization")
            .setMessage(
                "To ensure uninterrupted background functionality and maintain a stable connection," +
                        " please disable battery optimization for this app."
            )
            .setPositiveButton("Go to Settings") { _, _ ->
                try {
                    // Navigate to Battery Optimization Settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to App Info screen
                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    context.startActivity(appSettingsIntent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

