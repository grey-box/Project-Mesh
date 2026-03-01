package com.greybox.projectmesh.messaging.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.Conversation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ConversationDaoTest {

    private lateinit var db: MeshDatabase
    private lateinit var dao: ConversationDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeshDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = db.conversationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_and_getById_and_getByUserUuid_work() = runTest {
        val c = conversation(id = "c1", userUuid = "u1", userName = "Alice", time = 10L)

        dao.insertConversation(c)

        val byId = dao.getConversationById("c1")
        val byUser = dao.getConversationByUserUuid("u1")

        assertEquals(c, byId)
        assertEquals(c, byUser)
    }

    @Test
    fun getAllConversationsFlow_ordersByLastMessageTimeDesc() = runTest {
        dao.insertConversation(conversation(id = "old", userUuid = "u-old", userName = "Old", time = 1L))
        dao.insertConversation(conversation(id = "new", userUuid = "u-new", userName = "New", time = 100L))
        dao.insertConversation(conversation(id = "mid", userUuid = "u-mid", userName = "Mid", time = 50L))

        val list = dao.getAllConversationsFlow().first()

        assertEquals(listOf("new", "mid", "old"), list.map { it.id })
    }

    @Test
    fun updateConversation_replacesStoredValues() = runTest {
        dao.insertConversation(conversation(id = "c2", userUuid = "u2", userName = "Bob", time = 10L))

        dao.updateConversation(
            conversation(
                id = "c2",
                userUuid = "u2",
                userName = "Bobby",
                userAddress = "10.0.0.22",
                lastMessage = "updated",
                time = 999L,
                unreadCount = 4,
                isOnline = true
            )
        )

        val updated = dao.getConversationById("c2")
        assertNotNull(updated)
        assertEquals("Bobby", updated?.userName)
        assertEquals("10.0.0.22", updated?.userAddress)
        assertEquals("updated", updated?.lastMessage)
        assertEquals(999L, updated?.lastMessageTime)
        assertEquals(4, updated?.unreadCount)
        assertEquals(true, updated?.isOnline)
    }

    @Test
    fun updateUserConnectionStatus_updatesOnlyConnectionColumns() = runTest {
        dao.insertConversation(
            conversation(
                id = "c3",
                userUuid = "u3",
                userName = "Carol",
                userAddress = null,
                lastMessage = "keep",
                time = 30L,
                unreadCount = 2,
                isOnline = false
            )
        )

        dao.updateUserConnectionStatus(userUuid = "u3", isOnline = true, userAddress = "10.0.0.3")

        val updated = dao.getConversationByUserUuid("u3")
        assertNotNull(updated)
        assertEquals(true, updated?.isOnline)
        assertEquals("10.0.0.3", updated?.userAddress)
        assertEquals("keep", updated?.lastMessage)
        assertEquals(30L, updated?.lastMessageTime)
        assertEquals(2, updated?.unreadCount)
    }

    @Test
    fun updateLastMessage_changesMessageAndTimestamp() = runTest {
        dao.insertConversation(conversation(id = "c4", userUuid = "u4", userName = "Dave", time = 1L))

        dao.updateLastMessage(conversationId = "c4", lastMessage = "hello world", timestamp = 1234L)

        val updated = dao.getConversationById("c4")
        assertEquals("hello world", updated?.lastMessage)
        assertEquals(1234L, updated?.lastMessageTime)
    }

    @Test
    fun incrementUnreadCount_and_clearUnreadCount_work() = runTest {
        dao.insertConversation(conversation(id = "c5", userUuid = "u5", userName = "Eve", time = 1L))

        dao.incrementUnreadCount("c5")
        dao.incrementUnreadCount("c5")

        val incremented = dao.getConversationById("c5")
        assertEquals(2, incremented?.unreadCount)

        dao.clearUnreadCount("c5")

        val cleared = dao.getConversationById("c5")
        assertEquals(0, cleared?.unreadCount)
    }

    @Test
    fun insertConversation_withSameId_replacesRow() = runTest {
        dao.insertConversation(conversation(id = "same", userUuid = "u6", userName = "First", time = 1L))
        dao.insertConversation(
            conversation(
                id = "same",
                userUuid = "u6",
                userName = "Second",
                userAddress = "10.1.1.6",
                lastMessage = "new",
                time = 2L,
                unreadCount = 9,
                isOnline = true
            )
        )

        val row = dao.getConversationById("same")
        assertNotNull(row)
        assertEquals("Second", row?.userName)
        assertEquals("10.1.1.6", row?.userAddress)
        assertEquals("new", row?.lastMessage)
        assertEquals(2L, row?.lastMessageTime)
        assertEquals(9, row?.unreadCount)
        assertEquals(true, row?.isOnline)
    }

    @Test
    fun getConversationById_returnsNull_whenMissing() = runTest {
        assertNull(dao.getConversationById("does-not-exist"))
    }

    private fun conversation(
        id: String,
        userUuid: String,
        userName: String,
        userAddress: String? = null,
        lastMessage: String? = null,
        time: Long,
        unreadCount: Int = 0,
        isOnline: Boolean = false
    ): Conversation {
        return Conversation(
            id = id,
            userUuid = userUuid,
            userName = userName,
            userAddress = userAddress,
            lastMessage = lastMessage,
            lastMessageTime = time,
            unreadCount = unreadCount,
            isOnline = isOnline
        )
    }
}
