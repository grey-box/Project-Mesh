package com.greybox.projectmesh.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.R
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.arch.UserMessage
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber
import java.net.InetAddress

@Immutable
data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val languageCode: String = "en",
    val deviceName: String = "",
    val autoFinish: Boolean = false,
    val saveToFolder: String = "",
    val showDeviceNameDialog: Boolean = false,
    val userMessage: UserMessage? = null,
)

class SettingsScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle,
) : StateViewModel<SettingsUiState>(SettingsUiState()), DIAware {

    override val di = di
    private val settings: SettingsRepository by instance()
    private val appServer: AppServer by instance()
    private val userRepository: UserRepository by instance()

    /** combine() takes ≤5 flows; carrier groups the five mirrored repo fields. */
    private data class FormSettings(
        val theme: AppTheme, val languageCode: String, val deviceName: String,
        val autoFinish: Boolean, val saveToFolder: String,
    )

    init {
        combine(
            settings.theme, settings.languageCode, settings.deviceName,
            settings.autoFinish, settings.saveToFolder,
        ) { theme, lang, name, autoFinish, folder ->
            FormSettings(theme, lang, name, autoFinish, folder)
        }.onEach { s ->
            // Mirror ONLY repo-owned fields; never touch showDeviceNameDialog/userMessage.
            setState {
                copy(
                    theme = s.theme, languageCode = s.languageCode, deviceName = s.deviceName,
                    autoFinish = s.autoFinish, saveToFolder = s.saveToFolder,
                )
            }
        }.launchIn(viewModelScope)
    }

    // --- simple pass-throughs to the repository (single writer) ---
    fun setTheme(theme: AppTheme) = settings.setTheme(theme)
    fun setLanguage(code: String) = settings.setLanguage(code)
    fun setAutoFinish(value: Boolean) = settings.setAutoFinish(value)
    fun setSaveToFolder(path: String) = settings.setSaveToFolder(path)

    // --- pure UI state for the rename dialog ---
    fun showDeviceNameDialog() = setState { copy(showDeviceNameDialog = true) }
    fun dismissDeviceNameDialog() = setState { copy(showDeviceNameDialog = false) }

    /** Write the new name, then broadcast it to every connected peer (off the main thread). */
    fun setDeviceName(newDeviceName: String) {
        settings.setDeviceName(newDeviceName)
        Timber.tag("SettingsVM").d("Device name changed to: $newDeviceName")
        val localUuid = settings.localUuid
        if (localUuid == null) {
            Timber.tag("SettingsVM").e("Local UUID not found; cannot update user")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            userRepository.insertOrUpdateUser(
                uuid = localUuid,
                name = newDeviceName,
                address = appServer.localVirtualAddr.hostAddress,
            )
            userRepository.getAllConnectedUsers().forEach { user ->
                user.address?.let { ip ->
                    try {
                        appServer.pushUserInfoTo(InetAddress.getByName(ip))
                    } catch (e: Exception) {
                        Timber.tag("SettingsVM").e(e, "Error processing IP address:$ip")
                    }
                }
            }
        }
    }

    /** Restart the embedded server off the main thread, then surface a one-shot toast. */
    fun restartServer() {
        viewModelScope.launch(Dispatchers.IO) {
            appServer.restart()
            setState { copy(userMessage = UserMessage(R.string.server_restart_complete)) }
        }
    }

    fun resetConcurrency() {
        settings.setConcurrency(known = false, supported = true)
        setState { copy(userMessage = UserMessage(R.string.concurrency_reset)) }
    }

    fun onUserMessageShown() = setState { copy(userMessage = null) }
}
