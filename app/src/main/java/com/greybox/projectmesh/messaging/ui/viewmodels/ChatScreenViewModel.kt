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
import com.greybox.projectmesh.server.AppServer.Companion.DEFAULT_PORT
import com.greybox.projectmesh.server.AppServer.OutgoingTransferInfo
import com.greybox.projectmesh.server.AppServer.Status
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import com.greybox.projectmesh.bluetooth.HttpOverBluetoothClient
import com.greybox.projectmesh.bluetooth.BluetoothUuids
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import rawhttp.core.RawHttp
import rawhttp.core.body.StringBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kodein.di.instance
import java.net.InetAddress
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.user.UserEntity
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.GlobalApp.GlobalUserRepo.userRepository
import com.greybox.projectmesh.bluetooth.BluetoothMessageClient
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

class ChatScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val virtualAddress: InetAddress = savedStateHandle.get<InetAddress>("virtualAddress")!!

    // _uiState will be updated whenever there is a change in the UI state
    private val ipStr: String = virtualAddress.hostAddress

    // add btMessageClient
    private val bluetoothMessageClient: BluetoothMessageClient by di.instance()

    //get conversation id
    private val passedConversationId = savedStateHandle.get<String>("conversationId")
    private val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
    private val localUuid = sharedPrefs.getString("UUID", null) ?: "local-user"

    private val isOfflineMode = ipStr == "0.0.0.0"

    private val userEntity = runBlocking {
        if (isOfflineMode && passedConversationId != null) {
            val remoteUuid = passedConversationId
                .removePrefix("$localUuid-")  // Try removing local UUID from start
                .removeSuffix("-$localUuid")  // Try removing local UUID from end

            GlobalApp.GlobalUserRepo.userRepository.getUser(remoteUuid)
        } else {
            GlobalApp.GlobalUserRepo.userRepository.getUserByIp(ipStr)
        }
    }



    // Use the retrieved user name (fallback to "Unknown" if no user is found)
    private val deviceName = userEntity?.name ?: "Unknown"



    private val userUuid: String = when {
        isOfflineMode && passedConversationId != null -> {
            passedConversationId
                .removePrefix("$localUuid-")
                .removeSuffix("-$localUuid")
        }
        TestDeviceService.isOnlineTestDevice(virtualAddress) -> "test-device-uuid"
        ipStr == TestDeviceService.TEST_DEVICE_IP_OFFLINE ||
                userEntity?.name == TestDeviceService.TEST_DEVICE_NAME_OFFLINE -> "offline-test-device-uuid"
        else -> userEntity?.uuid ?: "unknown-${virtualAddress.hostAddress}"
    }

    private val savedConversationId = savedStateHandle.get<String>("conversationId")
    //Log.d("ChatDebug", "GOT CONVERSATION ID FROM SAVED STATE: $savedConversationId")

    private val conversationId =
        passedConversationId ?: ConversationUtils.createConversationId(localUuid, userUuid)

    private val chatName = savedConversationId ?: conversationId
    //Log.d("ChatDebug", "USING CHAT NAME: $chatName (saved: $savedConversationId, generated: $conversationId)")

    // bluetooth only flag
    private val _btOnlyFlag = MutableStateFlow(sharedPrefs.getBoolean("bt_only_mode", false))
    val btOnlyFlag: StateFlow<Boolean> = _btOnlyFlag

    // listener to update the btOnly flag
    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "bt_only_mode") {
                _btOnlyFlag.value = sp.getBoolean("bt_only_mode", false)
            }
        }

    override fun onCleared() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }


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

    private val rawHttp: RawHttp by di.instance()

    private val json: Json by di.instance()

    private val btClient: HttpOverBluetoothClient by di.instance()

    private val _deviceOnlineStatus = MutableStateFlow(false)
    val deviceOnlineStatus: StateFlow<Boolean> = _deviceOnlineStatus.asStateFlow()

    // this holds the mac value and lets us observer know when it changes
    private val _linkedBtMac = MutableStateFlow<String?>(null)
    // now we turn the mutable version into a readonly
    val linkedBtMac: StateFlow<String?> = _linkedBtMac.asStateFlow()
    // updates the valuye inside the MutableStateFlow
    fun setLinkedBluetoothDevice(mac: String?) {
        _linkedBtMac.value = mac
    }

    // launch a coroutine
    init {
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        val savedConversationId = savedStateHandle.get<String>("conversationId")

        // If we have a conversation ID from navigation, use it directly
        val effectiveChatName = if (savedConversationId != null) {
            Log.d(
                "ChatDebug",
                "USING SAVED CONVERSATION ID: $savedConversationId INSTEAD OF GENERATED: $chatName"
            )
            savedConversationId
        } else {
            chatName
        }

        viewModelScope.launch {
            // Debug logs
            Log.d("ChatDebug", "Will query messages with chatName: $chatName")
            Log.d("ChatDebug", "Using Conversation ID for messages: $conversationId")
            Log.d("ChatDebug", "User UUID: $userUuid")

            //check database content in background
            withContext(Dispatchers.IO) {
                val allMessages = db.messageDao().getAll()
                Log.d("ChatDebug", "All messages in database: ${allMessages.size}")
                for (msg in allMessages) {
                    Log.d(
                        "ChatDebug",
                        "Message: id=${msg.id}, chat=${msg.chat}, content=${msg.content}, sender=${msg.sender}"
                    )
                }
            }

            // add to track linked mac addresses in stateflow and database
            viewModelScope.launch {
                val currentUser = userRepository.getUser(userUuid)
                Log.d("ChatScreenViewModel", "Current user uuid: $userUuid")

                // Filter out the placeholder MAC address
                val macAddress = currentUser?.macAddress
                _linkedBtMac.value = if (macAddress == "AA:BB:CC:DD:EE:FF" || macAddress.isNullOrEmpty()) {
                    null
                } else {
                    macAddress
                }

                Log.d("ChatScreenViewModel", "Initialized linkedBtMac: ${_linkedBtMac.value}")
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
                    Log.d(
                        "ChatDebfug",
                        "IMMEDIATELY LOADED ${initialMessages.size} MESSAGES FOR OFFLINE ACCESS"
                    )
                } else {
                    Log.d("ChatDebug", "NO INITIAL MESSAGES FOUND FOR CHAT: $chatName")
                }

            } catch (e: Exception) {
                Log.e("ChatDebug", "ERROR LOADING INITIAL MESSAGES: ${e.message}", e)
            }

            val messagesFlow = if (isTestDevice) {
                val testDeviceName = when (userUuid) {
                    "test-device-uuid" -> TestDeviceService.TEST_DEVICE_NAME
                    "offline-test-device-uuid" -> TestDeviceService.TEST_DEVICE_NAME_OFFLINE
                    else -> null
                }

                if (testDeviceName != null) {
                    Log.d("ChatDebug", "Using multi-name query with: [$chatName, $testDeviceName]")
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
                Log.d("ChatDebug", "Received ${newChatMessages.size} messages")
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
                        Log.d(
                            "ChatDebug",
                            "Device status changed: $ipAddress is now ${if (isOnline) "online" else "offline"}"
                        )
                        _deviceOnlineStatus.value = isOnline

                        if (isOnline) {
                            Log.d(
                                "ChatDebug",
                                "Device came back online - refreshing message history"
                            )
                            // Force refresh messages from database
                            withContext(Dispatchers.IO) {
                                val refreshedMessages =
                                    db.messageDao().getChatMessagesSync(chatName)
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
                Log.d("ChatScreenViewModel", "Marked conversation as read: $conversationId")
            }
        } catch (e: Exception) {
            Log.e("ChatScreenViewModel", "Error marking conversation as read", e)
        }
    }

    fun linkBluetoothDevice(macAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("ChatScreenViewModel", "=== BLUETOOTH LINKING DEBUG ===")
                Log.d("ChatScreenViewModel", "MAC Address: $macAddress")
                Log.d("ChatScreenViewModel", "Local UUID: $localUuid")
                Log.d("ChatScreenViewModel", "Remote User UUID: $userUuid")
                Log.d("ChatScreenViewModel", "Conversation ID: $conversationId")

                // STEP 1: Check if this MAC is already linked to another user
                val allUsers = userRepository.getAllUsers()
                val userWithThisMac = allUsers.find { user ->
                    user.macAddress == macAddress &&
                            user.uuid != userUuid &&
                            user.macAddress != "AA:BB:CC:DD:EE:FF" // Ignore placeholder
                }

                if (userWithThisMac != null) {
                    Log.w("ChatScreenViewModel", "MAC $macAddress is already linked to ${userWithThisMac.name} (${userWithThisMac.uuid})")
                    Log.d("ChatScreenViewModel", "Unlinking from previous user...")

                    // Unlink from the previous user
                    userRepository.insertOrUpdateUser(
                        uuid = userWithThisMac.uuid,
                        name = userWithThisMac.name,
                        address = userWithThisMac.address,
                        macAddress = "AA:BB:CC:DD:EE:FF" // Use placeholder to unlink
                    )
                }

                // STEP 2: Check if current user exists
                val currentUser = userRepository.getUser(userUuid)
                Log.d("ChatScreenViewModel", "User lookup result: ${currentUser?.name ?: "NULL"}")

                if (currentUser == null) {
                    Log.e("ChatScreenViewModel", "Cannot link - user $userUuid not found")

                    Log.d("ChatScreenViewModel", "All users in database:")
                    allUsers.forEach { user ->
                        Log.d("ChatScreenViewModel", "  - UUID: ${user.uuid}, Name: ${user.name}, MAC: ${user.macAddress}")
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            offlineWarning = "Cannot link: User not found in database"
                        )}
                    }
                    return@launch
                }

                // STEP 3: Link the MAC to the current user
                userRepository.insertOrUpdateUser(
                    uuid = currentUser.uuid,
                    name = currentUser.name,
                    address = currentUser.address,
                    macAddress = macAddress
                )

                withContext(Dispatchers.Main) {
                    _linkedBtMac.value = macAddress
                }

                Log.d("ChatScreenViewModel", "Successfully linked $macAddress to ${currentUser.name} ($userUuid)")

                // Verify it was saved
                val updatedUser = userRepository.getUser(userUuid)
                Log.d("ChatScreenViewModel", "Verified MAC in database: ${updatedUser?.macAddress}")

            } catch (e: Exception) {
                Log.e("ChatScreenViewModel", "Error linking Bluetooth device", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        offlineWarning = "Error linking device: ${e.message}"
                    )}
                }
            }
        }
    }
