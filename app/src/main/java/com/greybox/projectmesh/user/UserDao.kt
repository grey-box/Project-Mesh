package com.greybox.projectmesh.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for user-related database operations.
 */
@Dao
interface UserDao {

    /**
     * Get a user by their UUID.
     *
     * @param uuid The UUID of the user
     * @return The matching UserEntity or null if not found
     */
    @Query("SELECT * FROM users WHERE uuid = :uuid LIMIT 1")
    suspend fun getUserByUuid(uuid: String): UserEntity?

    /**
     * Insert a new user into the database.
     * Replaces existing entry if there is a conflict.
     *
     * @param user The user entity to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    /**
     * Update an existing user in the database.
     *
     * @param user The user entity to update
     */
    @Update
    suspend fun updateUser(user: UserEntity)

    /**
     * Check if a user with a given UUID exists.
     *
     * @param uuid The UUID to check
     * @return True if a user exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE uuid = :uuid)")
    suspend fun hasWithID(uuid: String): Boolean

    /**
     * Get a user by their IP address.
     *
     * @param ip The IP address of the user
     * @return The matching UserEntity or null if not found
     */
    @Query("SELECT * FROM users WHERE address = :ip LIMIT 1")
    suspend fun getUserByIp(ip: String): UserEntity?

    /**
     * Get all users with a non-null address (i.e., currently connected users).
     *
     * @return List of connected users
     */
    @Query("SELECT * FROM users WHERE address IS NOT NULL")
    suspend fun getAllConnectedUsers(): List<UserEntity>

    /**
     * Get all users in the database.
     *
     * @return List of all users
     */
    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>
}
