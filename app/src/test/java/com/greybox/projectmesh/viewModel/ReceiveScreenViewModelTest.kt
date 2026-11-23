package com.greybox.projectmesh.viewModel

import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.R
import com.greybox.projectmesh.data.FileSaveResult
import com.greybox.projectmesh.data.ReceivedFileSaver
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testutil.FakeSettingsRepository
import com.greybox.projectmesh.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.io.File
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val transfers = MutableStateFlow<List<AppServer.IncomingTransferInfo>>(emptyList())
    private val settings = FakeSettingsRepository()
    private val server = mockk<AppServer>()
    private val saver = mockk<ReceivedFileSaver>()

    private fun buildViewModel(): ReceiveScreenViewModel {
        every { server.incomingTransfers } returns transfers
        val di = DI {
            bind<AppServer>() with singleton { server }
            bind<File>(tag = GlobalApp.TAG_RECEIVE_DIR) with singleton { temporaryFolder.root }
            bind<SettingsRepository>() with singleton { settings }
            bind<ReceivedFileSaver>() with singleton { saver }
        }
        return ReceiveScreenViewModel(di, mainDispatcherRule.dispatcher)
    }

    @Test
    fun `transfer state starts with the current list and follows updates`() = runTest {
        val pending = transfer()
        transfers.value = listOf(pending)
        val vm = buildViewModel()
        assertEquals(listOf(pending), vm.state.value.incomingTransfers)

        val completed = pending.copy(status = AppServer.Status.COMPLETED)
        transfers.value = listOf(completed)
        runCurrent()

        assertEquals(listOf(completed), vm.state.value.incomingTransfers)
    }

    @Test
    fun `accept hands a pending transfer to the server once`() = runTest {
        val pending = transfer()
        val target = File(temporaryFolder.root, pending.name)
        transfers.value = listOf(pending)
        every { server.acceptIncomingTransfer(pending, target) } answers {
            transfers.value = listOf(pending.copy(status = AppServer.Status.IN_PROGRESS))
        }
        val vm = buildViewModel()

        vm.onAccept(pending)
        vm.onAccept(pending)
        runCurrent()

        verify(exactly = 1) { server.acceptIncomingTransfer(pending, target) }
    }

    @Test
    fun `decline hands the chosen transfer to the server`() = runTest {
        val pending = transfer()
        coEvery { server.onDeclineIncomingTransfer(pending) } returns Unit
        val vm = buildViewModel()

        vm.onDecline(pending)
        runCurrent()

        coVerify(exactly = 1) { server.onDeclineIncomingTransfer(pending) }
    }

    @Test
    fun `delete hands the chosen transfer to the server`() = runTest {
        val completed = transfer().copy(status = AppServer.Status.COMPLETED)
        coEvery { server.onDeleteIncomingTransfer(completed) } returns Unit
        val vm = buildViewModel()

        vm.onDelete(completed)
        runCurrent()

        coVerify(exactly = 1) { server.onDeleteIncomingTransfer(completed) }
    }

    @Test
    fun `download uses the configured folder and clears the success message after showing it`() =
        runTest {
            val completed = transfer().copy(status = AppServer.Status.COMPLETED)
            val vm = buildViewModel()
            settings.setSaveToFolder("content://selected-folder")
            coEvery { saver.save(completed, "content://selected-folder") } returns FileSaveResult.SUCCESS

            vm.onDownload(completed)
            runCurrent()

            coVerify(exactly = 1) { saver.save(completed, "content://selected-folder") }
            assertEquals(R.string.receive_file_saved, vm.state.value.userMessage?.textRes)
            vm.onUserMessageShown()
            assertNull(vm.state.value.userMessage)
        }

    @Test
    fun `download reports when the source file is missing`() = runTest {
        val completed = transfer().copy(status = AppServer.Status.COMPLETED)
        coEvery { saver.save(completed, any()) } returns FileSaveResult.FILE_UNAVAILABLE
        val vm = buildViewModel()

        vm.onDownload(completed)
        runCurrent()

        assertEquals(R.string.error_file_unavailable, vm.state.value.userMessage?.textRes)
    }

    @Test
    fun `automatic acceptance reacts to the setting and only accepts pending files`() = runTest {
        val pending = transfer()
        val completed = pending.copy(id = 2, status = AppServer.Status.COMPLETED)
        val next = pending.copy(id = 3)
        transfers.value = listOf(pending, completed)
        every { server.acceptIncomingTransfer(pending, any()) } answers {
            transfers.value = listOf(pending.copy(status = AppServer.Status.IN_PROGRESS), completed)
        }
        val vm = buildViewModel()
        val observer = backgroundScope.launch { vm.autoAcceptWhileVisible() }

        runCurrent()
        verify(exactly = 0) { server.acceptIncomingTransfer(any(), any(), any()) }

        settings.setAutoFinish(true)
        runCurrent()
        verify(exactly = 1) { server.acceptIncomingTransfer(pending, any()) }
        verify(exactly = 0) { server.acceptIncomingTransfer(completed, any()) }

        observer.cancel()
        transfers.value = listOf(next)
        runCurrent()
        verify(exactly = 0) { server.acceptIncomingTransfer(next, any()) }
    }

    private fun transfer() = AppServer.IncomingTransferInfo(
        id = 1,
        fromHost = InetAddress.getLoopbackAddress(),
        name = "demo.txt",
        size = 10,
    )
}