package com.greybox.projectmesh.messaging.ui.models

import com.greybox.projectmesh.messaging.data.entities.Message
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class ChatScreenModelTest {

    @Test
    fun chatScreenModel_usesDefaultValues_whenConstructedWithoutArguments() {
        val model = ChatScreenModel()

        assertNull(model.deviceName)
        assertEquals(InetAddress.getByName("192.168.0.1"), model.virtualAddress)
        assertEquals(emptyList<Message>(), model.allChatMessages)
        assertNull(model.offlineWarning)
    }

    @Test
    fun chatScreenModel_setsProvidedValues_correctly() {
        val virtualAddress = InetAddress.getByName("10.0.0.25")
        val messages = listOf(
            mockk<Message>(),
            mockk<Message>()
        )

        val model = ChatScreenModel(
            deviceName = "Pixel 8",
            virtualAddress = virtualAddress,
            allChatMessages = messages,
            offlineWarning = "Device is offline"
        )

        assertEquals("Pixel 8", model.deviceName)
        assertEquals(virtualAddress, model.virtualAddress)
        assertEquals(messages, model.allChatMessages)
        assertEquals("Device is offline", model.offlineWarning)
    }

    @Test
    fun chatScreenModel_supportsCopy_withUpdatedFields() {
        val original = ChatScreenModel(
            deviceName = "Old Device",
            virtualAddress = InetAddress.getByName("192.168.1.5"),
            allChatMessages = listOf(mockk()),
            offlineWarning = null
        )

        val updated = original.copy(
            deviceName = "New Device",
            offlineWarning = "No connection"
        )

        assertEquals("New Device", updated.deviceName)
        assertEquals(InetAddress.getByName("192.168.1.5"), updated.virtualAddress)
        assertEquals(1, updated.allChatMessages.size)
        assertEquals("No connection", updated.offlineWarning)
    }

    @Test
    fun chatScreenModel_dataClassEquality_worksForSameValues() {
        val address = InetAddress.getByName("172.16.0.3")
        val messages = listOf(mockk<Message>())

        val model1 = ChatScreenModel(
            deviceName = "Device A",
            virtualAddress = address,
            allChatMessages = messages,
            offlineWarning = "Offline"
        )

        val model2 = ChatScreenModel(
            deviceName = "Device A",
            virtualAddress = address,
            allChatMessages = messages,
            offlineWarning = "Offline"
        )

        assertEquals(model1, model2)
        assertEquals(model1.hashCode(), model2.hashCode())
    }
}