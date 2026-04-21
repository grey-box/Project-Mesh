package com.greybox.projectmesh.user

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.db.MeshDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class UserDaoTest {

    private lateinit var db: MeshDatabase
    private lateinit var dao: UserDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MeshDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_and_getByUuid_work() = runTest {
        val user = UserEntity("u1", "Alice", "10.0.0.1", 10L)

        dao.insertUser(user)

        val got = dao.getUserByUuid("u1")
        assertEquals(user, got)
    }

    @Test
    fun getUserByIp_returnsMatchingUser() = runTest {
        dao.insertUser(UserEntity("u1", "A", "10.0.0.1", null))
        dao.insertUser(UserEntity("u2", "B", "10.0.0.2", null))

        val got = dao.getUserByIp("10.0.0.2")
        assertEquals("u2", got?.uuid)
        assertEquals("B", got?.name)
    }

    @Test
    fun updateUser_replacesExistingRowValues() = runTest {
        dao.insertUser(UserEntity("u3", "Old", "10.0.0.3", 100L))

        dao.updateUser(UserEntity("u3", "New", null, 200L))

        val got = dao.getUserByUuid("u3")
        assertEquals("New", got?.name)
        assertNull(got?.address)
        assertEquals(200L, got?.lastSeen)
    }

    @Test
    fun hasWithID_returnsTrueWhenExists_andFalseWhenMissing() = runTest {
        dao.insertUser(UserEntity("u4", "Dana", "10.0.0.4", null))

        assertTrue(dao.hasWithID("u4"))
        assertFalse(dao.hasWithID("missing"))
    }

    @Test
    fun getAllConnectedUsers_filtersOutNullAddresses() = runTest {
        dao.insertUser(UserEntity("u5", "Connected", "10.0.0.5", null))
        dao.insertUser(UserEntity("u6", "Offline", null, null))

        val connected = dao.getAllConnectedUsers()

        assertEquals(1, connected.size)
        assertEquals("u5", connected.single().uuid)
    }

    @Test
    fun getAllUsers_returnsAllRows() = runTest {
        dao.insertUser(UserEntity("u7", "A", null, null))
        dao.insertUser(UserEntity("u8", "B", "10.0.0.8", null))

        val all = dao.getAllUsers()

        assertEquals(2, all.size)
        assertEquals(setOf("u7", "u8"), all.map { it.uuid }.toSet())
    }

    @Test
    fun missingQueries_returnNull() = runTest {
        assertNull(dao.getUserByUuid("none"))
        assertNull(dao.getUserByIp("0.0.0.0"))
    }
}
