// Path: app/src/main/java/com/greybox/projectmesh/messaging/network/MessageService.kt
package com.greybox.projectmesh.messaging.network

import android.content.SharedPreferences
import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.messaging.repository.MessageRepository
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.utils.MessageUtils
import com.greybox.projectmesh.user.UserRepository
import java.net.InetAddress
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * Service layer for handling message operations including sending messages
 * and updating conversations.
 *
 * @property di Kodein DI container for retrieving required dependencies.
 */
class MessageService(
    override val di: DI
) : DIAware {
    private val messageNetworkHandler: MessageNetworkHandler by di.instance()
    private val messageRepository: MessageRepository by di.instance()
    private val conversationRepository: ConversationRepository by di.instance()
    private val userRepository: UserRepository by di.instance()
    private val settingsPrefs: SharedPreferences by di.instance(tag = "settings")

    /**
     * Sends a message to a given IP address.
     * First saves the message locally, then sends it over the network.
     *
     * @param address The target device's IP address.
     * @param message The [Message] object to be sent.
     */
    suspend fun sendMessage(address: InetAddress, message: Message) {
        //First save locally
        messageRepository.addMessage(message)

        //Then send over network
        messageNetworkHandler.sendChatMessage(
            address = address,
            time = message.dateReceived,
            message = message.content,
            file = null
        )
    }

    /**
     * Updates the conversation associated with a given user IP with a new message.
     *
     * @param address The IP address of the remote user.
     * @param message The [Message] object to update in the conversation.
     */
    private suspend fun updateConversationWithMessage(address: InetAddress, message: Message){
        try {
            //find user by ip address
            val ipStr = address.hostAddress
            val user = userRepository.getUserByIp(ipStr)

            if (user != null) {
                //user found -> create or update the convo
                val localUuid = settingsPrefs.getString("UUID", null) ?: "local-user"

                //get or create the convo
                val conversation = conversationRepository.getOrCreateConversation(
                    localUuid = localUuid,
                    remoteUser = user,
                )

                //update the conversation with the message
                conversationRepository.updateWithMessage(
                    conversationId = conversation.id,
                    message = message
                )

            }
        }catch (e: Exception){
            Log.e("MessageService", "Error updating conversation with message", e)
        }
    }
}
