package com.greybox.projectmesh.messaging.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kodein.di.DI

class MessageMigrationUtilsTest {

    private val di = DI {}
    private val utils = MessageMigrationUtils(di)

    @Test
    fun createConversationId_sortsNormalUuids() {
        val uuid1 = "b-uuid"
        val uuid2 = "a-uuid"

        val result = utils.createConversationId(uuid1, uuid2)

        assertEquals("a-uuid-b-uuid", result)
    }

    @Test
    fun createConversationId_handlesTestDeviceUuid() {
        val uuid1 = "some-other-uuid"
        val uuid2 = "test-device-uuid"

        val result = utils.createConversationId(uuid1, uuid2)

        assertEquals("local-user-test-device-uuid", result)
    }

    @Test
    fun createConversationId_handlesOfflineTestDeviceUuid() {
        val uuid1 = "some-other-uuid"
        val uuid2 = "offline-test-device-uuid"

        val result = utils.createConversationId(uuid1, uuid2)

        assertEquals("local-user-offline-test-device-uuid", result)
    }

    @Test
    fun createConversationId_isDeterministicForSamePair() {
        val uuidA = "1111-aaaa"
        val uuidB = "2222-bbbb"

        val id1 = utils.createConversationId(uuidA, uuidB)
        val id2 = utils.createConversationId(uuidB, uuidA)

        assertEquals(id1, id2)
    }
}
