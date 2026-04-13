package com.greybox.projectmesh.viewModel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig
import com.ustadmobile.meshrabiya.vnet.wifi.state.MeshrabiyaWifiState
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import com.greybox.projectmesh.testutil.MainDispatcherRule


@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE) // removes the "No manifest found" spam
class HomeScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var prefs: SharedPreferences
    private lateinit var node: AndroidVirtualNode
    private lateinit var di: DI
    private lateinit var stateFlow: MutableStateFlow<LocalNodeState>

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        node = mockk(relaxed = true)

        every { node.meshrabiyaWifiManager.is5GhzSupported } returns false

        val initialStateObj = makeLocalNodeState(
            wifiState = makeWifiState(
                connectConfigPresent = false,
                hotspotIsStarted = false,
                station = WifiStationState.Status.AVAILABLE
            ),
            connectUri = "mesh://connect",
            address = 7,
            nodesOnMesh = setOf(10, 11)
        )

        stateFlow = MutableStateFlow(initialStateObj)
        every { node.state } returns stateFlow

        // return null is fine for nullable response type
        coEvery { node.setWifiHotspotEnabled(any(), any(), any()) } returns null
        coEvery { node.connectAsStation(any()) } just Runs
        coEvery { node.disconnectWifiStation() } just Runs

        di = DI {
            bind<SharedPreferences>(tag = "settings") with singleton { prefs }
            bind<AndroidVirtualNode>() with singleton { node }
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun init_collectsNodeState_andUpdatesUiState() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())

        // IMPORTANT: let init collectors run first
        advanceUntilIdle()

        val s1 = vm.uiState.first()
        assertEquals("mesh://connect", s1.connectUri)
        assertEquals(7, s1.localAddress)
        assertFalse(s1.hotspotStatus)
        assertEquals(setOf(10, 11), s1.nodesOnMesh)

        stateFlow.value = makeLocalNodeState(
            wifiState = makeWifiState(
                connectConfigPresent = true,
                hotspotIsStarted = true,
                station = WifiStationState.Status.AVAILABLE
            ),
            connectUri = "mesh://new",
            address = 42,
            nodesOnMesh = setOf(99)
        )
        advanceUntilIdle()

        val s2 = vm.uiState.first()
        assertEquals("mesh://new", s2.connectUri)
        assertEquals(42, s2.localAddress)
        assertTrue(s2.hotspotStatus)
        assertEquals(setOf(99), s2.nodesOnMesh)
    }

    @Test
    fun init_when5GhzSupported_setsBandMenuAndDefaultBand() = runTest {
        every { node.meshrabiyaWifiManager.is5GhzSupported } returns true

        val vm = HomeScreenViewModel(di, SavedStateHandle())
        advanceUntilIdle()

        val s = vm.uiState.first()
        assertEquals(listOf(ConnectBand.BAND_5GHZ, ConnectBand.BAND_2GHZ), s.bandMenu)
        assertEquals(ConnectBand.BAND_5GHZ, s.band)
    }

    @Test
    fun saveConcurrencyKnown_andSupported_updateFlows_andPrefs() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())

        vm.saveConcurrencyKnown(true)
        vm.saveConcurrencySupported(false)

        assertTrue(vm.concurrencyKnown.value)
        assertFalse(vm.concurrencySupported.value)

        assertTrue(prefs.getBoolean("concurrency_known", false))
        assertFalse(prefs.getBoolean("concurrency_supported", true))
    }

    @Test
    fun prefsListener_updatesFlows_whenPrefsChange() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())

        prefs.edit().putBoolean("concurrency_known", true).commit()
        prefs.edit().putBoolean("concurrency_supported", false).commit()

        advanceUntilIdle()

        assertTrue(vm.concurrencyKnown.value)
        assertFalse(vm.concurrencySupported.value)
    }

    @Test
    fun onConnectBandChanged_updatesUiState() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())
        vm.onConnectBandChanged(ConnectBand.BAND_2GHZ)
        advanceUntilIdle()

        assertEquals(ConnectBand.BAND_2GHZ, vm.uiState.first().band)
    }

    @Test
    fun onSetHotspotTypeToCreate_updatesUiState() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())
        vm.onSetHotspotTypeToCreate(HotspotType.LOCALONLY_HOTSPOT)
        advanceUntilIdle()

        assertEquals(HotspotType.LOCALONLY_HOTSPOT, vm.uiState.first().hotspotTypeToCreate)
    }

    @Test
    fun onClickDisconnectStation_callsNodeDisconnect() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())
        vm.onClickDisconnectStation()
        advanceUntilIdle()

        coVerify { node.disconnectWifiStation() }
    }

    @Test
    fun onConnectWifi_callsConnectAsStation() = runTest {
        val vm = HomeScreenViewModel(di, SavedStateHandle())

        // Still ok to create dummy config, but DO NOT verify by equality (hashCode triggers NPE)
        val cfg = unsafeInstance<WifiConnectConfig>()

        vm.onConnectWifi(cfg)
        advanceUntilIdle()

        // FIX: verify call happened, don't force MockK to hash/compare cfg
        coVerify { node.connectAsStation(any()) }
    }

    // ---------------- Helpers ----------------

    private fun makeLocalNodeState(
        wifiState: MeshrabiyaWifiState,
        connectUri: String,
        address: Int,
        nodesOnMesh: Set<Int>
    ): LocalNodeState {
        val st = mockk<LocalNodeState>(relaxed = true)

        every { st.wifiState } returns wifiState
        every { st.connectUri } returns connectUri
        every { st.address } returns address

        val originatorMap: Map<Int, VirtualNode.LastOriginatorMessage> =
            nodesOnMesh.associateWith { unsafeInstance<VirtualNode.LastOriginatorMessage>() }

        every { st.originatorMessages } returns originatorMap
        return st
    }

    private fun makeWifiState(
        connectConfigPresent: Boolean,
        hotspotIsStarted: Boolean,
        station: WifiStationState.Status
    ): MeshrabiyaWifiState {
        val wifi = mockk<MeshrabiyaWifiState>(relaxed = true)

        val cfg: WifiConnectConfig? =
            if (connectConfigPresent) unsafeInstance<WifiConnectConfig>() else null
        every { wifi.connectConfig } returns cfg

        every { wifi.hotspotIsStarted } returns hotspotIsStarted

        val stationState = mockk<WifiStationState>(relaxed = true)
        every { stationState.status } returns station
        every { wifi.wifiStationState } returns stationState

        return wifi
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> unsafeInstance(): T {
        val unsafe = getUnsafe()
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, T::class.java) as T
    }

    private fun getUnsafe(): Any {
        val clazz = try {
            Class.forName("sun.misc.Unsafe")
        } catch (_: ClassNotFoundException) {
            Class.forName("jdk.internal.misc.Unsafe")
        }
        val f: Field = clazz.getDeclaredField("theUnsafe")
        f.isAccessible = true
        return f.get(null)
    }
}

