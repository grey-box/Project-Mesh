package com.greybox.projectmesh.messaging.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.ui.models.ChatScreenModel
import com.greybox.projectmesh.testing.TestDeviceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kodein.di.DI
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.server.AppServer.OutgoingTransferInfo
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kodein.di.instance
import java.net.InetAddress
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.user.UserEntity
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.DeviceStatusManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.URI

class ChatScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val virtualAddress: InetAddress = savedStateHandle.get<InetAddress>("virtualAddress")!!

    // _uiState will be updated whenever there is a change in the UI state
    private val ipStr: String = virtualAddress.hostAddress

    //get conversation id
    private val passedConversationId = savedStateHandle.get<String>("conversationId")

    //Get User info
    private val userEntity = runBlocking {
        GlobalApp.GlobalUserRepo.userRepository.getUserByIp(ipStr)
    }

    // Use the retrieved user name (fallback to "Unknown" if no user is found)
    private val deviceName = userEntity?.name ?: "Unknown"

    private val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
    private val localUuid = sharedPrefs.getString("UUID", null) ?: "local-user"

    private val userUuid: String = when {
        passedConversationId != null && passedConversationId.contains("-") -> {
            // Extract the UUID that's not the local UUID
            val uuids = passedConversationId.split("-")
            uuids.find { it != localUuid } ?: "unknown-${virtualAddress.hostAddress}"
        }
        // Otherwise use the standard logic
        TestDeviceService.isOnlineTestDevice(virtualAddress) -> "test-device-uuid"
        ipStr == TestDeviceService.TEST_DEVICE_IP_OFFLINE ||
                userEntity?.name == TestDeviceService.TEST_DEVICE_NAME_OFFLINE -> "offline-test-device-uuid"
        else -> userEntity?.uuid ?: "unknown-${virtualAddress.hostAddress}"
    }

    private val savedConversationId = savedStateHandle.get<String>("conversationId")
    //Timber.tag("ChatDebug").d("GOT CONVERSATION ID FROM SAVED STATE: $savedConversationId")

    private val conversationId = passedConversationId ?:
    ConversationUtils.createConversationId(localUuid, userUuid)

    private val chatName = savedConversationId ?: conversationId
    //Timber.tag("ChatDebug").d("USING CHAT NAME: $chatName (saved: $savedConversationId,
    // generated:
    // $conversationId)")



    private val addressDotNotation = virtualAddress.requireAddressAsInt().addressToDotNotation()

    private val conversationRepository: ConversationRepository by di.instance()


    private val _uiState = MutableStateFlow(
        ChatScreenModel(
            deviceName = deviceName,
            virtualAddress = virtualAddress
        )
    )

    // uiState is a read-only property that shows the current UI state
    val uiState: Flow<ChatScreenModel> = _uiState.asStateFlow()

    // di is used to get the AndroidVirtualNode instance
    private val db: MeshDatabase by di.instance()

    private val appServer: AppServer by di.instance()

    private val _deviceOnlineStatus = MutableStateFlow(false)
    val deviceOnlineStatus: StateFlow<Boolean> = _deviceOnlineStatus.asStateFlow()

    // launch a coroutine
    init {

        val savedConversationId = savedStateHandle.get<String>("conversationId")

        // If we have a conversation ID from navigation, use it directly
        val effectiveChatName = if (savedConversationId != null) {
            Timber.tag("ChatDebug").d("USING SAVED CONVERSATION ID: $savedConversationId INSTEAD OF GENERATED: $chatName")
            savedConversationId
        } else {
            chatName
        }

        viewModelScope.launch {
            // Debug logs
            Timber.tag("ChatDebug").d("Will query messages with chatName: $chatName")
            Timber.tag("ChatDebug").d("Using Conversation ID for messages: $conversationId")
            Timber.tag("ChatDebug").d("User UUID: $userUuid")

            //check database content in background
            withContext(Dispatchers.IO) {
                val allMessages = db.messageDao().getAll()
                Timber.d("ChatDebug", "All messages in database: ${allMessages.size}")
                for (msg in allMessages) {
                    Timber.tag("ChatDebug").d(
                        "Message: id=${msg.id}, chat=${msg.chat}, content=${msg.content}, sender=${msg.sender}"
                    )
                }
            }

            //determine which flow to collect from
            val isTestDevice =
                (userUuid == "test-device-uuid" || userUuid == "offline-test-device-uuid")

            //load messages synchronously for offline access
            val initialChatName = chatName // Use consistent chat name

            try {
                // Get messages immediately without waiting for Flow
                val initialMessages = withContext(Dispatchers.IO) {
                    // We'll need to add this method to MessageDao in Step 3
                    db.messageDao().getChatMessagesSync(chatName)
                }

                // Update UI immediately with initial messages
                if (initialMessages.isNotEmpty()) {
                    _uiState.update { prev ->
                        prev.copy(allChatMessages = initialMessages)
                    }
                    Timber.tag("ChatDebug").d("IMMEDIATELY LOADED ${initialMessages.size} " +
                            "MESSAGES FOR OFFLINE ACCESS")
                } else {
                    Timber.tag("ChatDebug").d("NO INITIAL MESSAGES FOUND FOR CHAT: $chatName")
                }

            } catch (e: Exception) {
                Timber.e("ChatDebug", "ERROR LOADING INITIAL MESSAGES: ${e.message}", e)
            }

            val messagesFlow = if (isTestDevice) {
                val testDeviceName = when (userUuid) {
                    "test-device-uuid" -> TestDeviceService.TEST_DEVICE_NAME
                    "offline-test-device-uuid" -> TestDeviceService.TEST_DEVICE_NAME_OFFLINE
                    else -> null
                }

                if (testDeviceName != null) {
                    Timber.tag("ChatDebug").d("Using multi-name query with: [$chatName,$testDeviceName]")
                    db.messageDao().getChatMessagesFlowMultipleNames(
                        listOf(chatName, testDeviceName)
                    )
                } else {
                    db.messageDao().getChatMessagesFlow(chatName)
                }
            } else {
                db.messageDao().getChatMessagesFlow(chatName)
            }

            //collect messages from the chosen flow
            messagesFlow.collect { newChatMessages ->
                Timber.tag("ChatDebug").d("Received ${newChatMessages.size} messages")
                _uiState.update { prev ->
                    prev.copy(allChatMessages = newChatMessages)
                }
            }
        }

        viewModelScope.launch {
            // If this is a real device (not placeholder address)
            if (virtualAddress.hostAddress != "0.0.0.0" &&
                virtualAddress.hostAddress != TestDeviceService.TEST_DEVICE_IP_OFFLINE
            ) {
                DeviceStatusManager.deviceStatusMap.collect { statusMap ->
                    val ipAddress = virtualAddress.hostAddress
                    val isOnline = statusMap[ipAddress] ?: false

                    // Only update if status changed
                    if (_deviceOnlineStatus.value != isOnline) {
                        Timber.tag("ChatDebug").d("Device status changed: $ipAddress is now ${if (isOnline) "online" else "offline"}")
                        _deviceOnlineStatus.value = isOnline

                        if (isOnline) {
                            Timber.d("ChatDebug", "Device came back online - refreshing message history")
                            // Force refresh messages from database
                            withContext(Dispatchers.IO) {
                                val refreshedMessages = db.messageDao().getChatMessagesSync(chatName)
                                _uiState.update { prev ->
                                    prev.copy(
                                        allChatMessages = refreshedMessages,
                                        offlineWarning = null // Clear offline warning
                                    )
                                }
                            }
                        } else {
                            // Update the UI state with offline warning
                            _uiState.update { prev ->
                                prev.copy(
                                    offlineWarning = "Device appears to be offline. Messages will be saved locally."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun markConversationAsRead() {
        try {
            if (userEntity != null) {
                //Create a convo id using both UUIDs
                val conversationId =
                    ConversationUtils.createConversationId(localUuid, userEntity.uuid)

                //Mark this conversation as read
                conversationRepository.markAsRead(conversationId)
                Timber.tag("ChatScreenViewModel").d("Marked conversation as read: $conversationId")
            }
        } catch (e: Exception) {
            Timber.tag("ChatScreenViewModel").e(e,"Error marking conversation as read")
        }
    }


    fun sendChatMessage(
        virtualAddress: InetAddress,
        message: String,
        file: URI?
    ) {//add file field here
        val ipAddress = virtualAddress.hostAddress
        val sendTime: Long = System.currentTimeMillis()

        //check if device is online first
        val isOnline = DeviceStatusManager.isDeviceOnline(ipAddress)

        //use same conversationid as chat name
        val messageEntity = Message(0, sendTime, message, "Me", chatName, file)

        Timber.tag("ChatDebug").d("Sending message to chat: $chatName")
        viewModelScope.launch {
            //save to local database
            db.messageDao().addMessage(messageEntity)

            //update convo with the new message
            if (userEntity != null) {
                try {
                    //get or create conversation
                    val remoteUser = UserEntity(
                        uuid = userUuid,
                        name = userEntity.name,
                        address = userEntity.address
                    )

                    val conversation = conversationRepository.getOrCreateConversation(
                        localUuid = localUuid,
                        remoteUser = remoteUser
                    )

                    //update conversation with the message
                    conversationRepository.updateWithMessage(
                        conversationId = conversation.id,
                        message = messageEntity
                    )

                    Timber.tag("ChatScreenViewModel").d("Updated conversation with sent message")
                } catch (e: Exception) {
                    Timber.tag("ChatScreenViewModel").e(e,"Failed to update conversation with sent message")
                }
            }

            if (isOnline) {
                try {
                    // Use withContext to ensure network operations run on IO thread
                    val delivered = withContext(Dispatchers.IO) {
                        // Try with a timeout to prevent blocking
                        withTimeoutOrNull(5000) {
                            appServer.sendChatMessageWithStatus(virtualAddress, sendTime, message, file)
                        } ?: false
                    }

                    // Update UI based on delivery status
                    if (!delivered) {
                        Timber.tag("ChatDebug").d("Message delivery failed")
                        _uiState.update { prev ->
                            prev.copy(offlineWarning = "Message delivery failed. Device may be offline.")
                        }
                        // Force device status verification
                        DeviceStatusManager.verifyDeviceStatus(ipAddress)
                    } else {
                        Timber.tag("ChatDebug").d("Message delivered successfully")
                    }
                } catch (e: Exception) {
                    Timber.tag("ChatScreenViewModel").e(e, "Error sending message: ${e.message}")
                    _uiState.update { prev ->
                        prev.copy(offlineWarning = "Error sending message: ${e.message}")
                    }
                }
            } else {
                Timber.tag("ChatScreenViewModel").d("Device $ipAddress appears to be offline, " +
                        "message saved locally only")
                _uiState.update { prev ->
                    prev.copy(offlineWarning = "Device appears to be offline. Message saved locally only.")
                }
            }
        }
    }

    //handles outgoing file transfer to fix unresolved reference error crash
    fun addOutgoingTransfer(fileUri: Uri, toAddress: InetAddress): OutgoingTransferInfo {
        return appServer.addOutgoingTransfer(fileUri, toAddress)
    }
}
