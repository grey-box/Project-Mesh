package com.greybox.projectmesh.messaging.data.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {

    @Test
    fun constructor_defaults_areApplied() {
        val conversation = Conversation(
            id = "convo-1",
            userUuid = "user-2",
            userName = "Alice",
            userAddress = null,
            lastMessage = null,
            lastMessageTime = 0L
        )

        assertEquals(0, conversation.unreadCount)
        assertFalse(conversation.isOnline)
    }

    @Test
    fun dataClass_copy_and_equality_behaveAsExpected() {
        val original = Conversation(
            id = "convo-2",
            userUuid = "user-3",
            userName = "Bob",
            userAddress = "10.0.0.2",
            lastMessage = "hi",
            lastMessageTime = 10L,
            unreadCount = 1,
            isOnline = true
        )

        val copy = original.copy(unreadCount = 5)

        assertEquals(original.id, copy.id)
        assertEquals(original.userUuid, copy.userUuid)
        assertEquals(original.userName, copy.userName)
        assertEquals(5, copy.unreadCount)
        assertTrue(copy.isOnline)
        assertTrue(original != copy)
    }

    @Test
    fun dataClass_destructuring_order_matchesConstructorOrder() {
        val conversation = Conversation(
            id = "convo-3",
            userUuid = "u9",
            userName = "Zed",
            userAddress = "10.0.0.9",
            lastMessage = "hey",
            lastMessageTime = 42L,
            unreadCount = 6,
            isOnline = true
        )

        val (
            id,
            userUuid,
            userName,
            userAddress,
            lastMessage,
            lastMessageTime,
            unreadCount,
            isOnline
        ) = conversation

        assertEquals("convo-3", id)
        assertEquals("u9", userUuid)
        assertEquals("Zed", userName)
        assertEquals("10.0.0.9", userAddress)
        assertEquals("hey", lastMessage)
        assertEquals(42L, lastMessageTime)
        assertEquals(6, unreadCount)
        assertTrue(isOnline)
    }
}
