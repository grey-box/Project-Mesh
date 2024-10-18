package com.greybox.projectmesh.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.views.NetworkScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kodein.di.DI
import com.greybox.projectmesh.model.NetworkScreenModel
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.net.InetAddress

class NetworkScreenViewModel(di:DI): ViewModel() {
    // _uiState will be updated whenever there is a change in the UI state
    private val _uiState = MutableStateFlow(NetworkScreenModel())
    // uiState is a read-only property that shows the current UI state
    val uiState: Flow<NetworkScreenModel> = _uiState.asStateFlow()
    // di is used to get the AndroidVirtualNode instance
    private val node: AndroidVirtualNode by di.instance()
    private val appServer: AppServer by di.instance()

    // launch a coroutine
    init {
        viewModelScope.launch {
            // collect the state flow of the AndroidVirtualNode
            node.state.collect{
                // update the UI state with the new state
                _uiState.update { prev -> prev.copy(
                    // update all nodes
                    allNodes = it.originatorMessages,
                    // update the ssid of the connecting station
                    connectingInProgressSsid =
                    if (it.wifiState.wifiStationState.status == WifiStationState.Status.CONNECTING){
                        it.wifiState.wifiStationState.config?.ssid
                    }
                    else{
                        null
                    }
                )
                }
            }
        }
    }

    fun getDeviceName(wifiAddress: Int){
        viewModelScope.launch {
            val inetAddress = InetAddress.getByAddress(wifiAddress.addressToByteArray())
            appServer.sendDeviceName(inetAddress)
        }
    }

}