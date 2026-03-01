package com.greybox.projectmesh.messaging.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.Message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MessageDaoTest {

    private lateinit var db: MeshDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeshDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = db.messageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addMessage_persistsRows_and_getAll_matchesStoredData() = runTest {
        dao.addMessage(message(chat = "a", content = "m1", time = 10L))
        dao.addMessage(message(chat = "b", content = "m2", time = 20L, file = URI.create("https://example.com/f")))

        val rows = dao.getAll()

        assertEquals(2, rows.size)
        assertEquals(listOf("m1", "m2"), rows.map { it.content })
        assertTrue(rows[0].id > 0)
        assertTrue(rows[1].id > 0)
        assertEquals("https://example.com/f", rows[1].file.toString())
    }

    @Test
    fun getAllFlow_emitsAllMessages() = runTest {
        dao.addMessage(message(chat = "a", content = "x", time = 1L))
        dao.addMessage(message(chat = "b", content = "y", time = 2L))

        val rows = dao.getAllFlow().first()

        assertEquals(2, rows.size)
    }

    @Test
    fun getChatMessagesFlow_returnsOnlyChat_andSortedAscendingByDate() = runTest {
        dao.addMessage(message(chat = "c1", content = "late", time = 20L))
        dao.addMessage(message(chat = "c2", content = "other", time = 5L))
        dao.addMessage(message(chat = "c1", content = "early", time = 10L))

        val rows = dao.getChatMessagesFlow("c1").first()

        assertEquals(listOf("early", "late"), rows.map { it.content })
        assertEquals(listOf(10L, 20L), rows.map { it.dateReceived })
        assertTrue(rows.all { it.chat == "c1" })
    }

    @Test
    fun getChatMessagesSync_returnsOnlyChat_andSortedAscendingByDate() = runTest {
        dao.addMessage(message(chat = "sync", content = "second", time = 200L))
        dao.addMessage(message(chat = "sync", content = "first", time = 100L))
        dao.addMessage(message(chat = "other", content = "ignored", time = 50L))

        val rows = dao.getChatMessagesSync("sync")

        assertEquals(listOf("first", "second"), rows.map { it.content })
    }

    @Test
    fun getChatMessagesFlowMultipleNames_filtersByList_andSortsAscending() = runTest {
        dao.addMessage(message(chat = "a", content = "a2", time = 30L))
        dao.addMessage(message(chat = "b", content = "b1", time = 10L))
        dao.addMessage(message(chat = "c", content = "c1", time = 5L))
        dao.addMessage(message(chat = "a", content = "a1", time = 20L))

        val rows = dao.getChatMessagesFlowMultipleNames(listOf("a", "b")).first()

        assertEquals(listOf("b1", "a1", "a2"), rows.map { it.content })
        assertTrue(rows.all { it.chat == "a" || it.chat == "b" })
    }

    @Test
    fun delete_removesSingleMessage() = runTest {
        dao.addMessage(message(chat = "d", content = "keep", time = 1L))
        dao.addMessage(message(chat = "d", content = "remove", time = 2L))

        val existing = dao.getChatMessagesSync("d")
        val toDelete = existing.first { it.content == "remove" }

        dao.delete(toDelete)

        val after = dao.getChatMessagesSync("d")
        assertEquals(1, after.size)
        assertEquals("keep", after.single().content)
    }

    @Test
    fun deleteAll_removesProvidedMessages() = runTest {
        dao.addMessage(message(chat = "e", content = "m1", time = 1L))
        dao.addMessage(message(chat = "e", content = "m2", time = 2L))
        dao.addMessage(message(chat = "e", content = "m3", time = 3L))

        val rows = dao.getChatMessagesSync("e")
        dao.deleteAll(rows.take(2))

        val after = dao.getChatMessagesSync("e")
        assertEquals(1, after.size)
        assertEquals("m3", after.single().content)
    }

    @Test
    fun clearTable_removesAllRows() = runTest {
        dao.addMessage(message(chat = "x", content = "1", time = 1L))
        dao.addMessage(message(chat = "y", content = "2", time = 2L))

        dao.clearTable()

        assertTrue(dao.getAll().isEmpty())
    }

    private fun message(
        chat: String,
        content: String,
        time: Long,
        sender: String = "sender",
        file: URI? = null
    ): Message {
        return Message(
            id = 0,
            dateReceived = time,
            content = content,
            sender = sender,
            chat = chat,
            file = file
        )
    }
}
