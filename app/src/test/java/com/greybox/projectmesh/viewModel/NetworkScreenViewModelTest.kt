package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testing.TestDeviceEntry
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.state.MeshrabiyaWifiState
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NetworkScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var node: AndroidVirtualNode
    private lateinit var appServer: AppServer
    private lateinit var di: DI

    private lateinit var nodeStateFlow: MutableStateFlow<LocalNodeState>

    @Before
    fun setUp() {
        node = mockk(relaxed = true)
        appServer = mockk(relaxed = true)

        mockkObject(DeviceStatusManager)
        every { DeviceStatusManager.updateDeviceStatus(any(), any(), any()) } just Runs
        every { DeviceStatusManager.handleNetworkDisconnect(any()) } just Runs

        mockkObject(TestDeviceEntry)
        val testMsg = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        every { TestDeviceEntry.createTestEntry() } returns (1234 to testMsg)

        nodeStateFlow = MutableStateFlow(
            makeNodeState(
                originators = mapOf(
                    1 to mockk(relaxed = true),
                    2 to mockk(relaxed = true),
                ),
                connecting = true,
                ssid = "MyWifi"
            )
        )

        every { node.state } returns nodeStateFlow

        di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceStatusManager)
        unmockkObject(TestDeviceEntry)
        clearAllMocks()
    }

    @Test
    fun init_setsConnectingSsid_andIncludesTestDevice_andUpdatesStatuses() = runTest {
        val vm = NetworkScreenViewModel(di, SavedStateHandle())
        advanceUntilIdle()

        val state = vm.uiState.first()

        assertEquals("MyWifi", state.connectingInProgressSsid)
        assertTrue(state.allNodes.keys.containsAll(setOf(1, 2, 1234)))

        state.allNodes.keys.forEach { addrInt ->
            val ip = InetAddress.getByAddress(addrInt.addressToByteArray()).hostAddress
            verify { DeviceStatusManager.updateDeviceStatus(ip, true, verified = false) }
        }
    }

    @Test
    fun whenNotConnecting_connectingInProgressSsidBecomesNull() = runTest {
        val vm = NetworkScreenViewModel(di, SavedStateHandle())
        advanceUntilIdle()

        nodeStateFlow.value = makeNodeState(
            originators = mapOf(9 to mockk(relaxed = true)),
            connecting = false,
            ssid = "ShouldNotAppear"
        )
        advanceUntilIdle()

        val state = vm.uiState.first()
        assertNull(state.connectingInProgressSsid)
    }

    @Test
    fun whenNodeDisappears_callsHandleNetworkDisconnect() = runTest {
        val vm = NetworkScreenViewModel(di, SavedStateHandle())
        advanceUntilIdle()

        nodeStateFlow.value = makeNodeState(
            originators = mapOf(2 to mockk(relaxed = true)), // node 1 disappeared
            connecting = false,
            ssid = null
        )
        advanceUntilIdle()

        val ip1 = InetAddress.getByAddress(1.addressToByteArray()).hostAddress
        verify { DeviceStatusManager.handleNetworkDisconnect(ip1) }
    }

    //@Test
  /*  fun getDeviceName_callsAppServerSendDeviceName() = runTest {
        val vm = NetworkScreenViewModel(di, SavedStateHandle())
        advanceUntilIdle()

        val addr = 0x0A000001 // 10.0.0.1
        NetworkScreenViewModel::class.java
            .getDeclaredMethod("getDeviceName", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(vm, addr)
        advanceUntilIdle()

        val inet = InetAddress.getByAddress(addr.addressToByteArray())

        io.mockk.coVerify { appServer.sendDeviceName(inet) }
    }
*/
    private fun makeNodeState(
        originators: Map<Int, VirtualNode.LastOriginatorMessage>,
        connecting: Boolean,
        ssid: String?
    ): LocalNodeState {
        val nodeState = mockk<LocalNodeState>(relaxed = true)

        every { nodeState.originatorMessages } returns originators

        val wifiState = mockk<MeshrabiyaWifiState>(relaxed = true)
        val stationState = mockk<WifiStationState>(relaxed = true)

        every {
            stationState.status
        } returns if (connecting) WifiStationState.Status.CONNECTING else WifiStationState.Status.AVAILABLE

        if (ssid != null) {
            val cfg = mockk<WifiConnectConfig>(relaxed = true)
            every { cfg.ssid } returns ssid
            every { stationState.config } returns cfg
        } else {
            every { stationState.config } returns null
        }

        every { wifiState.wifiStationState } returns stationState
        every { nodeState.wifiState } returns wifiState

        return nodeState
    }
}
