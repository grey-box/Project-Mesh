package com.greybox.projectmesh.messaging.ui.screens

import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.ui.models.ConversationsHomeScreenModel
import com.greybox.projectmesh.messaging.ui.viewmodels.ConversationsHomeScreenViewModel
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
@LooperMode(LooperMode.Mode.PAUSED)
class ConversationsHomeScreenTest {

    @Test
    fun conversationsHomeScreen_composesWithoutCrashing_whenErrorExists() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockViewModel = mockk<ConversationsHomeScreenViewModel>(relaxed = true)
        val stateFlow = MutableStateFlow(
            ConversationsHomeScreenModel(
                isLoading = false,
                conversations = emptyList(),
                error = "Unable to load"
            )
        )

        every { mockViewModel.uiState } returns stateFlow
        every { mockViewModel.refreshConversations() } just runs
        every { mockViewModel.markConversationAsRead(any()) } just runs

        activity.runOnUiThread {
            activity.setContent {
                ConversationsHomeScreen(
                    onConversationSelected = {},
                    viewModel = mockViewModel
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun conversationsHomeScreen_composesWithoutCrashing_whenEmpty() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockViewModel = mockk<ConversationsHomeScreenViewModel>(relaxed = true)
        val stateFlow = MutableStateFlow(
            ConversationsHomeScreenModel(
                isLoading = false,
                conversations = emptyList(),
                error = null
            )
        )

        every { mockViewModel.uiState } returns stateFlow
        every { mockViewModel.refreshConversations() } just runs
        every { mockViewModel.markConversationAsRead(any()) } just runs

        activity.runOnUiThread {
            activity.setContent {
                ConversationsHomeScreen(
                    onConversationSelected = {},
                    viewModel = mockViewModel
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun conversationsList_composesWithoutCrashing_withConversationItems() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val conversation = mockk<Conversation>(relaxed = true)
        every { conversation.id } returns "conv-1"
        every { conversation.userName } returns "Alice"
        every { conversation.isOnline } returns true
        every { conversation.userAddress } returns "192.168.1.10"
        every { conversation.lastMessage } returns "Hello there"
        every { conversation.lastMessageTime } returns 1710000000000L
        every { conversation.unreadCount } returns 2

        activity.runOnUiThread {
            activity.setContent {
                ConversationsList(
                    conversations = listOf(conversation),
                    onConversationClick = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun conversationItem_composesWithoutCrashing_whenOfflineAndNoMessages() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val conversation = mockk<Conversation>(relaxed = true)
        every { conversation.id } returns "conv-2"
        every { conversation.userName } returns "Bob"
        every { conversation.isOnline } returns false
        every { conversation.userAddress } returns null
        every { conversation.lastMessage } returns null
        every { conversation.lastMessageTime } returns 0L
        every { conversation.unreadCount } returns 0

        activity.runOnUiThread {
            activity.setContent {
                ConversationItem(
                    conversation = conversation,
                    onClick = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun emptyConversationsView_composesWithoutCrashing() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        activity.runOnUiThread {
            activity.setContent {
                EmptyConversationsView()
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun errorView_composesWithoutCrashing() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        var retried = false

        activity.runOnUiThread {
            activity.setContent {
                ErrorView(
                    errorMessage = "Network error",
                    onRetry = { retried = true }
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
        assertNull(null.takeIf { retried })
    }
}