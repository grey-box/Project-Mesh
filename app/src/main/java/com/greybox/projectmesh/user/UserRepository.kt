package com.greybox.projectmesh.user

import android.util.Log

/**
 * Repository layer that wraps UserDao calls and applies simple business logic.
 *
 * @property userDao Data access object for interacting with the UserEntity table
 */
class UserRepository(private val userDao: UserDao) {

    /**
     * Inserts a new user or updates an existing one.
     *
     * @param uuid Unique identifier for the user
     * @param name Display name of the user
     * @param address Optional network address
     */
    suspend fun insertOrUpdateUser(uuid: String, name: String, address: String?) {
        val existing = userDao.getUserByUuid(uuid)
        if (existing == null) {
            // Insert new user with address
            userDao.insertUser(
                UserEntity(
                    uuid = uuid,
                    name = name,
                    address = address
                )
            )
        } else {
            // Update existing user, copying over address
            userDao.updateUser(
                existing.copy(
                    name = name,
                    address = address
                )
            )
        }
    }

    /**
     * Retrieves a user by their IP address.
     *
     * @param ip IP address to search by
     * @return Matching UserEntity or null
     */
    suspend fun getUserByIp(ip: String): UserEntity? {
        return userDao.getUserByIp(ip)
    }

    /**
     * Retrieves a user by their UUID.
     */
    suspend fun getUser(uuid: String): UserEntity? {
        return userDao.getUserByUuid(uuid)
    }

    /**
     * Retrieves all users that have a non-null address,
     * meaning users currently considered connected.
     */
    suspend fun getAllConnectedUsers(): List<UserEntity> {
        return userDao.getAllConnectedUsers()
    }

    /**
     * Retrieves all users stored in the database.
     */
    suspend fun getAllUsers(): List<UserEntity> {
        return userDao.getAllUsers()
    }

    /**
     * Checks if a user exists by UUID.
     */
    suspend fun hasUser(uuid: String): Boolean {
        return userDao.hasWithID(uuid)
    }

    // Add more methods as needed
}
