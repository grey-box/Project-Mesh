package com.greybox.projectmesh.messaging.repository

import com.greybox.projectmesh.messaging.data.dao.ConversationDao
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import com.greybox.projectmesh.user.UserEntity
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import java.net.URI
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationRepositoryTest {

    private lateinit var dao: ConversationDao
    private lateinit var repo: ConversationRepository
    private val di = DI {}

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        dao = mockk(relaxed = true)
        repo = ConversationRepository(dao, di)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        clearAllMocks()
    }

    @Test
    fun getConversationById_delegatesToDao_andReturnsResult() = runTest {
        val id = "abc"
        val expected = Conversation(
            id = id,
            userUuid = "u2",
            userName = "Bob",
            userAddress = "10.0.0.2",
            lastMessage = "hi",
            lastMessageTime = 123L,
            unreadCount = 0,
            isOnline = true
        )

        coEvery { dao.getConversationById(id) } returns expected

        val actual = repo.getConversationById(id)

        assertEquals(expected, actual)
        coVerify(exactly = 1) { dao.getConversationById(id) }
    }

    @Test
    fun getConversationById_whenDaoReturnsNull_returnsNull() = runTest {
        val id = "missing"
        coEvery { dao.getConversationById(id) } returns null

        val actual = repo.getConversationById(id)

        assertNull(actual)
        coVerify(exactly = 1) { dao.getConversationById(id) }
    }

    @Test
    fun getOrCreateConversation_whenMissing_createsAndInsertsConversation() = runTest {
        val localUuid = "local-1"
        val remote = UserEntity(uuid = "remote-1", name = "Alice", address = "10.0.0.10", lastSeen = null)

        val expectedId = ConversationUtils.createConversationId(localUuid, remote.uuid)

        coEvery { dao.getConversationById(expectedId) } returns null
        coEvery { dao.insertConversation(any()) } returns Unit

        val before = System.currentTimeMillis()
        val result = repo.getOrCreateConversation(localUuid, remote)
        val after = System.currentTimeMillis()

        assertEquals(expectedId, result.id)
        assertEquals(remote.uuid, result.userUuid)
        assertEquals(remote.name, result.userName)
        assertEquals(remote.address, result.userAddress)
        assertNull(result.lastMessage)
        assertEquals(0, result.unreadCount)
        assertTrue(result.isOnline)

        assertTrue(result.lastMessageTime in before..after)

        coVerify(exactly = 1) { dao.getConversationById(expectedId) }
        coVerify(exactly = 1) {
            dao.insertConversation(match {
                it.id == expectedId &&
                    it.userUuid == remote.uuid &&
                    it.userName == remote.name &&
                    it.userAddress == remote.address &&
                    it.lastMessage == null &&
                    it.unreadCount == 0 &&
                    it.isOnline == true
            })
        }
    }

    @Test
    fun getOrCreateConversation_whenExists_doesNotInsert_andReturnsExisting() = runTest {
        val localUuid = "local-2"
        val remote = UserEntity(uuid = "remote-2", name = "Eve", address = null, lastSeen = null)

        val id = ConversationUtils.createConversationId(localUuid, remote.uuid)
        val existing = Conversation(
            id = id,
            userUuid = remote.uuid,
            userName = remote.name,
            userAddress = null,
            lastMessage = "old",
            lastMessageTime = 999L,
            unreadCount = 5,
            isOnline = false
        )

        coEvery { dao.getConversationById(id) } returns existing

        val result = repo.getOrCreateConversation(localUuid, remote)

        assertEquals(existing, result)
        coVerify(exactly = 1) { dao.getConversationById(id) }
        coVerify(exactly = 0) { dao.insertConversation(any()) }
    }

    @Test
    fun updateWithMessage_callsUpdateLastMessageTwice_andIncrementsUnread_ifSenderNotMe() = runTest {
        val convoId = "c1"
        val msg = Message(
            id = 1,
            dateReceived = 444L,
            content = "hello",
            sender = "Alice",
            chat = convoId,
            file = null
        )

        repo.updateWithMessage(convoId, msg)

        coVerify(exactly = 2) {
            dao.updateLastMessage(
                conversationId = convoId,
                lastMessage = msg.content,
                timestamp = msg.dateReceived
            )
        }
        coVerify(exactly = 1) { dao.incrementUnreadCount(convoId) }
    }

    @Test
    fun updateWithMessage_doesNotIncrementUnread_ifSenderIsMe() = runTest {
        val convoId = "c2"
        val msg = Message(
            id = 2,
            dateReceived = 555L,
            content = "sent by me",
            sender = "Me",
            chat = convoId,
            file = URI.create("file://example")
        )

        repo.updateWithMessage(convoId, msg)

        coVerify(exactly = 2) {
            dao.updateLastMessage(
                conversationId = convoId,
                lastMessage = msg.content,
                timestamp = msg.dateReceived
            )
        }
        coVerify(exactly = 0) { dao.incrementUnreadCount(convoId) }
    }

    @Test
    fun markAsRead_clearsUnreadCount() = runTest {
        val convoId = "c3"

        repo.markAsRead(convoId)

        coVerify(exactly = 1) { dao.clearUnreadCount(convoId) }
    }

    @Test
    fun updateUserStatus_callsDao_andDoesNotThrow() = runTest {
        repo.updateUserStatus(userUuid = "u1", isOnline = true, userAddress = "10.0.0.9")

        coVerify(exactly = 1) {
            dao.updateUserConnectionStatus(userUuid = "u1", isOnline = true, userAddress = "10.0.0.9")
        }
    }

    @Test
    fun updateUserStatus_whenDaoThrows_exceptionIsCaught() = runTest {
        coEvery {
            dao.updateUserConnectionStatus(any(), any(), any())
        } throws RuntimeException("db error")

        repo.updateUserStatus(userUuid = "u2", isOnline = false, userAddress = null)

        coVerify(exactly = 1) {
            dao.updateUserConnectionStatus(userUuid = "u2", isOnline = false, userAddress = null)
        }

        assertNotNull("reached end without throwing", Unit)
    }
}
