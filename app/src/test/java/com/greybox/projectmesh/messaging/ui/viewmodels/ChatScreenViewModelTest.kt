package com.greybox.projectmesh.messaging.ui.viewmodels

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.user.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class ChatScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(DeviceStatusManager)
        every { DeviceStatusManager.deviceStatusMap } returns MutableStateFlow(emptyMap())
        every { DeviceStatusManager.isDeviceOnline(any()) } returns false
        every { DeviceStatusManager.verifyDeviceStatus(any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(
        virtualAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
        initialMessages: List<Message> = emptyList(),
        messageFlowMessages: List<Message> = initialMessages,
        addTransferResult: AppServer.OutgoingTransferInfo = mockk(relaxed = true)
    ): Triple<ChatScreenViewModel, MeshDatabase, AppServer> {
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString("UUID", null) } returns "local-user"

        val mockDb = mockk<MeshDatabase>(relaxed = true)
        every { mockDb.messageDao().getAll() } returns emptyList()
        every { mockDb.messageDao().getChatMessagesSync(any()) } returns initialMessages
        every { mockDb.messageDao().getChatMessagesFlow(any()) } returns flowOf(messageFlowMessages)
        every { mockDb.messageDao().getChatMessagesFlowMultipleNames(any()) } returns flowOf(messageFlowMessages)
        coEvery { mockDb.messageDao().addMessage(any()) } returns Unit

        val mockConversationRepo = mockk<ConversationRepository>(relaxed = true)

        val mockAppServer = mockk<AppServer>(relaxed = true)
        every { mockAppServer.addOutgoingTransfer(any(), any()) } returns addTransferResult

        val mockUserRepo = mockk<UserRepository>(relaxed = true)
        coEvery { mockUserRepo.getUserByIp(any()) } returns null
        GlobalApp.GlobalUserRepo.userRepository = mockUserRepo

        val di = DI {
            bind<SharedPreferences>(tag = "settings") with singleton { mockPrefs }
            bind<MeshDatabase>() with singleton { mockDb }
            bind<AppServer>() with singleton { mockAppServer }
            bind<ConversationRepository>() with singleton { mockConversationRepo }
        }

        val savedStateHandle = SavedStateHandle(
            mapOf("virtualAddress" to virtualAddress)
        )

        return Triple(ChatScreenViewModel(di, savedStateHandle), mockDb, mockAppServer)
    }

    @Test
    fun chatScreenViewModel_initializesUiState_withUnknownNameAndAddress() = runTest {
        val virtualAddress = InetAddress.getByName("0.0.0.0")
        val (viewModel, _, _) = createViewModel(
            virtualAddress = virtualAddress,
            initialMessages = emptyList(),
            messageFlowMessages = emptyList()
        )

        advanceUntilIdle()
        val state = viewModel.uiState.first()

        assertEquals("Unknown", state.deviceName)
        assertEquals(virtualAddress, state.virtualAddress)
        assertTrue(state.allChatMessages.isEmpty())
    }


    @Test
    fun sendChatMessage_savesMessageLocally_andSetsOfflineWarning_whenDeviceOffline() = runTest {
        val virtualAddress = InetAddress.getByName("0.0.0.0")
        every { DeviceStatusManager.isDeviceOnline(virtualAddress.hostAddress) } returns false
        every { DeviceStatusManager.verifyDeviceStatus(any()) } just runs

        val (viewModel, mockDb, _) = createViewModel(
            virtualAddress = virtualAddress,
            initialMessages = emptyList(),
            messageFlowMessages = emptyList()
        )

        viewModel.sendChatMessage(
            virtualAddress = virtualAddress,
            message = "Test message",
            file = null
        )

        advanceUntilIdle()
        val state = viewModel.uiState.first()

        coVerify { mockDb.messageDao().addMessage(any()) }
        assertTrue(state.offlineWarning != null)
        assertTrue(state.offlineWarning!!.contains("offline", ignoreCase = true))
    }

    @Test
    fun addOutgoingTransfer_delegatesToAppServer() = runTest {
        val expected = mockk<AppServer.OutgoingTransferInfo>(relaxed = true)
        val virtualAddress = InetAddress.getByName("0.0.0.0")
        val fileUri = mockk<Uri>(relaxed = true)

        val (viewModel, _, _) = createViewModel(
            virtualAddress = virtualAddress,
            addTransferResult = expected,
            initialMessages = emptyList(),
            messageFlowMessages = emptyList()
        )

        val result = viewModel.addOutgoingTransfer(fileUri, virtualAddress)

        assertSame(expected, result)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}