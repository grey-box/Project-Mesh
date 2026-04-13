package com.greybox.projectmesh.db

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier
import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.dao.ConversationDao
import com.greybox.projectmesh.user.UserDao
import androidx.room.RoomDatabase

/**
 * JVM-only tests for MeshDatabase class shape.
 * These do NOT spin up Room or touch Android runtime.
 *
 * We verify:
 *  - MeshDatabase is abstract
 *  - MeshDatabase extends RoomDatabase
 *  - Required DAO methods exist and have the correct return types
 *
 * Anything involving entities, queries, schema, and migrations belongs
 * in src/androidTest with an in-memory RoomDatabase.
 */
class MeshDatabaseTest {

    @Test
    fun meshDatabase_isAbstract_and_extendsRoomDatabase() {
        val cls = MeshDatabase::class.java

        // Must be abstract
        assertTrue("MeshDatabase must be abstract",
            Modifier.isAbstract(cls.modifiers))

        // Must extend androidx.room.RoomDatabase
        assertTrue("MeshDatabase must extend RoomDatabase",
            RoomDatabase::class.java.isAssignableFrom(cls))
    }

    @Test
    fun meshDatabase_has_requiredDaoMethods_with_correctReturnTypes() {
        val cls = MeshDatabase::class.java

        // messageDao(): MessageDao
        val messageDaoMethod = cls.getMethod("messageDao")
        assertEquals(MessageDao::class.java, messageDaoMethod.returnType)

        // userDao(): UserDao
        val userDaoMethod = cls.getMethod("userDao")
        assertEquals(UserDao::class.java, userDaoMethod.returnType)

        // conversationDao(): ConversationDao
        val conversationDaoMethod = cls.getMethod("conversationDao")
        assertEquals(ConversationDao::class.java, conversationDaoMethod.returnType)
    }
}
