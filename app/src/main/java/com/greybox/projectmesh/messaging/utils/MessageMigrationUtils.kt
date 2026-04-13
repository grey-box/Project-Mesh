//script to help migrate existing messages
package com.greybox.projectmesh.messaging.utils

import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.testing.TestDeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber

class MessageMigrationUtils(
    override val di: DI
): DIAware {
    private val db: MeshDatabase by di.instance()

    /**
     * Migrates existing messages to use in converstion IDs as chatNames
     */

    suspend fun migrateMessagesToChatIds() {
        withContext(Dispatchers.IO){
            try {
                //get all messages by id
                val messages = db.messageDao().getAll()
                Timber.tag("MessageMigration").d("Found ${messages.size} messages to check for " +
                        "migration")

                //group messages by their current chat names
                val messagesByChat = messages.groupBy { it.chat }

                //process each chat group
                messagesByChat.forEach { (chatName, messagesInChat) ->
                    //skip messages that already appear by using convo id format
                    if (chatName.contains("-") && (chatName.count { it == '-' } >= 1)){
                        Timber.tag("MessageMigration").d("Chat $chatName already appears to use conversation ID format")
                        return@forEach
                    }

                    //determine the Uuid for this chat
                    val userUuid = when (chatName) {
                        TestDeviceService.TEST_DEVICE_NAME -> "test-device-uuid"
                        TestDeviceService.TEST_DEVICE_NAME_OFFLINE -> "offline-test-device-uuid"
                        else -> {
                            try {
                                //try to find users by checking connected users first
                                val allUsers = GlobalApp.GlobalUserRepo.userRepository.getAllUsers()
                                val matchingUser = allUsers.find { user -> user.name == chatName }

                                if (matchingUser != null) {
                                    matchingUser.uuid
                                } else {
                                    // If not found in connected users, create a placeholder UUID
                                    "unknown-${chatName}"
                                }
                            }catch (e: Exception) {
                                Timber.tag(
                                    "MessageMigration").e(e,
                                    "Error finding user for chat $chatName"
                                )
                                "unknown-${chatName}"
                            }
                        }
                    }

                    //create the new conversation ID
                    val localUuid = GlobalApp.GlobalUserRepo.prefs.getString("UUID", null) ?: "local-user"
                    val newChatName = createConversationId(localUuid, userUuid)

                    if (chatName != newChatName) {
                        Timber.tag("MessageMigration").d("Migrating ${messagesInChat.size} messages from '$chatName' to '$newChatName'")

                        //Create new messages with the updated chat name
                        val updatedMessages = messagesInChat.map {
                            it.copy (chat = newChatName)
                        }

                        //Delete old messages and insert updated ones
                        db.messageDao().deleteAll(messagesInChat)
                        for (message in updatedMessages) {
                            db.messageDao().addMessage(message)
                        }

                        Timber.tag("MessageMigration").d("Successfully migrated messages for chat" +
                                " 'chatName'")
                    }
                }
            }catch (e:Exception){
                Timber.tag("MessageMigration").e(e,"Error during message migration")
            }
        }
    }

    private fun createConversationId(uuid1: String, uuid2: String): String {
        // Special cases for test devices
        if (uuid2 == "test-device-uuid") {
            return "local-user-test-device-uuid"
        }
        if (uuid2 == "offline-test-device-uuid") {
            return "local-user-offline-test-device-uuid"
        }
        return listOf(uuid1, uuid2).sorted().joinToString("-")
    }
}