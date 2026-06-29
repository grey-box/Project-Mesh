package com.greybox.projectmesh

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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
import android.os.Environment
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.ui.viewmodels.ChatScreenViewModel
import com.greybox.projectmesh.views.LogScreen
import com.greybox.projectmesh.navigation.AppNavHost
import com.greybox.projectmesh.views.RequestPermissionsScreen
import com.greybox.projectmesh.BuildConfig
import org.kodein.di.compose.localDI
import timber.log.Timber
import com.greybox.projectmesh.app.AppViewModel
import com.greybox.projectmesh.app.DeepLink
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.util.LocaleUtil
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
  
        // Run this task asynchronously (default directory creation)
        lifecycleScope.launch(Dispatchers.IO) {
            ensureDefaultDirectory()
        }
        //Initialize test device:
        TestDeviceService.initialize()
        Timber.tag("MainActivity").d("Test device initialized")

        setContent {
            val launchedFromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
            RequestPermissionsScreen(skipPermissions = launchedFromNotification)

            val appViewModel: AppViewModel = viewModel(
                factory = ViewModelFactory(
                    di = localDI(),
                    owner = LocalSavedStateRegistryOwner.current,
                    vmFactory = { di, savedStateHandle -> AppViewModel(di, savedStateHandle) },
                    defaultArgs = bundleOf(),
                )
            )

            LaunchedEffect(Unit) {appViewModel.applyDeepLink(parseDeepLink(intent)) }

            val ui by appViewModel.state.collectAsStateWithLifecycle()
            val context = LocalContext.current
            LaunchedEffect(ui.userMessage?.id) {
                ui.userMessage?.let{
                    Toast.makeText(context, it.textRes, Toast.LENGTH_SHORT).show()
                    appViewModel.onUserMessageShown()
                }
            }

            val locale = LocaleUtil.applyLocale(context, ui.languageCode)
            key(locale) {
                ProjectMeshTheme(appTheme = ui.theme) {
                    if (!ui.hasRunBefore) {
                        OnboardingScreen(onComplete = appViewModel::completeOnboarding)
                    } else {
                        AppNavHost(di, ui.startDestination, onScreenChanged = appViewModel::onScreenChanged)
                    }
                }
            }
        }
        Timber.tag("BuildCheck").d("Build Type is: ${BuildConfig.BUILD_TYPE}")
    }

    private fun parseDeepLink(intent: Intent): DeepLink? = when {
        intent.getStringExtra("navigateTo") == BottomNavItem.Receive.route -> DeepLink.OpenReceive
        intent.action == "OPEN_CHAT_SCREEN" ->
            intent.getStringExtra("ip")?.let { DeepLink.OpenChat(it) }
        intent.action == "OPEN_CHAT_CONVERSATION" ->
            intent.getStringExtra("conversationId")?.let { DeepLink.OpenConversation(it) }
        else -> null
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
}

