package com.greybox.projectmesh.viewModel

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun init_setsUris_andCollectsNodeStateIntoAllNodes() = runTest {
        val uri1 = Uri.parse("content://test/one")
        val uri2 = Uri.parse("content://test/two")

        val msg1 = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        val msg2 = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        val originators = mapOf(1 to msg1, 2 to msg2)

        val initialState = mockk<LocalNodeState>(relaxed = true)
        every { initialState.originatorMessages } returns emptyMap()
        val stateFlow = MutableStateFlow(initialState)

        val node = mockk<AndroidVirtualNode>(relaxed = false)
        every { node.state } returns stateFlow

        val appServer = mockk<AppServer>(relaxed = false)

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }

        val viewModel = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = listOf(uri1, uri2),
            popBackWhenDone = { }
        )

        advanceUntilIdle()

        val initialUi = viewModel.uiState.first()
        assertEquals(listOf(uri1, uri2), initialUi.uris)
        assertEquals(emptyMap<Int, VirtualNode.LastOriginatorMessage>(), initialUi.allNodes)
        assertNull(initialUi.contactingInProgressDevice)

        val nextState = mockk<LocalNodeState>(relaxed = true)
        every { nextState.originatorMessages } returns originators
        stateFlow.value = nextState

        advanceUntilIdle()

        val updatedUi = viewModel.uiState.first()
        assertEquals(listOf(uri1, uri2), updatedUi.uris)
        assertEquals(originators, updatedUi.allNodes)
        assertNull(updatedUi.contactingInProgressDevice)

        confirmVerified(appServer)
    }

    @Test
    fun onClickReceiver_updatesContactingDevice_andCallsAddOutgoingTransfer_forEachUri() = runTest {
        val uri1 = Uri.parse("content://test/a")
        val uri2 = Uri.parse("content://test/b")
        val dest = InetAddress.getByName("192.168.0.42")
        val destKey = dest.requireAddressAsInt()

        val state = mockk<LocalNodeState>(relaxed = true)
        every { state.originatorMessages } returns emptyMap()

        val node = mockk<AndroidVirtualNode>(relaxed = false)
        every { node.state } returns MutableStateFlow(state)

        val appServer = mockk<AppServer>(relaxed = false)
        val outInfo = mockk<AppServer.OutgoingTransferInfo>(relaxed = true)

        coEvery { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) } returns outInfo
        coEvery { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) } returns outInfo

        var popped = 0

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }

        val viewModel = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = listOf(uri1, uri2),
            popBackWhenDone = { popped += 1 }
        )

        advanceUntilIdle()

        viewModel.onClickReceiver(destKey)
        advanceUntilIdle()

        val ui = viewModel.uiState.first()
        assertEquals("192.168.0.42", ui.contactingInProgressDevice)
        assertEquals(listOf(uri1, uri2), ui.uris)
        assertTrue(popped == 1)

        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) }
        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) }

        confirmVerified(appServer)
    }

    @Test
    fun onClickReceiver_whenAllTransfersThrow_doesNotPopBack() = runTest {
        val uri1 = Uri.parse("content://test/a")
        val uri2 = Uri.parse("content://test/b")
        val dest = InetAddress.getByName("10.0.0.9")
        val destKey = dest.requireAddressAsInt()

        val state = mockk<LocalNodeState>(relaxed = true)
        every { state.originatorMessages } returns emptyMap()

        val node = mockk<AndroidVirtualNode>(relaxed = false)
        every { node.state } returns MutableStateFlow(state)

        val appServer = mockk<AppServer>(relaxed = false)

        coEvery { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) } throws RuntimeException("fail")
        coEvery { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) } throws RuntimeException("fail")

        var popped = 0

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }

        val viewModel = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = listOf(uri1, uri2),
            popBackWhenDone = { popped += 1 }
        )

        advanceUntilIdle()

        viewModel.onClickReceiver(destKey)
        advanceUntilIdle()

        val ui = viewModel.uiState.first()
        assertEquals("10.0.0.9", ui.contactingInProgressDevice)
        assertEquals(0, popped)

        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) }
        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) }

        confirmVerified(appServer)
    }

    @Test
    fun onClickReceiver_whenSomeTransfersThrow_stillPopsBackIfAnySucceeded() = runTest {
        val uri1 = Uri.parse("content://test/a")
        val uri2 = Uri.parse("content://test/b")
        val uri3 = Uri.parse("content://test/c")
        val dest = InetAddress.getByName("172.16.0.5")
        val destKey = dest.requireAddressAsInt()

        val state = mockk<LocalNodeState>(relaxed = true)
        every { state.originatorMessages } returns emptyMap()

        val node = mockk<AndroidVirtualNode>(relaxed = false)
        every { node.state } returns MutableStateFlow(state)

        val appServer = mockk<AppServer>(relaxed = false)
        val outInfo = mockk<AppServer.OutgoingTransferInfo>(relaxed = true)

        coEvery { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) } returns outInfo
        coEvery { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) } throws RuntimeException("fail")
        coEvery { appServer.addOutgoingTransfer(uri = uri3, toNode = dest, toPort = any()) } throws RuntimeException("fail")

        var popped = 0

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }

        val viewModel = SelectDestNodeScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            sendUris = listOf(uri1, uri2, uri3),
            popBackWhenDone = { popped += 1 }
        )

        advanceUntilIdle()

        viewModel.onClickReceiver(destKey)
        advanceUntilIdle()

        val ui = viewModel.uiState.first()
        assertEquals("172.16.0.5", ui.contactingInProgressDevice)
        assertEquals(1, popped)

        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri1, toNode = dest, toPort = any()) }
        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri2, toNode = dest, toPort = any()) }
        coVerify(exactly = 1) { appServer.addOutgoingTransfer(uri = uri3, toNode = dest, toPort = any()) }

        confirmVerified(appServer)
    }
}
