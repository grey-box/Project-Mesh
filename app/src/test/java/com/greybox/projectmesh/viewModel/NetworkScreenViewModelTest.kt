package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testing.TestDeviceEntry
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.state.MeshrabiyaWifiState
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // 192.168.0.5 and 192.168.0.6 as big-endian Ints (Meshrabiya's address format)
    private val peerA = 0xC0A80005.toInt()
    private val peerB = 0xC0A80006.toInt()
    // stand-in for the always-injected test device (192.168.0.99)
    private val testDeviceAddr = 0xC0A80063.toInt()

    private val nodeState = MutableStateFlow(LocalNodeState())
    private val node: AndroidVirtualNode = mockk { every { state } returns nodeState }
    private val appServer: AppServer = mockk(relaxed = true)

    @Before
    fun setUp() {
        // createTestEntry() opens a real DatagramSocket: replace it with a mock entry
        mockkObject(TestDeviceEntry.Companion)
        every { TestDeviceEntry.createTestEntry() } returns (testDeviceAddr to mockk(relaxed = true))

        // global singleton that logs via android.util.Log: stub out its side effects
        mockkObject(DeviceStatusManager)
        every { DeviceStatusManager.updateDeviceStatus(any(), any(), any()) } just runs
        every { DeviceStatusManager.handleNetworkDisconnect(any()) } just runs
    }

    @After
    fun tearDown() = unmockkAll()

    private fun buildVm(): NetworkScreenViewModel {
        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }
        return NetworkScreenViewModel(di, SavedStateHandle())
    }

    private fun msg(): VirtualNode.LastOriginatorMessage = mockk(relaxed = true)

    private fun stationState(status: WifiStationState.Status, ssidName: String) =
        MeshrabiyaWifiState(
            wifiStationState = WifiStationState(
                status = status,
                config = mockk<WifiConnectConfig> { every { ssid } returns ssidName },
            )
        )

    @Test
    fun `initial state is empty before the node mirror runs`() {
        val vm = buildVm()
        assertTrue(vm.state.value.nodes.isEmpty())
        assertNull(vm.state.value.connectingSsid)
    }

    @Test
    fun `nodes are mapped from node state with ip strings, plus the test device`() =
        runTest(mainDispatcherRule.dispatcher) {
            nodeState.value = LocalNodeState(originatorMessages = mapOf(peerA to msg()))
            val vm = buildVm()
            advanceUntilIdle()

            val nodes = vm.state.value.nodes
            assertEquals(listOf(peerA, testDeviceAddr), nodes.map { it.address })
            assertEquals(listOf("192.168.0.5", "192.168.0.99"), nodes.map { it.ipAddress })
        }

    @Test
    fun `connectingSsid is set only while the station is CONNECTING`() =
        runTest(mainDispatcherRule.dispatcher) {
            nodeState.value = LocalNodeState(
                wifiState = stationState(WifiStationState.Status.CONNECTING, "mesh-ssid")
            )
            val vm = buildVm()
            advanceUntilIdle()
            assertEquals("mesh-ssid", vm.state.value.connectingSsid)

            nodeState.value = LocalNodeState(
                wifiState = stationState(WifiStationState.Status.AVAILABLE, "mesh-ssid")
            )
            advanceUntilIdle()
            assertNull(vm.state.value.connectingSsid)
        }

    @Test
    fun `state follows later node updates`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm()
        advanceUntilIdle()

        nodeState.value = LocalNodeState(originatorMessages = mapOf(peerA to msg(), peerB to msg()))
        advanceUntilIdle()
        assertEquals(setOf(peerA, peerB, testDeviceAddr), vm.state.value.nodes.map { it.address }.toSet())
    }

    @Test
    fun `a node that disappears is reported as disconnected`() =
        runTest(mainDispatcherRule.dispatcher) {
            nodeState.value = LocalNodeState(originatorMessages = mapOf(peerA to msg(), peerB to msg()))
            buildVm()
            advanceUntilIdle()

            nodeState.value = LocalNodeState(originatorMessages = mapOf(peerA to msg()))
            advanceUntilIdle()

            verify(exactly = 1) { DeviceStatusManager.handleNetworkDisconnect("192.168.0.6") }
            verify(exactly = 0) { DeviceStatusManager.handleNetworkDisconnect("192.168.0.5") }
        }

    @Test
    fun `every listed node is marked online, unverified`() = runTest(mainDispatcherRule.dispatcher) {
        nodeState.value = LocalNodeState(originatorMessages = mapOf(peerA to msg()))
        buildVm()
        advanceUntilIdle()

        verify { DeviceStatusManager.updateDeviceStatus("192.168.0.5", true, false) }
        verify { DeviceStatusManager.updateDeviceStatus("192.168.0.99", true, false) }
    }

    @Test
    fun `onNodeClicked requests and pushes user info for that address`() {
        val vm = buildVm()
        vm.onNodeClicked("192.168.0.5")

        val addr = InetAddress.getByName("192.168.0.5")
        verify(exactly = 1) { appServer.requestRemoteUserInfo(addr, any()) }
        verify(exactly = 1) { appServer.pushUserInfoTo(addr, any()) }
    }
}
