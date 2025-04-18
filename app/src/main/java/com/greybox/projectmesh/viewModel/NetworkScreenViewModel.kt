package com.greybox.projectmesh.viewModel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.DeviceStatusManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import org.kodein.di.DI
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import com.greybox.projectmesh.testing.TestDeviceEntry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.net.InetAddress
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.testing.TestDeviceService
import kotlinx.coroutines.withTimeoutOrNull

data class NetworkScreenModel(
    val connectingInProgressSsid: String? = null,
    val allNodes: Map<Int, VirtualNode.LastOriginatorMessage> = emptyMap(),
)

class NetworkScreenViewModel(di:DI, savedStateHandle: SavedStateHandle): ViewModel() {
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
            //create test device entry
            val testEntry = TestDeviceEntry.createTestEntry()

            node.state.collect { nodeState ->
                // get the test device entry
                // Combine real nodes with test device
                val allNodesWithTest = nodeState.originatorMessages.toMutableMap()
                allNodesWithTest[testEntry.first] = testEntry.second

                Log.d("NetworkScreenViewModel", "Updating nodes. Count: ${allNodesWithTest.size}")
                Log.d("NetworkScreenViewModel", "Test device address: ${testEntry.first}")
                Log.d("NetworkScreenViewModel", "All nodes: ${allNodesWithTest.keys}")

                // update the UI state with the new state
                _uiState.update { prev ->
                    prev.copy(
                        // update all nodes
                        allNodes = allNodesWithTest,
                        //allNodes = it.originatorMessages,
                        // update the ssid of the connecting station
                        connectingInProgressSsid =
                        if (nodeState.wifiState.wifiStationState.status == WifiStationState.Status.CONNECTING) {
                            nodeState.wifiState.wifiStationState.config?.ssid
                        } else {
                            null
                        }
                    )
                }

                //just mark nodes as online initially - DeviceStatusManager will verify
                allNodesWithTest.forEach { (addressInt, _) ->
                    val ipAddress =
                        InetAddress.getByAddress(addressInt.addressToByteArray()).hostAddress
                    // Update with verified=false to let the manager handle verification
                    DeviceStatusManager.updateDeviceStatus(ipAddress, true, verified = false)
                }
            }
        }

        /*
        THIS LOGIC SHOULDNT BE NEEDED ANY MORE IF DEVICE STATUS MANAGER WORKS
        viewModelScope.launch {
            while (true) {
                try {
                    //get all connected users
                    val connectedUsers = GlobalApp.GlobalUserRepo.userRepository.getAllConnectedUsers()

                    val onlineDevices = DeviceStatusManager.getOnlineDevices()

                    for (user in connectedUsers) {
                        user.address?.let { ipStr ->
                            try {
                                //skip test devices:
                                if (ipStr == TestDeviceService.TEST_DEVICE_IP) {
                                    // Online test device should always be online
                                    DeviceStatusManager.updateDeviceStatus(ipStr, true)
                                }
                                if (ipStr == TestDeviceService.TEST_DEVICE_IP_OFFLINE) {
                                    // Offline test device should always be offline
                                    DeviceStatusManager.updateDeviceStatus(ipStr, false)
                                }else{
                                    val addr = InetAddress.getByName(ipStr)
                                    val isReachable = withTimeoutOrNull(3000) {
                                        addr.isReachable(2000) // 2 second timeout
                                    } ?: false

                                    if (!isReachable) {
                                        // Device is not reachable at basic network level
                                        throw Exception("Device is not reachable")
                                    }

                                    //ping user by requesting online info to update status
                                    appServer.requestRemoteUserInfo(addr)

                                    //Update central status manager to show device as online
                                    DeviceStatusManager.updateDeviceStatus(ipStr, true)
                                    Log.d("NetworkScreenViewModel", "Pinged user: ${user.name} at $ipStr")
                                }
                            } catch (e: Exception) {
                                //if ping fails, mark user as offline
                                GlobalApp.GlobalUserRepo.conversationRepository.updateUserStatus(
                                    userUuid = user.uuid,
                                    isOnline = false,
                                    userAddress = null
                                )

                                //update central status manager to show device as offline
                                DeviceStatusManager.updateDeviceStatus(ipStr, false)
                                Log.d("NetworkScreenViewModel", "User ${user.name} appears to be offline")
                            }
                        }
                    }

                    //check any online devices that may not be in the users list
                    //i.e.: devices marked online but not properly registered as users
                    for (deviceIp in onlineDevices) {
                        if (connectedUsers.any { it.address == deviceIp } ||
                            deviceIp == TestDeviceService.TEST_DEVICE_IP ||
                            deviceIp == TestDeviceService.TEST_DEVICE_IP_OFFLINE) continue

                        // Skip devices we already checked in the user loop
                        if (connectedUsers.any { it.address == deviceIp }) continue

                        // Skip test device - it's handled differently
                        if (deviceIp == TestDeviceService.TEST_DEVICE_IP) continue

                        try {
                            val addr = InetAddress.getByName(deviceIp)
                            val isReachable = withTimeoutOrNull(3000) {
                                addr.isReachable(2000)
                            } ?: false

                            if (!isReachable) {
                                Log.d(
                                    "NetworkScreenViewModel",
                                    "Marking device $deviceIp as offline - not reachable"
                                )
                                DeviceStatusManager.updateDeviceStatus(deviceIp, false)
                            }
                        }catch (e: Exception){
                            Log.d("NetworkScreenViewModel", "Error checking device $deviceIp: ${e.message}")
                            DeviceStatusManager.updateDeviceStatus(deviceIp, false)
                        }
                    }
                }catch (e: Exception) {
                    Log.e("NetworkScreenViewModel", "Error during periodic ping", e)
                }
                //wait for 30 secs before next ming
                delay(30000)
            }
        }
    }
    */
        fun getDeviceName(wifiAddress: Int) {
            viewModelScope.launch {
                val inetAddress = InetAddress.getByAddress(wifiAddress.addressToByteArray())
                appServer.sendDeviceName(inetAddress)
            }
        }
    }
}