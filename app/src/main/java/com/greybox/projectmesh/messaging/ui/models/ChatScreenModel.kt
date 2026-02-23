package com.greybox.projectmesh.messaging.ui.models

import com.greybox.projectmesh.messaging.data.entities.Message
import java.net.InetAddress

/**
 * Data model representing the state of a chat screen in the UI.
 *
 * @property deviceName Optional name of the device or user.
 * @property virtualAddress Virtual network address of the device; defaults to 192.168.0.1.
 * @property allChatMessages List of all messages to display on the chat screen; defaults to empty list.
 * @property offlineWarning Optional warning message to show if the device/user is offline.
 */
data class ChatScreenModel(
    val deviceName: String? = null,
    val virtualAddress: InetAddress = InetAddress.getByName("192.168.0.1"),
    val allChatMessages: List<Message> = emptyList(),
    val offlineWarning: String? = null
)
