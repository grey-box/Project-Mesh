package com.greybox.projectmesh.viewModel

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.greybox.projectmesh.server.AppServer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

class SelectDestNodeScreenViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_sets_uris_and_collect_updates_allNodes() = runTest {
        val uri1 = mockk<Uri>(relaxed = true)
        val uri2 = mockk<Uri>(relaxed = true)
        val sendUris = listOf(uri1, uri2)

        val appServer = mockk<AppServer>(relaxed = true)

        val initialState = mockk<LocalNodeState>(relaxed = true)
        val nodeStateFlow = MutableStateFlow(initialState)

        val node = mockk<AndroidVirtualNode>()
        every { node.state } returns nodeStateFlow

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<AndroidVirtualNode>() with singleton { node }
        }

        val vm = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = sendUris,
            popBackWhenDone = {}
        )

        mainDispatcher.scheduler.advanceUntilIdle()

        val s0 = vm.uiState.first { it.uris.size == 2 }
        assertEquals(2, s0.uris.size)

        val msg = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        val newMap = mapOf(123 to msg)

        val updatedState = mockk<LocalNodeState>()
        every { updatedState.originatorMessages } returns newMap

        nodeStateFlow.value = updatedState
        mainDispatcher.scheduler.advanceUntilIdle()

        val s1 = vm.uiState.first { it.allNodes.size == 1 }
        assertEquals(1, s1.allNodes.size)
    }
}

  /*  @Test
    fun onClickReceiver_updates_contacting_and_pops_when_any_transfer_succeeds() = runTest {
        val uri1 = mockk<Uri>(relaxed = true)
        val uri2 = mockk<Uri>(relaxed = true)
        val sendUris = listOf(uri1, uri2)

        val appServer = mockk<AppServer>(relaxed = true)
        // ensure no exception inside try{} so it returns true and pops
        coEvery { appServer.addOutgoingTransfer(any(), any()) } returns mockk(relaxed = true)

        val node = mockk<AndroidVirtualNode>()
        every { node.state } returns MutableStateFlow(mockk<LocalNodeState>(relaxed = true))

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<AndroidVirtualNode>() with singleton { node }
        }

        var popped = false

        val vm = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = sendUris,
            popBackWhenDone = { popped = true }
        )

        val address = 0xC0A80001.toInt() // 192.168.0.1
        vm.onClickReceiver(address)

        val state = vm.uiState.first { it.contactingInProgressDevice != null }
        assertEquals(address.addressToDotNotation(), state.contactingInProgressDevice)

        withTimeout(5_000) {
            while (!popped) {
                mainDispatcher.scheduler.advanceUntilIdle()
                delay(10)
            }
        }

        assertTrue(popped)
    }
}
   */