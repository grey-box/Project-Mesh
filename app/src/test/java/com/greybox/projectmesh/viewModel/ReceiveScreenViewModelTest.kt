package com.greybox.projectmesh.viewModel

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.server.AppServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.File

class ReceiveScreenViewModelTest {

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
    fun uiState_updates_when_incomingTransfers_emits() = runTest {
        val incomingFlow = MutableStateFlow<List<AppServer.IncomingTransferInfo>>(emptyList())

        val appServer = mockk<AppServer>(relaxed = true)
        every { appServer.incomingTransfers } returns incomingFlow

        val receiveDir = File(createTempDir(prefix = "recv"), "inbox").apply { mkdirs() }

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<File>(tag = GlobalApp.TAG_RECEIVE_DIR) with singleton { receiveDir }
        }

        val vm = ReceiveScreenViewModel(di, SavedStateHandle())

        val t1 = mockk<AppServer.IncomingTransferInfo>(relaxed = true)
        val t2 = mockk<AppServer.IncomingTransferInfo>(relaxed = true)

        incomingFlow.value = listOf(t1, t2)
        mainDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.first { it.incomingTransfers.size == 2 }
        assertEquals(2, state.incomingTransfers.size)
    }

    @Test
    fun onAccept_creates_dir_and_calls_acceptIncomingTransfer_with_destination_file() = runTest {
        val incomingFlow = MutableStateFlow<List<AppServer.IncomingTransferInfo>>(emptyList())

        val appServer = mockk<AppServer>(relaxed = true)
        every { appServer.incomingTransfers } returns incomingFlow
        coEvery { appServer.acceptIncomingTransfer(any(), any()) } returns Unit

        val receiveDir = File(createTempDir(prefix = "recv"), "inbox") // should not exist yet

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<File>(tag = GlobalApp.TAG_RECEIVE_DIR) with singleton { receiveDir }
        }

        val vm = ReceiveScreenViewModel(di, SavedStateHandle())

        val transfer = mockk<AppServer.IncomingTransferInfo>()
        every { transfer.name } returns "file.bin"

        vm.onAccept(transfer)

        mainDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            appServer.acceptIncomingTransfer(
                eq(transfer),
                match { it.path == File(receiveDir, "file.bin").path }
            )
        }
    }

    @Test
    fun onDecline_calls_onDeclineIncomingTransfer() = runTest {
        val incomingFlow = MutableStateFlow<List<AppServer.IncomingTransferInfo>>(emptyList())

        val appServer = mockk<AppServer>(relaxed = true)
        every { appServer.incomingTransfers } returns incomingFlow

        val receiveDir = File(createTempDir(prefix = "recv"), "inbox").apply { mkdirs() }

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<File>(tag = GlobalApp.TAG_RECEIVE_DIR) with singleton { receiveDir }
        }

        val vm = ReceiveScreenViewModel(di, SavedStateHandle())

        val transfer = mockk<AppServer.IncomingTransferInfo>(relaxed = true)

        vm.onDecline(transfer)
        mainDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appServer.onDeclineIncomingTransfer(eq(transfer)) }
    }

    @Test
    fun onDelete_calls_onDeleteIncomingTransfer() = runTest {
        val incomingFlow = MutableStateFlow<List<AppServer.IncomingTransferInfo>>(emptyList())

        val appServer = mockk<AppServer>(relaxed = true)
        every { appServer.incomingTransfers } returns incomingFlow

        val receiveDir = File(createTempDir(prefix = "recv"), "inbox").apply { mkdirs() }

        val di = DI {
            bind<AppServer>() with singleton { appServer }
            bind<File>(tag = GlobalApp.TAG_RECEIVE_DIR) with singleton { receiveDir }
        }

        val vm = ReceiveScreenViewModel(di, SavedStateHandle())

        val transfer = mockk<AppServer.IncomingTransferInfo>(relaxed = true)

        vm.onDelete(transfer)
        mainDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { appServer.onDeleteIncomingTransfer(eq(transfer)) }
    }
}
