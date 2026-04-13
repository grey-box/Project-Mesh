package com.greybox.projectmesh.messaging.network

import android.content.SharedPreferences
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.messaging.repository.MessageRepository
import com.greybox.projectmesh.user.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class MessageServiceTest {

    private lateinit var networkHandler: MessageNetworkHandler
    private lateinit var messageRepository: MessageRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var settingsPrefs: SharedPreferences
    private lateinit var service: MessageService

    @Before
    fun setUp() {
        networkHandler = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        settingsPrefs = mockk(relaxed = true)

        val di = DI {
            bind<MessageNetworkHandler>() with singleton { networkHandler }
            bind<MessageRepository>() with singleton { messageRepository }
            bind<ConversationRepository>() with singleton { conversationRepository }
            bind<UserRepository>() with singleton { userRepository }
            bind<SharedPreferences>(tag = "settings") with singleton { settingsPrefs }
        }

        service = MessageService(di)
    }

    @Test
    fun sendMessage_savesFirst_thenSendsOverNetwork_withNullFile() = runTest {
        val addr = InetAddress.getByName("10.0.0.5")
        val msg = Message(
            id = 0,
            dateReceived = 12345L,
            content = "hello",
            sender = "Me",
            chat = "chat-1",
            file = null
        )

        service.sendMessage(addr, msg)

        coVerifyOrder {
            messageRepository.addMessage(msg)
            networkHandler.sendChatMessage(
                address = addr,
                time = msg.dateReceived,
                message = msg.content,
                file = null
            )
        }
    }

    @Test
    fun sendMessage_whenRepositoryThrows_propagates_andDoesNotSend() = runTest {
        val addr = InetAddress.getByName("10.0.0.6")
        val msg = Message(0, 1L, "x", "Me", "chat-x", null)

        coEvery { messageRepository.addMessage(msg) } throws RuntimeException("db fail")

        try {
            service.sendMessage(addr, msg)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("db fail", e.message)
        }

        coVerify(exactly = 0) {
            networkHandler.sendChatMessage(any(), any(), any(), any())
        }
    }

    @Test
    fun sendMessage_whenNetworkThrows_propagates_afterSave() = runTest {
        val addr = InetAddress.getByName("10.0.0.7")
        val msg = Message(0, 2L, "y", "Me", "chat-y", null)

        every {
            networkHandler.sendChatMessage(
                address = addr,
                time = msg.dateReceived,
                message = msg.content,
                file = null
            )
        } throws RuntimeException("network fail")

        try {
            service.sendMessage(addr, msg)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("network fail", e.message)
        }

        coVerify(exactly = 1) { messageRepository.addMessage(msg) }
    }
}
