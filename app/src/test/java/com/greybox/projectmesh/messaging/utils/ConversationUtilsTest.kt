package com.greybox.projectmesh.messaging.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM-only tests for ConversationUtils.createConversationId.
 *
 * Verifies:
 * - stable ordering of UUID pairs
 * - identical UUIDs produce expected ID
 * - special-case handling for device UUIDs
 * - offline device UUIDs follow same special rule
 */
class ConversationUtilsTest {

    // ----------------------------------------
    // Ordering: "a","b" should equal "b","a"
    // ----------------------------------------
    @Test
    fun checkCreateConversationId() {
        val id1 = ConversationUtils.createConversationId("a", "b")
        val id2 = ConversationUtils.createConversationId("b", "a")

        assertEquals("a-b", id1)
        assertEquals(id1, id2)
    }

    // --------------------------------------
    // Identical values should join naturally
    // --------------------------------------
    @Test
    fun checkIdenticalUuids() {
        val id = ConversationUtils.createConversationId("same", "same")
        assertEquals("same-same", id)
    }

    // -----------------------------------------------------
    // Special rule: remote UUID "test-device-uuid" maps to
    // "local-user-<uuid>"
    // -----------------------------------------------------
    @Test
    fun checkSpecialCase() {
        val id = ConversationUtils.createConversationId("anything", "test-device-uuid")
        assertEquals("local-user-test-device-uuid", id)
    }

    // -----------------------------------------------------
    // Offline device rule: mirrors test-device behavior
    // -----------------------------------------------------
    @Test
    fun checkSecondSpecialCase() {
        val id = ConversationUtils.createConversationId("anything", "offline-test-device-uuid")
        assertEquals("local-user-offline-test-device-uuid", id)
    }
}
