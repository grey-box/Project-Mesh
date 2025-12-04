package com.greybox.projectmesh.mainscreen

import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance

class MainViewModel(
    di: DI
) : ViewModel() {

    // Inject dependencies
    private val settingPrefs: SharedPreferences by di.instance(tag = "settings")
    private val meshPrefs: SharedPreferences by di.instance(tag = "mesh")
    private val node: AndroidVirtualNode by di.instance()

    // Private mutable state
    private val _uiState = MutableStateFlow(MeshUiState())

    // Public immutable state
    val uiState: StateFlow<MeshUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized")
        loadInitialState()
        observeMeshNetwork()
    }

    /**
     * Load initial state from SharedPreferences
     */
    private fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    deviceName = settingPrefs.getString("device_name", Build.MODEL) ?: Build.MODEL,
                    appTheme = settingPrefs.getString("app_theme", "SYSTEM") ?: "SYSTEM",
                    languageCode = settingPrefs.getString("language", "en") ?: "en",
                    autoFinish = settingPrefs.getBoolean("auto_finish", false),
                    saveToFolder = settingPrefs.getString("save_to_folder", null)
                        ?: "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Project Mesh",
                    hasRunBefore = meshPrefs.getBoolean("hasRunBefore", false)
                )
            }
        }
    }

    /**
     * Observe mesh network state changes
     */
    private fun observeMeshNetwork() {
        viewModelScope.launch {
            node.state.collect { nodeState ->
                _uiState.update { state ->
                    state.copy(
                        wifiState = nodeState.wifiState,
                        localAddress = nodeState.address,
                        nodesOnMesh = nodeState.originatorMessages.keys,
                        connectedNodesCount = nodeState.originatorMessages.size,
                        isNetworkActive = nodeState.wifiState.hotspotIsStarted,
                        statusMessage = if (nodeState.wifiState.hotspotIsStarted) {
                            "Mesh active - ${nodeState.originatorMessages.size} nodes"
                        } else {
                            "Mesh network inactive"
                        }
                    )
                }
            }
        }
    }

    /**
     * Handle UI events
     */
    fun onEvent(event: MeshUiEvent) {
        when (event) {
            is MeshUiEvent.StartMeshNetwork -> startMeshNetwork()
            is MeshUiEvent.StopMeshNetwork -> stopMeshNetwork()
            is MeshUiEvent.NavigateToScreen -> navigateToScreen(event.route)
            is MeshUiEvent.NavigateToChat -> navigateToChat(event.identifier)
            is MeshUiEvent.UpdateDeviceName -> updateDeviceName(event.name)
            is MeshUiEvent.UpdateTheme -> updateTheme(event.theme)
            is MeshUiEvent.UpdateLanguage -> updateLanguage(event.code)
            is MeshUiEvent.PermissionsGranted -> handlePermissions(event.granted)
            is MeshUiEvent.ClearError -> clearError()
            is MeshUiEvent.CompleteOnboarding -> completeOnboarding()
        }
    }

    private fun startMeshNetwork() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, statusMessage = "Starting mesh...") }
                // Node starting happens automatically via observeMeshNetwork
                Log.d(TAG, "Mesh network start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start mesh", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start mesh: ${e.message}"
                    )
                }
            }
        }
    }

    private fun stopMeshNetwork() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, statusMessage = "Stopping mesh...") }
                // Node stopping happens automatically
                Log.d(TAG, "Mesh network stop requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop mesh", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to stop mesh: ${e.message}"
                    )
                }
            }
        }
    }

    private fun navigateToScreen(route: String) {
        _uiState.update { it.copy(currentScreen = route) }
        Log.d(TAG, "Navigated to: $route")
    }

    private fun navigateToChat(identifier: String) {
        _uiState.update {
            it.copy(
                shouldNavigateToChat = identifier,
                currentScreen = "chatScreen/$identifier"
            )
        }
        Log.d(TAG, "Navigating to chat: $identifier")
    }

    private fun updateDeviceName(name: String) {
        settingPrefs.edit().putString("device_name", name).apply()
        _uiState.update { it.copy(deviceName = name) }
    }

    private fun updateTheme(theme: String) {
        settingPrefs.edit().putString("app_theme", theme).apply()
        _uiState.update { it.copy(appTheme = theme) }
    }

    private fun updateLanguage(code: String) {
        settingPrefs.edit().putString("language", code).apply()
        _uiState.update { it.copy(languageCode = code) }
    }

    private fun handlePermissions(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasAllPermissions = granted,
                permissionsRequested = true,
                error = if (!granted) "Some permissions were denied" else null
            )
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun completeOnboarding() {
        meshPrefs.edit().putBoolean("hasRunBefore", true).apply()
        _uiState.update { it.copy(hasRunBefore = true, showOnboarding = false) }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}