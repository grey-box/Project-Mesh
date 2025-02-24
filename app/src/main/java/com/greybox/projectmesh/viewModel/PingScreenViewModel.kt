package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.model.PingScreenModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kodein.di.DI
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.net.InetAddress

class PingScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle,
    virtualAddress: InetAddress
): ViewModel() {
    // _uiState will be updated whenever there is a change in the UI state
    private val _uiState = MutableStateFlow(
        PingScreenModel(
            deviceName = GlobalApp.DeviceInfoManager.getDeviceName(virtualAddress),
            virtualAddress = virtualAddress
        )
    )
    // uiState is a read-only property that shows the current UI state
    val uiState: Flow<PingScreenModel> = _uiState.asStateFlow()
    // di is used to get the AndroidVirtualNode instance
    private val node: AndroidVirtualNode by di.instance()
    private val appServer: AppServer by di.instance()
    private var lastTimeReceived: Long = 0L

    // launch a coroutine
    init {
        viewModelScope.launch {
            // collect the state flow of the AndroidVirtualNode
            node.state.collect{ localNodeState ->
                val newOriginatorMessage = localNodeState.originatorMessages[virtualAddress.requireAddressAsInt()]
                if ( newOriginatorMessage != null && lastTimeReceived != newOriginatorMessage.timeReceived ) {
                    lastTimeReceived = newOriginatorMessage.timeReceived
                    // update the UI state with the new state
                    _uiState.update { prev ->
                        prev.copy(
                            allOriginatorMessages = ((prev.allOriginatorMessages + newOriginatorMessage) as List<VirtualNode.LastOriginatorMessage>)
                        )
                    }
                }
            }
        }
    }
}