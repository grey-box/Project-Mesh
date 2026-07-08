package com.greybox.projectmesh

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.app.AppViewModel
import com.greybox.projectmesh.app.DeepLink
import com.greybox.projectmesh.debug.CrashHandler
import com.greybox.projectmesh.debug.CrashScreenActivity
import com.greybox.projectmesh.logging.ReleaseTree
import com.greybox.projectmesh.navigation.AppNavHost
import com.greybox.projectmesh.navigation.BottomNavItem
import com.greybox.projectmesh.testing.TestDeviceService
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import com.greybox.projectmesh.util.LocaleUtil
import com.greybox.projectmesh.views.OnboardingScreen
import com.greybox.projectmesh.views.RequestPermissionsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI
import org.kodein.di.compose.localDI
import timber.log.Timber
import java.io.File


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

