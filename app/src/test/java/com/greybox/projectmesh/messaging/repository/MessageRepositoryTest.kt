package com.greybox.projectmesh.messaging.repository

import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.entities.Message
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import java.net.URI
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryTest {

    private lateinit var dao: MessageDao
    private lateinit var repo: MessageRepository
    private val di = DI {}

    @Before
    fun setUp() {
        dao = mockk(relaxed = true)
        repo = MessageRepository(dao, di)
    }

    @Test
    fun getChatMessages_delegatesToDao() = runTest {
        val chatId = "chat-1"
        val list = listOf(
            Message(1, 10L, "a", "Alice", chatId, null),
            Message(2, 20L, "b", "Me", chatId, URI.create("file://x"))
        )
        every { dao.getChatMessagesFlow(chatId) } returns flowOf(list)

        val actual = repo.getChatMessages(chatId).first()

        assertEquals(list, actual)
        verify(exactly = 1) { dao.getChatMessagesFlow(chatId) }
    }

    @Test
    fun getAllMessages_delegatesToDao() = runTest {
        every { dao.getAllFlow() } returns flowOf(emptyList())

        repo.getAllMessages()

        verify(exactly = 1) { dao.getAllFlow() }
    }

    @Test
    fun addMessage_callsDaoAddMessage() = runTest {
        val msg = Message(0, 123L, "hello", "Alice", "chat-2", null)

        repo.addMessage(msg)

        coVerify(exactly = 1) { dao.addMessage(msg) }
    }

    @Test
    fun clearMessages_callsDaoClearTable() = runTest {
        repo.clearMessages()

        verify(exactly = 1) { dao.clearTable() }
    }
}
