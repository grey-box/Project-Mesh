package com.greybox.projectmesh.messaging.ui.viewmodels

import android.content.SharedPreferences
import android.util.Log
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsHomeScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = ConversationsMainDispatcherRule()

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockRepository: ConversationRepository
    private lateinit var di: DI

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkObject(DeviceStatusManager)
        every { DeviceStatusManager.deviceStatusMap } returns MutableStateFlow(emptyMap())

        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getString("UUID", null) } returns "local-user"

        mockRepository = mockk(relaxed = true)
        coEvery { mockRepository.markAsRead(any()) } returns Unit
        coEvery { mockRepository.updateUserStatus(any(), any(), any()) } returns Unit

        di = DI {
            bind<SharedPreferences>(tag = "settings") with singleton { mockPrefs }
            bind<ConversationRepository>() with singleton { mockRepository }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun conversationsHomeScreenViewModel_loadsConversations_andFiltersOutSelf() = runTest {
        val selfConversation = mockk<Conversation>(relaxed = true)
        every { selfConversation.userUuid } returns "local-user"
        every { selfConversation.id } returns "self-conv"
        every { selfConversation.userName } returns "Me"

        val otherConversation = mockk<Conversation>(relaxed = true)
        every { otherConversation.userUuid } returns "remote-user"
        every { otherConversation.id } returns "remote-conv"
        every { otherConversation.userName } returns "Alice"

        every { mockRepository.getAllConversations() } returns flowOf(
            listOf(selfConversation, otherConversation)
        )

        val viewModel = ConversationsHomeScreenViewModel(di)

        advanceUntilIdle()
        val state = viewModel.uiState.first()

        assertFalse(state.isLoading)
        assertEquals(1, state.conversations.size)
        assertEquals("remote-user", state.conversations.first().userUuid)
        assertEquals("remote-conv", state.conversations.first().id)
        assertEquals(null, state.error)
    }

    @Test
    fun conversationsHomeScreenViewModel_setsError_whenRepositoryFlowFails() = runTest {
        every { mockRepository.getAllConversations() } returns flow {
            throw RuntimeException("boom")
        }

        val viewModel = ConversationsHomeScreenViewModel(di)

        advanceUntilIdle()
        val state = viewModel.uiState.first()

        assertFalse(state.isLoading)
        assertTrue(state.error != null)
        assertTrue(state.error!!.contains("Failed to load conversations"))
        assertTrue(state.error!!.contains("boom"))
    }

    @Test
    fun refreshConversations_callsRepositoryAgain() = runTest {
        every { mockRepository.getAllConversations() } returns flowOf(emptyList())

        val viewModel = ConversationsHomeScreenViewModel(di)
        advanceUntilIdle()

        viewModel.refreshConversations()
        advanceUntilIdle()

        io.mockk.verify(exactly = 2) { mockRepository.getAllConversations() }
    }

    @Test
    fun markConversationAsRead_delegatesToRepository() = runTest {
        every { mockRepository.getAllConversations() } returns flowOf(emptyList())

        val viewModel = ConversationsHomeScreenViewModel(di)
        advanceUntilIdle()

        viewModel.markConversationAsRead("conv-123")
        advanceUntilIdle()

        coVerify { mockRepository.markAsRead("conv-123") }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsMainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}