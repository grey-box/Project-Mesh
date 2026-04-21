package com.greybox.projectmesh.user

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserEntityTest {

    @Test
    fun constructor_defaults_addressAndLastSeen_toNull() {
        val entity = UserEntity(
            uuid = "u1",
            name = "Alice"
        )

        assertNull(entity.address)
        assertNull(entity.lastSeen)
    }

    @Test
    fun dataClass_equality_hashCode_andCopy_areConsistent() {
        val a = UserEntity("u2", "Bob", "10.0.0.2", 100L)
        val b = UserEntity("u2", "Bob", "10.0.0.2", 100L)
        val c = a.copy(name = "Bobby")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals("Bobby", c.name)
        assertEquals("u2", c.uuid)
    }

    @Test
    fun nullableFields_supportOfflineAndUnknownLastSeenCases() {
        val offline = UserEntity(
            uuid = "u3",
            name = "Offline Device",
            address = null,
            lastSeen = null
        )

        assertNull(offline.address)
        assertNull(offline.lastSeen)
    }

    @Test
    fun serialization_roundTrip_preservesAllFields() {
        val original = UserEntity(
            uuid = "u4",
            name = "Carol",
            address = "192.168.1.10",
            lastSeen = 999L
        )

        val encoded = Json.encodeToString(UserEntity.serializer(), original)
        val decoded = Json.decodeFromString(UserEntity.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
