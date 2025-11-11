package com.greybox.projectmesh.bluetooth

import android.content.Context
import com.greybox.projectmesh.messaging.data.MeshDatabase
import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.entities.Message
import com.ustadmobile.meshrabiya.log.MNetLogger
import io.mockk.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import rawhttp.core.RawHttp

class BluetoothServerTest {
    private val rawHttp = RawHttp()
    private val json = Json { encodeDefaults = true }

    // Mocks for Android + DB
    private val context = mockk<Context>(relaxed = true)
    private val logger = mockk<MNetLogger>(relaxed = true)
    private val db = mockk<MeshDatabase>()
    private val messageDao = mockk<MessageDao>(relaxed = true)

    private fun makeServer(testScope: TestScope): BluetoothServer {
        every { db.messageDao() } returns messageDao

        return BluetoothServer(
            context = context,
            rawHttp = rawHttp,
            logger = logger,
            json = json,
            db = db,
            scope = testScope,
            maxClients = 1
        )
    }
}