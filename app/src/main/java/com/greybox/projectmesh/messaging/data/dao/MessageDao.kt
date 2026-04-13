package com.greybox.projectmesh.messaging.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.greybox.projectmesh.messaging.data.entities.Message
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Message] entities.
 *
 * Provides methods to query, insert, and delete messages in the Room database.
 */
@Dao
interface MessageDao {

    /**
     * Retrieves all messages as a synchronous list.
     *
     * @return A [List] of all [Message] objects in the database.
     */
    @Query("SELECT * FROM message")
    fun getAll(): List<Message>

    /**
     * Returns a flow of all messages.
     *
     * @return [Flow] emitting a list of [Message] objects whenever the data changes.
     */
    @Query("SELECT * FROM message")
    fun getAllFlow(): Flow<List<Message>>

    /**
     * Returns a flow of messages for a specific chat, ordered by date received ascending.
     *
     * @param chat The chat identifier.
     * @return [Flow] emitting a list of [Message] objects for the given chat.
     */
    @Query("SELECT * FROM message WHERE chat = :chat ORDER BY dateReceived ASC")
    fun getChatMessagesFlow(chat: String): Flow<List<Message>>

    /**
     * Deletes all messages from the database.
     */
    @Query("DELETE FROM message")
    fun clearTable()

    /**
     * Returns a flow of messages for multiple chat names, ordered by date received ascending.
     *
     * @param chatNames A list of chat identifiers.
     * @return [Flow] emitting a list of [Message] objects for the given chats.
     */
    @Query("SELECT * FROM message WHERE chat IN (:chatNames) ORDER BY dateReceived ASC")
    fun getChatMessagesFlowMultipleNames(chatNames: List<String>): Flow<List<Message>>

    /**
     * Synchronously retrieves messages for a specific chat, ordered by date received ascending.
     *
     * @param chat The chat identifier.
     * @return A [List] of [Message] objects for the given chat.
     */
    @Query("SELECT * FROM message WHERE chat = :chat ORDER BY dateReceived ASC")
    fun getChatMessagesSync(chat: String): List<Message>

    /**
     * Inserts a new message into the database.
     *
     * @param m The [Message] to add.
     */
    @Insert
    suspend fun addMessage(m: Message)

    /**
     * Deletes a single message from the database.
     *
     * @param m The [Message] to delete.
     */
    @Delete
    fun delete(m: Message)

    /**
     * Deletes multiple messages from the database.
     *
     * @param messages The list of [Message] objects to delete.
     */
    @Delete
    suspend fun deleteAll(messages: List<Message>)
}