// unlink a bluetooth device
    fun unlinkDevice() {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getUser(userUuid)
                if (currentUser  != null) {
                    userRepository.insertOrUpdateUser(
                        uuid = currentUser.uuid,
                        name = currentUser.name,
                        address = currentUser.address,
                        macAddress = "AA:BB:CC:DD:EE:FF"  // <-- pass in a placeholder address
                    )

                    _linkedBtMac.value = null // <-- null out the linked mac address

                    Log.d("ChatScreenViewModel", "Device unlinked")

                }
            } catch (e: Exception) {
                Log.e("ChatScreenViewModel", "Failed to unlink", e)
            }
        }
    }

    /*
 * This is the Bluetooth equivalent of sendChatMessage() in ChatScreenViewModel
 *
 * This function:
 * 1. Checks if a device is linked
 * 2. Saves message to local database
 * 3. Updates local conversation
 * 4. Sends message via Bluetooth
 */
    fun sendBluetoothChatMessage(message: String, file: URI?) {
        val sendTime = System.currentTimeMillis()

        if (_linkedBtMac.value == null) {
            return // don't launch the coroutine
        }

        viewModelScope.launch {
            // current user is the most recent version of the remote device
            // verify the stateflow device with the device in the database
            val currentUser = userRepository.getUser(userUuid)
            val verifiedMac = currentUser?.macAddress

            if (verifiedMac != _linkedBtMac.value) {
                Log.w("ChatScreenViewModel", "MAC mismatch detected, re-syncing")
                _linkedBtMac.value = verifiedMac
            }

            // final verification before sending
            if (verifiedMac == null) {
                Log.w("ChatScreenViewModel", "Verified: No device linked")
                return@launch
            }

            // added logs for debugging
            val linkedMacAddress = verifiedMac
            Log.d("ChatScreenViewModel", "Sending Bluetooth message to MAC: $linkedMacAddress")

            // Step 2: Create message entity (same as Wi-Fi)
            // Note: keeping file=null for now to mirror skeleton; wire file transfers later.
            val messageEntity = Message(
                id = 0,
                dateReceived = sendTime,
                content = message,
                sender = "Me",
                chat = chatName,
                file = file
            )

            // Step 3: Save to local database (same as Wi-Fi)
            db.messageDao().addMessage(messageEntity)

            // Step 4: Update conversation with the message
            if (currentUser != null) {  // Use currentUser instead of userEntity
                try {
                    val remoteUser = UserEntity(
                        uuid = userUuid,
                        name = currentUser.name,
                        address = currentUser.address,
                        macAddress = currentUser.macAddress
                    )

                    val conversation = conversationRepository.getOrCreateConversation(
                        localUuid = localUuid,
                        remoteUser = remoteUser
                    )

                    conversationRepository.updateWithMessage(
                        conversationId = conversation.id,
                        message = messageEntity
                    )

                    Log.d("ChatScreenViewModel", "Updated conversation with sent Bluetooth message")
                } catch (e: Exception) {
                    Log.e("ChatScreenViewModel", "Failed to update conversation", e)
                }
            }

            // Step 5: Send via Bluetooth
            // changed to mirror sendChatMessage and let the btClient handle the HTTP/response
            try {
                val success = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(5000) {
                        bluetoothMessageClient.sendBtChatMessageWithStatus(
                            macAddress = linkedMacAddress,
                            time = sendTime,
                            message = message,
                            f = file
                        )
                    } ?: false
                }

                // if we get anything other than success, then we unlink from this device
                if (success) {
                    Log.d(
                        "ChatScreenViewModel",
                        "Bluetooth message sent successfully to $linkedMacAddress"
                    )
                } else {
                    Log.e(
                        "ChatScreenViewModel",
                        "Failed to send Bluetooth message to $linkedMacAddress"
                    )

                    Log.e(
                        "ChatScreenViewModel",
                        "Unlinking from: $linkedMacAddress"
                    )
                   unlinkDevice()
                }
            } catch (e: Exception) {
                Log.e("ChatScreenViewModel", "Error sending Bluetooth message: ${e.message}", e)
                _uiState.update { prev ->
                    prev.copy(offlineWarning = "Error sending Bluetooth message: ${e.message}")
                }
            }
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

        Log.d("ChatDebug", "Sending message to chat: $chatName")
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

                    Log.d("ChatScreenViewModel", "Updated conversation with sent message")
                } catch (e: Exception) {
                    Log.e(
                        "ChatScreenViewModel",
                        "Failed to update conversation with sent message",
                        e
                    )
                }
            }

            if (isOnline) {
                try {
                    // Use withContext to ensure network operations run on IO thread
                    val delivered = withContext(Dispatchers.IO) {
                        // Try with a timeout to prevent blocking
                        withTimeoutOrNull(5000) {
                            appServer.sendChatMessageWithStatus(
                                virtualAddress,
                                sendTime,
                                message,
                                file
                            )
                        } ?: false
                    }

                    // Update UI based on delivery status
                    if (!delivered) {
                        Log.d("ChatDebug", "Message delivery failed")
                        _uiState.update { prev ->
                            prev.copy(offlineWarning = "Message delivery failed. Device may be offline.")
                        }
                        // Force device status verification
                        DeviceStatusManager.verifyDeviceStatus(ipAddress)
                    } else {
                        Log.d("ChatDebug", "Message delivered successfully")
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreenViewModel", "Error sending message: ${e.message}", e)
                    _uiState.update { prev ->
                        prev.copy(offlineWarning = "Error sending message: ${e.message}")
                    }
                }
            } else {
                Log.d(
                    "ChatScreenViewModel",
                    "Device $ipAddress appears to be offline, message saved locally only"
                )
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
