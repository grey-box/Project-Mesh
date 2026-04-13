package com.greybox.projectmesh.messaging.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a conversation (chat thread) with another user.
 *
 * Each conversation tracks the other user's info, the last message, unread count, and online status.
 *
 * @property id The unique ID of the conversation, typically a composite of the two users' IDs.
 * @property userUuid The UUID of the other user in the conversation.
 * @property userName The display name of the other user.
 * @property userAddress The IP address of the other user (nullable).
 * @property lastMessage The text of the last message in the conversation (nullable).
 * @property lastMessageTime Timestamp of when the last message was sent.
 * @property unreadCount The number of unread messages in this conversation (default 0).
 * @property isOnline Indicates whether the other user is currently online (default false).
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String, //Composite id of the two users
    @ColumnInfo(name = "user_uuid") val userUuid: String, //other user id
    @ColumnInfo(name = "user_name") val userName: String, //other user name
    @ColumnInfo(name = "user_address") val userAddress: String?, // other user ip address
    @ColumnInfo(name = "last_message") val lastMessage: String?, //last message text
    @ColumnInfo(name = "last_message_time") val lastMessageTime: Long, //Timestamp of last message
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0, //count of unread messages
    @ColumnInfo(name = "is_online") val isOnline: Boolean = false //whether the user is online
)
