package com.greybox.projectmesh.messaging.repository

import com.greybox.projectmesh.messaging.data.dao.ConversationDao
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import com.greybox.projectmesh.user.UserEntity
import kotlinx.coroutines.flow.Flow
import org.kodein.di.DI
import org.kodein.di.DIAware
import timber.log.Timber

class ConversationRepository(
    private val conversationDao: ConversationDao,
    override val di: DI
) : DIAware {

    //get all conversations as a flow
    fun getAllConversations(): Flow<List<Conversation>>{
        return conversationDao.getAllConversationsFlow()
    }

    //get specific convo by id
    suspend fun getConversationById(conversationId: String): Conversation? {
        Timber.tag("ConversationRepository").d("Getting conversation by ID: $conversationId")
        val result = conversationDao.getConversationById(conversationId)
        Timber.tag("ConversationRepository").d("Result for ID $conversationId: ${result != null}")
        return result
    }

    suspend fun getOrCreateConversation(localUuid: String, remoteUser: UserEntity): Conversation {
        //create a unique conversation ID using both UUIDs in order to ensure consistency
        val conversationId = ConversationUtils.createConversationId(localUuid, remoteUser.uuid)

        Timber.tag("ConversationRepository").d("Looking for conversation with ID: $conversationId")
        Timber.tag("ConversationRepository").d("Local UUID: $localUuid, Remote UUID: ${remoteUser
            .uuid}")

        //try to get an existing conversation
        var conversation = conversationDao.getConversationById(conversationId)

        //if no conversation exists, create a new one
        if(conversation == null){
            Timber.d("ConversationRepository", "Conversation not found, creating new one with ${remoteUser.name}")

            conversation = Conversation(
                id = conversationId,
                userUuid = remoteUser.uuid,
                userName = remoteUser.name,
                userAddress = remoteUser.address,
                lastMessage = null,
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0,
                isOnline = remoteUser.address != null
            )
            conversationDao.insertConversation(conversation)
            Timber.tag("ConversationRepository").d("Created new conversation with ${remoteUser
                .name}")
        } else {
            Timber.tag("ConversationRepository").d("Found existing conversation with ${remoteUser
                .name}")
        }
        return conversation
    }

    //update conversation with the latest message
    suspend fun updateWithMessage(conversationId: String, message: Message) {

        conversationDao.updateLastMessage(
            conversationId = conversationId,
            lastMessage = message.content,
            timestamp = message.dateReceived
        )

        //if this is a received message (not sent by self) increment unread count
        if (message.sender != "Me") {
            conversationDao.incrementUnreadCount(conversationId)
        }

        conversationDao.updateLastMessage(
            conversationId = conversationId,
            lastMessage = message.content,
            timestamp = message.dateReceived
        )

    }

    //mark conversation as read
    suspend fun markAsRead(conversationId: String) {
        conversationDao.clearUnreadCount(conversationId)
    }

    //update a user's online status
    suspend fun updateUserStatus(userUuid: String, isOnline: Boolean, userAddress: String?) {
        try {
            // Update in database
            conversationDao.updateUserConnectionStatus(
                userUuid = userUuid,
                isOnline = isOnline,
                userAddress = userAddress
            )

            // Log for debugging
            Timber.tag("ConversationRepository").d("Updated user $userUuid connection status: online=$isOnline, address=$userAddress")
        } catch (e: Exception) {
            Timber.tag("ConversationRepository").e(e,"Failed to update user connection status")
        }
    }

}
