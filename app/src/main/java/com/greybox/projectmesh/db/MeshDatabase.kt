package com.greybox.projectmesh.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.dao.ConversationDao
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.user.UserDao
import com.greybox.projectmesh.user.UserEntity

/**
 * Room database for the ProjectMesh application.
 *
 * This database stores messages, conversations, and user entities.
 * It provides DAOs to access and manipulate each type of data.
 */
@Database(
    entities = [
        Message::class,
        UserEntity::class,  // <- add this
        Conversation::class
    ],
    version = 4,
    exportSchema = false
)
abstract class MeshDatabase : RoomDatabase() {

    /**
     * Provides access to message-related database operations.
     *
     * @return A [MessageDao] instance for querying and modifying messages.
     */
    abstract fun messageDao(): MessageDao

    /**
     * Provides access to user-related database operations.
     *
     * @return A [UserDao] instance for querying and modifying user entities.
     */
    abstract fun userDao(): UserDao

    /**
     * Provides access to conversation-related database operations.
     *
     * @return A [ConversationDao] instance for querying and modifying conversations.
     */
    abstract fun conversationDao(): ConversationDao
}
