package com.greybox.projectmesh.messaging.ui.screens

import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.ui.models.ChatScreenModel
import com.greybox.projectmesh.user.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
@LooperMode(LooperMode.Mode.PAUSED)
class ChatScreenTest {

    @Test
    fun userStatusBar_composesWithoutCrashing_whenOnline() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        activity.runOnUiThread {
            activity.setContent {
                UserStatusBar(
                    userName = "Alice",
                    isOnline = true,
                    userAddress = "192.168.1.10"
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun userStatusBar_composesWithoutCrashing_whenOffline() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        activity.runOnUiThread {
            activity.setContent {
                UserStatusBar(
                    userName = "Bob",
                    isOnline = false,
                    userAddress = "192.168.1.20"
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun displayAllMessages_composesWithoutCrashing_whenNoMessages() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val uiState = ChatScreenModel(
            deviceName = "Bob",
            virtualAddress = InetAddress.getByName("192.168.1.20"),
            allChatMessages = emptyList(),
            offlineWarning = "Offline"
        )

        activity.runOnUiThread {
            activity.setContent {
                DisplayAllMessages(
                    uiState = uiState,
                    onClickButton = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun displayAllMessages_composesWithoutCrashing_whenMessagesExist() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockUserRepository = mockk<UserRepository>(relaxed = true)
        coEvery { mockUserRepository.getUserByIp(any()) } returns null
        GlobalApp.GlobalUserRepo.userRepository = mockUserRepository

        val mockMessage = mockk<Message>(relaxed = true)
        every { mockMessage.sender } returns "Peer"
        every { mockMessage.content } returns "Hello"
        every { mockMessage.file } returns null
        every { mockMessage.dateReceived } returns 1710000000000L

        val uiState = ChatScreenModel(
            deviceName = "Bob",
            virtualAddress = InetAddress.getByName("192.168.1.20"),
            allChatMessages = listOf(mockMessage),
            offlineWarning = null
        )

        activity.runOnUiThread {
            activity.setContent {
                DisplayAllMessages(
                    uiState = uiState,
                    onClickButton = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun messageBubble_composesWithoutCrashing_withMockMessage() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockMessage = mockk<Message>(relaxed = true)
        every { mockMessage.file } returns null
        every { mockMessage.dateReceived } returns 1710000000000L

        activity.runOnUiThread {
            activity.setContent {
                MessageBubble(
                    chatMessage = mockMessage,
                    sentBySelf = true,
                    sender = "Me",
                    modifier = Modifier,
                    messageContent = {}
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }
}