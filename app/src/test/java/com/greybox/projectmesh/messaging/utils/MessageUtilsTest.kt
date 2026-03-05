package com.greybox.projectmesh.messaging.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class MessageUtilsTest {

    @Test
    fun formatTimestamp_formatsToHoursAndMinutesInUtc() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val timestamp = 0L
            val result = MessageUtils.formatTimestamp(timestamp)
            assertEquals("00:00", result)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun generateChatId_isOrderIndependent() {
        val id1 = MessageUtils.generateChatId("alice", "bob")
        val id2 = MessageUtils.generateChatId("bob", "alice")
        assertEquals("alice-bob", id1)
        assertEquals(id1, id2)
    }

    @Test
    fun generateChatId_handlesSameUser() {
        val id = MessageUtils.generateChatId("alice", "alice")
        assertEquals("alice-alice", id)
    }

    @Test
    fun generateChatId_isCaseSensitive() {
        val id = MessageUtils.generateChatId("Alice", "alice")
        assertEquals("Alice-alice", id)
    }
}
