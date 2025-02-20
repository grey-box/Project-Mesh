package com.greybox.projectmesh.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.user.UserDao
import com.greybox.projectmesh.user.UserEntity

@Database(
    entities = [
        Message::class,
        UserEntity::class  // <- add this
    ], version = 3)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
}
