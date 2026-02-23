package com.greybox.projectmesh.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a user in the system.
 *
 * @property uuid Unique identifier for the user
 * @property name Display name of the user
 * @property address Optional network address (null if offline)
 * @property lastSeen Optional timestamp of the last time the user was seen
 */
@Serializable
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val address: String? = null, // Optional: null if offline
    val lastSeen: Long? = null   // Optional: timestamp in milliseconds
)
