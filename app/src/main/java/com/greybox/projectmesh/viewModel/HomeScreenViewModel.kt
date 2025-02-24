package com.greybox.projectmesh.viewModel

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.model.HomeScreenModel
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.di.DI
import org.kodein.di.instance


class HomeScreenViewModel(di: DI,
                          savedStateHandle: SavedStateHandle): ViewModel(){
    // inject SharedPreferences
    private val settingPrefs: SharedPreferences by di.instance(tag = "settings")
    // _uiState will be updated whenever there is a change in the UI state
    private val _uiState = MutableStateFlow(HomeScreenModel())
    // uiState is a read-only property that shows the current UI state
    val uiState: Flow<HomeScreenModel> = _uiState.asStateFlow()
    // di is used to get the AndroidVirtualNode instance
    private val node: AndroidVirtualNode by di.instance()
    // Concurrency Known State
    private val _concurrencyKnown = MutableStateFlow(loadConcurrencyKnown())
    val concurrencyKnown: StateFlow<Boolean> = _concurrencyKnown
    // Concurrency Supported State
    private val _concurrencySupported = MutableStateFlow(loadConcurrencySupported())
    val concurrencySupported: StateFlow<Boolean> = _concurrencySupported

    private val sharedPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == CONCURRENCY_KNOWN_KEY || key == CONCURRENCY_SUPPORTED_KEY) {
            _concurrencyKnown.value = loadConcurrencyKnown()
            _concurrencySupported.value = loadConcurrencySupported()
        }
    }

    // control the visibility of the STA/AP concurrency doesn't support warning popup
    private val _showNoConcurrencyWarning = MutableStateFlow(false)
    val showNoConcurrencyWarning: StateFlow<Boolean> = _showNoConcurrencyWarning.asStateFlow()

    // control the visibility of the STA/AP concurrency support warning popup
    private val _showConcurrencyWarning = MutableStateFlow(false)
    val showConcurrencyWarning: StateFlow<Boolean> = _showConcurrencyWarning.asStateFlow()

    init {
        // launch a coroutine
        viewModelScope.launch {
            // collect the state flow of the AndroidVirtualNode
            node.state.collect {
                // update the UI state with the new state
                _uiState.update { prev ->
                    // Creates a new instance of the state,
                    // copying the existing properties and updating only the ones specified.
                    prev.copy(
                        wifiState = it.wifiState,
                        connectUri = it.connectUri,
                        localAddress = it.address,
                        hotspotStatus = it.wifiState.hotspotIsStarted,
                        nodesOnMesh = it.originatorMessages.keys
                    )
                }
            }
        }
        settingPrefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    private fun loadConcurrencyKnown(): Boolean {
        return settingPrefs.getBoolean(CONCURRENCY_KNOWN_KEY, false)
    }

    fun saveConcurrencyKnown(concurrencyKnown: Boolean) {
        _concurrencyKnown.value = concurrencyKnown
        settingPrefs.edit().putBoolean(CONCURRENCY_KNOWN_KEY, concurrencyKnown).apply()
    }

    private fun loadConcurrencySupported(): Boolean {
        return settingPrefs.getBoolean(CONCURRENCY_SUPPORTED_KEY, true)
    }

    fun saveConcurrencySupported(concurrencySupported: Boolean) {
        _concurrencySupported.value = concurrencySupported
        settingPrefs.edit().putBoolean(CONCURRENCY_SUPPORTED_KEY, concurrencySupported).apply()
    }

    fun onSetIncomingConnectionsEnabled(enable: Boolean) {
        viewModelScope.launch {
            val wasWifiConnected = _uiState.value.wifiState?.wifiStationState?.status
            try {
                Log.d("HotspotDebug", "Attempt 1: Setting Hotspot to $enable")
                val response = withTimeoutOrNull(1500) {
                    node.setWifiHotspotEnabled(
                        enabled = enable,
                        preferredBand = _uiState.value.band,
                        hotspotType = _uiState.value.hotspotTypeToCreate
                    )
                }
                if (response == null) {
                    Log.w("HotspotDebug", "No response within 1500ms, Retrying...")
                    // Retry once
                    val retryResponse = node.setWifiHotspotEnabled(
                        enabled = enable,
                        preferredBand = _uiState.value.band,
                        hotspotType = _uiState.value.hotspotTypeToCreate
                    )
                    Log.d("HotspotDebug", "Retry successful: $retryResponse")
                }
                else {
                    Log.d("HotspotDebug", "Hotspot set successfully: $response")
                }
                if (!_concurrencyKnown.value && Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                    delay(500)
                    val isWifiStillConnected = _uiState.value.wifiState?.wifiStationState?.status
                    if (wasWifiConnected == WifiStationState.Status.AVAILABLE)
                    {
                        if(isWifiStillConnected == WifiStationState.Status.INACTIVE || isWifiStillConnected == null){
                            markStaApConcurrencyUnsupported()
                            Log.d("HotspotDebug", "Wi-Fi disconnected after enabling hotspot. STA/AP concurrency NOT supported.")
                        }
                        else{
                            markStaApConcurrencySupported()
                            Log.d("HotspotDebug", "Wi-Fi still connected after enabling hotspot. STA/AP concurrency supported.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HotspotDebug", "Failed to set hotspot: ${e.message}")
            }
        }
    }

    // This function is responsible for connecting to a wifi network as a station (Client Mode)
    fun onConnectWifi(
        hotSpotConfig: WifiConnectConfig
    ){
        viewModelScope.launch {
            try{
                val wasHotspotOnline = _uiState.value.hotspotStatus
                node.connectAsStation(hotSpotConfig)
                if(!_concurrencyKnown.value && Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                    delay(500)
                    val isHotspotStillOnline = _uiState.value.hotspotStatus
                    if(wasHotspotOnline){
                        if(!isHotspotStillOnline){
                            markStaApConcurrencyUnsupported()
                        }
                        else{
                            markStaApConcurrencySupported()
                        }
                    }
                }
            }
            catch (e: Exception){
                Log.e("HomeScreenViewModel", "onConnectWifi: ${e.message}")
            }
        }
    }

    // disconnect the wifi station
    fun onClickDisconnectStation(){
        viewModelScope.launch {
            node.disconnectWifiStation()
        }
    }

    private fun markStaApConcurrencyUnsupported(){
        saveConcurrencyKnown(true)
        saveConcurrencySupported(false)
        _showNoConcurrencyWarning.value = true
    }

    private fun markStaApConcurrencySupported(){
        saveConcurrencyKnown(true)
        saveConcurrencySupported(true)
        _showConcurrencyWarning.value = true
    }

    fun dismissNoConcurrencyWarning() {
        _showNoConcurrencyWarning.value = false
    }

    fun dismissConcurrencyWarning() {
        _showConcurrencyWarning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister the listener when ViewModel is cleared
        settingPrefs.unregisterOnSharedPreferenceChangeListener(sharedPrefsListener)
    }

    companion object{
        private const val CONCURRENCY_KNOWN_KEY = "concurrency_known"
        private const val CONCURRENCY_SUPPORTED_KEY = "concurrency_supported"
    }
}