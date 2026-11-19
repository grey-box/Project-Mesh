package com.greybox.projectmesh.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testing.TestDeviceEntry
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber
import java.net.InetAddress

/** One row in the node list. The IP string is converted once here, not in the UI. */
@Immutable
data class NodeItem(
    val address: Int,
    val ipAddress: String,
    val entry: VirtualNode.LastOriginatorMessage,
)

@Immutable
data class NetworkUiState(
    val nodes: List<NodeItem> = emptyList(),
    val connectingSsid: String? = null,
)

class NetworkScreenViewModel(
    di: DI,
    @Suppress("UNUSED_PARAMETER") savedStateHandle: SavedStateHandle,
) : StateViewModel<NetworkUiState>(NetworkUiState()), DIAware {

    override val di = di
    private val node: AndroidVirtualNode by instance()
    private val appServer: AppServer by instance()

    // Behavior preserved from the legacy VM: the test device is always shown.
    // TODO(bug): this is injected in production builds too.
    private val testEntry = TestDeviceEntry.createTestEntry()
    private var previousNodes = emptySet<Int>()

    init {
        // NOTE: no synchronous seed. Meshrabiya exposes node.state as Flow (not
        // StateFlow), so there is no .value to read. See PR description.
        node.state
            .onEach { nodeState ->
                reportDisconnects(nodeState)
                setState { withNodeState(nodeState) }
                markNodesOnline()
            }
            .launchIn(viewModelScope)
    }

    /** The ONE node-state -> UI-state mapping. */
    private fun NetworkUiState.withNodeState(nodeState: LocalNodeState): NetworkUiState {
        val allNodes = nodeState.originatorMessages + testEntry
        val station = nodeState.wifiState.wifiStationState
        return copy(
            nodes = allNodes.map { (address, entry) ->
                NodeItem(address = address, ipAddress = address.toIpString(), entry = entry)
            },
            connectingSsid = if (station.status == WifiStationState.Status.CONNECTING) {
                station.config?.ssid
            } else {
                null
            },
        )
    }

    /** Tell DeviceStatusManager about nodes that disappeared since the last emission. */
    private fun reportDisconnects(nodeState: LocalNodeState) {
        val currentNodes = nodeState.originatorMessages.keys
        (previousNodes - currentNodes).forEach { address ->
            val ip = address.toIpString()
            DeviceStatusManager.handleNetworkDisconnect(ip)
            Timber.tag("NetworkScreenViewModel").d("Detected disconnection of node: $ip")
        }
        previousNodes = currentNodes
    }

    /** Mark every listed node online (unverified); DeviceStatusManager verifies. */
    private fun markNodesOnline() {
        currentState.nodes.forEach { item ->
            DeviceStatusManager.updateDeviceStatus(item.ipAddress, true, verified = false)
        }
    }

    /** User tapped a node: exchange user info. Navigation stays in the UI layer.
     *  AppServer runs both calls in its own IO scope with its own error handling. */
    fun onNodeClicked(ipAddress: String) {
        val addr = InetAddress.getByName(ipAddress)
        appServer.requestRemoteUserInfo(addr)
        appServer.pushUserInfoTo(addr)
    }

    private fun Int.toIpString(): String =
        InetAddress.getByAddress(addressToByteArray()).hostAddress ?: ""
}
