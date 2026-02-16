package com.greybox.projectmesh.viewModel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testutil.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
@Config(sdk = [29], manifest = Config.NONE)
class SendScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appServer: AppServer
    private lateinit var outgoingFlow: MutableStateFlow<List<AppServer.OutgoingTransferInfo>>
    private lateinit var di: DI

    @Before
    fun setUp() {
        outgoingFlow = MutableStateFlow(emptyList())
        appServer = mockk(relaxed = true)

        every { appServer.outgoingTransfers } returns outgoingFlow
        coEvery { appServer.removeOutgoingTransfer(any()) } returns Unit

        di = DI {
            bind<AppServer>() with singleton { appServer }
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun init_collectsOutgoingTransfers_andUpdatesUiState() = runTest {
        val vm = SendScreenViewModel(di, SavedStateHandle()) { }

        advanceUntilIdle()
        assertEquals(emptyList<AppServer.OutgoingTransferInfo>(), latest(vm))

        val t1 = outgoingTransfer(id = 1, name = "a.txt")
        val t2 = outgoingTransfer(id = 2, name = "b.txt")
        outgoingFlow.value = listOf(t1, t2)

        advanceUntilIdle()
        assertEquals(listOf(t1, t2), latest(vm))
    }

    @Test
    fun onFileChosen_callsCallbackWithUris() = runTest {
        var got: List<Uri>? = null
        val vm = SendScreenViewModel(di, SavedStateHandle()) { uris -> got = uris }

        val uris = listOf(Uri.parse("content://test/one"), Uri.parse("content://test/two"))
        vm.onFileChosen(uris)

        assertEquals(uris, got)
    }

    @Test
    fun onDelete_callsRemoveOutgoingTransferWithId() = runTest {
        val vm = SendScreenViewModel(di, SavedStateHandle()) { }
        val t = outgoingTransfer(id = 77, name = "gone.txt")

        vm.onDelete(t)
        advanceUntilIdle()

        coVerify(exactly = 1) { appServer.removeOutgoingTransfer(77) }
    }

    @Test
    fun uiState_emitsUpdates_whenOutgoingTransfersChangesMultipleTimes() = runTest {
        val vm = SendScreenViewModel(di, SavedStateHandle()) { }
        advanceUntilIdle()

        val t1 = outgoingTransfer(id = 1, name = "a.txt")
        val t2 = outgoingTransfer(id = 2, name = "b.txt")
        val t3 = outgoingTransfer(id = 3, name = "c.txt")

        vm.uiState.test {
            awaitItem()
            outgoingFlow.value = listOf(t1)
            advanceUntilIdle()
            assertEquals(listOf(t1), awaitItem().outgoingTransfers)

            outgoingFlow.value = listOf(t1, t2)
            advanceUntilIdle()
            assertEquals(listOf(t1, t2), awaitItem().outgoingTransfers)

            outgoingFlow.value = listOf(t3)
            advanceUntilIdle()
            assertEquals(listOf(t3), awaitItem().outgoingTransfers)

            cancelAndConsumeRemainingEvents()
        }
    }

    private suspend fun latest(vm: SendScreenViewModel): List<AppServer.OutgoingTransferInfo> {
        var out: List<AppServer.OutgoingTransferInfo> = emptyList()
        vm.uiState.test {
            out = awaitItem().outgoingTransfers
            cancelAndConsumeRemainingEvents()
        }
        return out
    }

    private fun outgoingTransfer(id: Int, name: String): AppServer.OutgoingTransferInfo {
        return AppServer.OutgoingTransferInfo(
            id = id,
            name = name,
            uri = Uri.parse("content://test/$id"),
            toHost = InetAddress.getByName("192.168.1.${10 + id}"),
            size = 1234 + id
        )
    }
}
