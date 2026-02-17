package com.greybox.projectmesh.messaging.repository

import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.entities.Message
import kotlinx.coroutines.flow.Flow
import org.kodein.di.DI
import org.kodein.di.DIAware

// Changed to use Kodein instead of javax.inject

/**
 * Repository for managing messages in the app.
 * Handles retrieval, insertion, and clearing of messages for chats.
 *
 * @property messageDao DAO for database operations related to messages.
 * @property di Kodein DI container for dependency injection.
 */
class MessageRepository(
    private val messageDao: MessageDao,
    override val di: DI
) : DIAware {

    /**
     * Retrieves all messages for a specific chat as a [Flow].
     *
     * @param chatId The ID of the chat to retrieve messages for.
     * @return A [Flow] emitting a [List] of [Message] objects for the chat.
     */
    fun getChatMessages(chatId: String): Flow<List<Message>> {
        return messageDao.getChatMessagesFlow(chatId)
    }

    /**
     * Adds a new message to the database.
     *
     * @param message The [Message] to add.
     */
    suspend fun addMessage(message: Message) {
        messageDao.addMessage(message)
    }

    /**
     * Retrieves all messages from the database as a [Flow].
     *
     * @return A [Flow] emitting a [List] of all [Message] objects.
     */
    fun getAllMessages(): Flow<List<Message>> {
        return messageDao.getAllFlow()
    }

    /**
     * Clears all messages from the database.
     */
    suspend fun clearMessages() {
        messageDao.clearTable()
    }
}
