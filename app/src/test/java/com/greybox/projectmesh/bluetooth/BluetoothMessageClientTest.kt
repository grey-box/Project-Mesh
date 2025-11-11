package com.greybox.projectmesh.bluetooth

import com.greybox.projectmesh.messaging.data.entities.Message
import com.ustadmobile.meshrabiya.log.MNetLogger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import rawhttp.core.RawHttp
import java.net.InetAddress
import java.net.URI

class BluetoothMessageClientTest {

    // Helper attributes
    private val rawHttp = RawHttp()
    private val json = Json { encodeDefaults = true }
    private val logger = mockk<MNetLogger>(relaxed = true)
    private val mockBtClient = mockk<HttpOverBluetoothClient>()
    private val localAddr = InetAddress.getByName("10.0.0.42")

    // DI container that returns the Bluetooth client
    private fun testDi(): DI = DI {
        bind<HttpOverBluetoothClient>() with singleton { mockBtClient }
    }
}