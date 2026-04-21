package com.greybox.projectmesh.mainscreen

import com.ustadmobile.meshrabiya.vnet.wifi.state.MeshrabiyaWifiState

/**
 * Complete UI state for MainActivity
 * This represents ALL state needed to render the main screen
 */
data class MeshUiState(
    // Mesh Network State
    val wifiState: MeshrabiyaWifiState? = null,
    val isNetworkActive: Boolean = false,
    val localAddress: Int = 0,
    val connectedNodesCount: Int = 0,
    val availableNodesCount: Int = 0,
    val nodesOnMesh: Set<Int> = emptySet(),

    // Navigation State
    val currentScreen: String = "home",
    val shouldNavigateToChat: String? = null,

    // User Settings State
    val deviceName: String = "",
    val appTheme: String = "SYSTEM",
    val languageCode: String = "en",
    val autoFinish: Boolean = false,
    val saveToFolder: String = "",

    // Loading & Error States
    val isLoading: Boolean = false,
    val error: String? = null,
    val statusMessage: String = "Mesh network inactive",

    // Permissions State
    val hasAllPermissions: Boolean = false,
    val permissionsRequested: Boolean = false,

    // Onboarding State
    val hasRunBefore: Boolean = false,
    val showOnboarding: Boolean = false
)

/**
 * UI Events that can be triggered from MainActivity
 */
sealed class MeshUiEvent {
    data object StartMeshNetwork : MeshUiEvent()
    data object StopMeshNetwork : MeshUiEvent()
    data class NavigateToScreen(val route: String) : MeshUiEvent()
    data class NavigateToChat(val identifier: String) : MeshUiEvent()
    data class UpdateDeviceName(val name: String) : MeshUiEvent()
    data class UpdateTheme(val theme: String) : MeshUiEvent()
    data class UpdateLanguage(val code: String) : MeshUiEvent()
    data class PermissionsGranted(val granted: Boolean) : MeshUiEvent()
    data  class UpdateAutoFinish(val enabled: Boolean) : MeshUiEvent()
    data class UpdateSaveToFolder(val path: String) : MeshUiEvent()
    data object ClearError : MeshUiEvent()
    data object CompleteOnboarding : MeshUiEvent()
}