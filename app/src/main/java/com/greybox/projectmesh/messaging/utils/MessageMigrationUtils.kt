//script to help migrate existing messages
package com.greybox.projectmesh.messaging.utils

import android.content.Context
import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.testing.TestDeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

/**
 * Utility responsible for migrating legacy messages to the newer
 * conversation-ID–based chat naming format.
 *
 * It inspects all existing messages, infers correct conversation IDs,
 * and rewrites their `chat` field when necessary.
 *
 * This allows older installations to transition cleanly to the
 * standardized conversation model.
 */
class MessageMigrationUtils(
    override val di: DI
): DIAware {

    private val db: MeshDatabase by di.instance()

    /**
     * Migrates all historical messages so that each message's `chat`
     * value follows the modern conversation ID format.
     *
     * Steps performed:
     * - Loads all messages
     * - Groups them by legacy chat name
     * - Determines correct UUID association for each chat group
     * - Generates a conversation ID using local + remote UUIDs
     * - Rewrites messages with updated chat names
     *
     * Errors are logged but do not stop the migration process.
     */
    suspend fun migrateMessagesToChatIds() {
        withContext(Dispatchers.IO){
            try {
                //get all messages by id
                val messages = db.messageDao().getAll()
                Log.d("MessageMigration", "Found ${messages.size} messages to check for migration")

                //group messages by their current chat names
                val messagesByChat = messages.groupBy { it.chat }

                //process each chat group
                messagesByChat.forEach { (chatName, messagesInChat) ->
                    //skip messages that already appear by using convo id format
                    if (chatName.contains("-") && (chatName.count { it == '-' } >= 1)){
                        Log.d("MessageMigration", "Chat $chatName already appears to use conversation ID format")
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
                                Log.e(
                                    "MessageMigration",
                                    "Error finding user for chat $chatName",
                                    e
                                )
                                "unknown-${chatName}"
                            }
                        }
                    }

                    //create the new conversation ID
                    val localUuid = GlobalApp.GlobalUserRepo.prefs.getString("UUID", null) ?: "local-user"
                    val newChatName = createConversationId(localUuid, userUuid)

                    if (chatName != newChatName) {
                        Log.d("MessageMigration", "Migrating ${messagesInChat.size} messages from '$chatName' to '$newChatName'")

                        //Create new messages with the updated chat name
                        val updatedMessages = messagesInChat.map {
                            it.copy (chat = newChatName)
                        }

                        //Delete old messages and insert updated ones
                        db.messageDao().deleteAll(messagesInChat)
                        for (message in updatedMessages) {
                            db.messageDao().addMessage(message)
                        }

                        Log.d("MessageMigration", "Successfully migrated messages for chat 'chatName'")
                    }
                }
            }catch (e:Exception){
                Log.e("MessageMigration", "Error during message migration", e)
            }
        }
    }

    /**
     * Generates a consistent conversation ID using two UUIDs.
     *
     * Special cases:
     * - Test device UUIDs map to fixed, readable conversation IDs.
     *
     * @param uuid1 Local UUID.
     * @param uuid2 Remote UUID.
     * @return A stable, sorted, hyphen-joined conversation ID.
     */
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
