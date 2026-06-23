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
import com.greybox.projectmesh.navigation.AppNavHost

import com.greybox.projectmesh.views.RequestPermissionsScreen
import com.greybox.projectmesh.BuildConfig
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
                        AppNavHost(
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

