package com.greybox.projectmesh.viewModel

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.data.SettingsRepository
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.state.MeshrabiyaWifiState
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber

@Immutable
data class HomeUiState(
    // mirrored from node.state (Flow — not seedable, defaults show until first emission)
    val wifiState: MeshrabiyaWifiState? = null,
    val connectUri: String? = null,
    val localAddress: Int = 0,
    val hotspotStatus: Boolean = false,
    val nodesOnMesh: Set<Int> = emptySet(),
    // set once in init from the synchronous 5GHz capability check
    val bandMenu: List<ConnectBand> = listOf(ConnectBand.BAND_2GHZ),
    val band: ConnectBand = bandMenu.first(),
    val hotspotTypeMenu: List<HotspotType> = listOf(
        HotspotType.AUTO,
        HotspotType.WIFIDIRECT_GROUP,
        HotspotType.LOCALONLY_HOTSPOT,
    ),
    val hotspotTypeToCreate: HotspotType = hotspotTypeMenu.first(),
    // mirrored from SettingsRepository (was: SharedPreferences listener + screen param)
    val deviceName: String = "",
    val concurrencyKnown: Boolean = false,
    val concurrencySupported: Boolean = true,
    // VM-owned dialog visibility (was: two loose StateFlows)
    val showNoConcurrencyWarning: Boolean = false,
    val showConcurrencyWarning: Boolean = false,
) {
    val wifiConnectionEnabled: Boolean
        get() = wifiState?.connectConfig != null
    val connectBandVisible: Boolean
        get() = Build.VERSION.SDK_INT >= 29 && wifiState?.connectConfig == null
}

class HomeScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle,
) : StateViewModel<HomeUiState>(HomeUiState()), DIAware {

    override val di = di
    private val settings: SettingsRepository by instance()
    private val node: AndroidVirtualNode by instance()

    /** Carrier for the repo-mirrored fields (see SettingsScreenViewModel). */
    private data class HomeSettings(
        val deviceName: String,
        val concurrencyKnown: Boolean,
        val concurrencySupported: Boolean,
    )

    init {
        // seed synchronously before the mirrors launch. The 5GHz
        // capability check is also synchronous, so it belongs to the seed too.
        setState { withHomeSettings(currentHomeSettings()) }
        if (node.meshrabiyaWifiManager.is5GhzSupported) {
            setState {
                copy(
                    bandMenu = listOf(ConnectBand.BAND_5GHZ, ConnectBand.BAND_2GHZ),
                    band = ConnectBand.BAND_5GHZ,
                )
            }
        }

        combine(
            settings.deviceName, settings.concurrencyKnown, settings.concurrencySupported,
        ) { name, known, supported ->
            HomeSettings(name, known, supported)
        }.onEach { s -> setState { withHomeSettings(s) } }
            .launchIn(viewModelScope)

        node.state.onEach { nodeState ->
            setState {
                copy(
                    wifiState = nodeState.wifiState,
                    connectUri = nodeState.connectUri,
                    localAddress = nodeState.address,
                    hotspotStatus = nodeState.wifiState.hotspotIsStarted,
                    nodesOnMesh = nodeState.originatorMessages.keys,
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun currentHomeSettings() = HomeSettings(
        deviceName = settings.deviceName.value,
        concurrencyKnown = settings.concurrencyKnown.value,
        concurrencySupported = settings.concurrencySupported.value,
    )

    /** The ONE carrier→state mapping, shared by seed and mirror. */
    private fun HomeUiState.withHomeSettings(s: HomeSettings) = copy(
        deviceName = s.deviceName,
        concurrencyKnown = s.concurrencyKnown,
        concurrencySupported = s.concurrencySupported,
    )

    // --- pure UI state ---
    fun setBand(band: ConnectBand) = setState { copy(band = band) }
    fun setHotspotType(hotspotType: HotspotType) = setState { copy(hotspotTypeToCreate = hotspotType) }
    fun dismissNoConcurrencyWarning() = setState { copy(showNoConcurrencyWarning = false) }
    fun dismissConcurrencyWarning() = setState { copy(showConcurrencyWarning = false) }

    /** Android 11+ official-API detection result (HomeScreen calls this once); no dialog. */
    fun onConcurrencyDetected(supported: Boolean) =
        settings.setConcurrency(known = true, supported = supported)

    fun setIncomingConnectionsEnabled(enable: Boolean) {
        viewModelScope.launch {
            val wasWifiConnected = currentState.wifiState?.wifiStationState?.status
            try {
                Timber.tag("HotspotDebug").d("Attempt 1: Setting Hotspot to $enable")
                val response = withTimeoutOrNull(1500) {
                    node.setWifiHotspotEnabled(
                        enabled = enable,
                        preferredBand = currentState.band,
                        hotspotType = currentState.hotspotTypeToCreate,
                    )
                }
                if (response == null) {
                    Timber.tag("HotspotDebug").w("No response within 1500ms, Retrying...")
                    // Retry once
                    val retryResponse = node.setWifiHotspotEnabled(
                        enabled = enable,
                        preferredBand = currentState.band,
                        hotspotType = currentState.hotspotTypeToCreate,
                    )
                    Timber.tag("HotspotDebug").d("Retry successful: $retryResponse")
                } else {
                    Timber.tag("HotspotDebug").d("Hotspot set successfully: $response")
                }
                if (!currentState.concurrencyKnown && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    delay(500)
                    val isWifiStillConnected = currentState.wifiState?.wifiStationState?.status
                    if (wasWifiConnected == WifiStationState.Status.AVAILABLE) {
                        if (isWifiStillConnected == WifiStationState.Status.INACTIVE || isWifiStillConnected == null) {
                            markStaApConcurrencyUnsupported()
                            Timber.tag("HotspotDebug").d("Wi-Fi disconnected after enabling hotspot. STA/AP concurrency NOT supported.")
                        } else {
                            markStaApConcurrencySupported()
                            Timber.tag("HotspotDebug").d("Wi-Fi still connected after enabling " +
                                    "hotspot. STA/AP concurrency supported.")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("HotspotDebug").e("Failed to set hotspot: ${e.message}")
            }
        }
    }

    /** Connect to a wifi network as a station (client mode). */
    fun connectWifi(hotSpotConfig: WifiConnectConfig) {
        viewModelScope.launch {
            try {
                val wasHotspotOnline = currentState.hotspotStatus
                node.connectAsStation(hotSpotConfig)
                if (!currentState.concurrencyKnown && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    delay(500)
                    val isHotspotStillOnline = currentState.hotspotStatus
                    if (wasHotspotOnline) {
                        if (!isHotspotStillOnline) {
                            markStaApConcurrencyUnsupported()
                        } else {
                            markStaApConcurrencySupported()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag("HomeScreenViewModel").e("onConnectWifi: ${e.message}")
            }
        }
    }

    fun disconnectStation() {
        viewModelScope.launch {
            node.disconnectWifiStation()
        }
    }

    private fun markStaApConcurrencyUnsupported() {
        settings.setConcurrency(known = true, supported = false)
        setState { copy(showNoConcurrencyWarning = true) }
    }

    private fun markStaApConcurrencySupported() {
        settings.setConcurrency(known = true, supported = true)
        setState { copy(showConcurrencyWarning = true) }
    }
}
