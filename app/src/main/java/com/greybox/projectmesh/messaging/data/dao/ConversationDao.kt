package com.greybox.projectmesh.messaging.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.greybox.projectmesh.messaging.data.entities.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Conversation] entities.
 *
 * Provides methods to query, insert, and update conversations in the Room database.
 */
@Dao
interface ConversationDao {

    /**
     * Returns a flow of all conversations, sorted by the timestamp of the last message in descending order.
     *
     * @return [Flow] emitting a list of [Conversation] objects whenever the data changes.
     */
    @Query("SELECT * FROM conversations ORDER BY last_message_time DESC")
    fun getAllConversationsFlow(): Flow<List<Conversation>>

    /**
     * Retrieves a conversation by its unique ID.
     *
     * @param conversationId The unique ID of the conversation.
     * @return The [Conversation] if found, or `null` if no matching conversation exists.
     */
    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): Conversation?

    /**
     * Retrieves a conversation associated with a specific user UUID.
     *
     * @param userUuid The UUID of the user.
     * @return The [Conversation] if found, or `null` if no matching conversation exists.
     */
    @Query("SELECT * FROM conversations WHERE user_uuid = :userUuid LIMIT 1")
    suspend fun getConversationByUserUuid(userUuid: String): Conversation?

    /**
     * Inserts a new conversation into the database.
     *
     * If a conversation with the same ID already exists, it will be replaced.
     *
     * @param conversation The [Conversation] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    /**
     * Updates an existing conversation in the database.
     *
     * @param conversation The [Conversation] to update.
     */
    @Update
    suspend fun updateConversation(conversation: Conversation)

    /**
     * Updates the online status and user address for a conversation based on the user UUID.
     *
     * @param userUuid The UUID of the user.
     * @param isOnline Whether the user is currently online.
     * @param userAddress The user's network address (nullable).
     */
    @Query("UPDATE conversations SET is_online = :isOnline, user_address = :userAddress WHERE user_uuid = :userUuid")
    suspend fun updateUserConnectionStatus(userUuid: String, isOnline: Boolean, userAddress: String?)

    /**
     * Updates the last message and its timestamp for a specific conversation.
     *
     * @param conversationId The unique ID of the conversation.
     * @param lastMessage The latest message text.
     * @param timestamp The time when the last message was sent.
     */
    @Query("UPDATE conversations SET last_message = :lastMessage, last_message_time = :timestamp WHERE id = :conversationId")
    suspend fun updateLastMessage(conversationId: String, lastMessage: String, timestamp: Long)

    /**
     * Increments the unread message count for a specific conversation by 1.
     *
     * @param conversationId The unique ID of the conversation.
     */
    @Query("UPDATE conversations SET unread_count = unread_count + 1 WHERE id = :conversationId")
    suspend fun incrementUnreadCount(conversationId: String)

    /**
     * Clears the unread message count for a specific conversation, setting it to 0.
     *
     * @param conversationId The unique ID of the conversation.
     */
    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :conversationId")
    suspend fun clearUnreadCount(conversationId: String)
}
