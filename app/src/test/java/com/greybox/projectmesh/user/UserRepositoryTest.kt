package com.greybox.projectmesh.user

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryTest {

    private lateinit var userDao: UserDao
    private lateinit var repo: UserRepository

    @Before
    fun setUp() {
        userDao = mockk(relaxed = true)
        repo = UserRepository(userDao)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun insertOrUpdateUser_whenNoExistingUser_insertsNewUser() = runTest {
        val uuid = "u1"
        val name = "Alice"
        val address = "10.0.0.1"

        coEvery { userDao.getUserByUuid(uuid) } returns null
        coEvery { userDao.insertUser(any()) } returns Unit

        repo.insertOrUpdateUser(uuid, name, address)

        coVerify(exactly = 1) { userDao.getUserByUuid(uuid) }
        coVerify(exactly = 1) {
            userDao.insertUser(
                match {
                    it.uuid == uuid && it.name == name && it.address == address && it.lastSeen == null
                }
            )
        }
        coVerify(exactly = 0) { userDao.updateUser(any()) }
    }

    @Test
    fun insertOrUpdateUser_whenExistingUser_updatesExistingUser() = runTest {
        val uuid = "u2"
        val old = UserEntity(uuid = uuid, name = "Old", address = "10.0.0.2", lastSeen = 123L)
        val newName = "New"
        val newAddress = "10.0.0.99"

        coEvery { userDao.getUserByUuid(uuid) } returns old
        coEvery { userDao.updateUser(any()) } returns Unit

        repo.insertOrUpdateUser(uuid, newName, newAddress)

        coVerify(exactly = 1) { userDao.getUserByUuid(uuid) }
        coVerify(exactly = 0) { userDao.insertUser(any()) }
        coVerify(exactly = 1) {
            userDao.updateUser(
                match {
                    it.uuid == uuid && it.name == newName && it.address == newAddress && it.lastSeen == old.lastSeen
                }
            )
        }
    }

    @Test
    fun insertOrUpdateUser_whenExistingUser_updatesAndCanNullOutAddress() = runTest {
        val uuid = "u3"
        val old = UserEntity(uuid = uuid, name = "Old", address = "10.0.0.3", lastSeen = null)
        val newName = "New"

        coEvery { userDao.getUserByUuid(uuid) } returns old
        coEvery { userDao.updateUser(any()) } returns Unit

        repo.insertOrUpdateUser(uuid, newName, null)

        coVerify(exactly = 1) {
            userDao.updateUser(
                match {
                    it.uuid == uuid && it.name == newName && it.address == null && it.lastSeen == null
                }
            )
        }
    }

    @Test
    fun getUserByIp_delegatesToDao() = runTest {
        val ip = "10.0.0.4"
        val entity = UserEntity(uuid = "u4", name = "Bob", address = ip)

        coEvery { userDao.getUserByIp(ip) } returns entity

        val got = repo.getUserByIp(ip)
        assertEquals(entity, got)
        coVerify(exactly = 1) { userDao.getUserByIp(ip) }
    }

    @Test
    fun getUser_delegatesToDao() = runTest {
        val uuid = "u5"
        val entity = UserEntity(uuid = uuid, name = "Cara", address = null)

        coEvery { userDao.getUserByUuid(uuid) } returns entity

        val got = repo.getUser(uuid)
        assertEquals(entity, got)
        coVerify(exactly = 1) { userDao.getUserByUuid(uuid) }
    }

    @Test
    fun getUser_whenNotFound_returnsNull() = runTest {
        val uuid = "missing"
        coEvery { userDao.getUserByUuid(uuid) } returns null

        val got = repo.getUser(uuid)
        assertNull(got)
        coVerify(exactly = 1) { userDao.getUserByUuid(uuid) }
    }

    @Test
    fun getAllConnectedUsers_delegatesToDao() = runTest {
        val list = listOf(
            UserEntity(uuid = "u1", name = "A", address = "10.0.0.1"),
            UserEntity(uuid = "u2", name = "B", address = "10.0.0.2")
        )

        coEvery { userDao.getAllConnectedUsers() } returns list

        val got = repo.getAllConnectedUsers()
        assertEquals(list, got)
        coVerify(exactly = 1) { userDao.getAllConnectedUsers() }
    }

    @Test
    fun getAllUsers_delegatesToDao() = runTest {
        val list = listOf(
            UserEntity(uuid = "u1", name = "A", address = null),
            UserEntity(uuid = "u2", name = "B", address = "10.0.0.2")
        )

        coEvery { userDao.getAllUsers() } returns list

        val got = repo.getAllUsers()
        assertEquals(list, got)
        coVerify(exactly = 1) { userDao.getAllUsers() }
    }

    @Test
    fun hasUser_delegatesToDao_true() = runTest {
        val uuid = "uTrue"
        coEvery { userDao.hasWithID(uuid) } returns true

        val got = repo.hasUser(uuid)
        assertEquals(true, got)
        coVerify(exactly = 1) { userDao.hasWithID(uuid) }
    }

    @Test
    fun hasUser_delegatesToDao_false() = runTest {
        val uuid = "uFalse"
        coEvery { userDao.hasWithID(uuid) } returns false

        val got = repo.hasUser(uuid)
        assertEquals(false, got)
        coVerify(exactly = 1) { userDao.hasWithID(uuid) }
    }
}
