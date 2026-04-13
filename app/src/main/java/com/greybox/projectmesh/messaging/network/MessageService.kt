// Path: app/src/main/java/com/greybox/projectmesh/messaging/network/MessageService.kt
package com.greybox.projectmesh.messaging.network

import android.content.SharedPreferences
import com.greybox.projectmesh.messaging.repository.MessageRepository
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.user.UserRepository
import java.net.InetAddress
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber

class MessageService(
    override val di: DI
) : DIAware {
    private val messageNetworkHandler: MessageNetworkHandler by di.instance()
    private val messageRepository: MessageRepository by di.instance()
    private val conversationRepository: ConversationRepository by di.instance()
    private val userRepository: UserRepository by di.instance()
    private val settingsPrefs: SharedPreferences by di.instance(tag = "settings")

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
            Timber.tag("MessageService").e(e,"Error updating conversation with message")
        }
    }
}