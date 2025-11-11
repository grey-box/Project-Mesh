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
    
    // Builds correct HTTP request & returns true on 200
    @Test
    fun sendMessageWithStatus_success() = runBlocking {
        // Arrange
        val sut = BluetoothMessageClient(
            mLogger = logger,
            rawHttp = rawHttp,
            json = json,
            di = testDi(),
            localVirtualAddr = localAddr
        )

        val mac = "01:23:45:67:89:AB"
        val sendTime = 1234L
        val msgText = "hello over bluetooth"

        // Capture the RawHttpRequest we send to HttpOverBluetoothClient.
        val requestSlot = slot<rawhttp.core.RawHttpRequest>()

        // Stub the underlying Bluetooth HTTP client.
        every {
            mockBtClient.sendRequest(
                remoteAddress = eq(mac),
                uuidMask = eq(BluetoothUuids.ALLOCATION_SERVICE_UUID),
                request = capture(requestSlot)
            )
        } returns mockk(relaxed = true) {
            // The 'response' property is a RawHttpResponse<*>
            every { response.statusCode } returns 200
            every { response.startLine.reason } returns "OK"
        }

        // Act
        val result = sut.sendBtChatMessageWithStatus(
            macAddress = mac,
            time = sendTime,
            message = msgText,
            f = null as URI?
        )

        // Assert: return value
        assertTrue(result)

        // Assert: sendRequest was called exactly once with correct MAC & UUID mask.
        verify(exactly = 1) {
            mockBtClient.sendRequest(
                remoteAddress = mac,
                uuidMask = BluetoothUuids.ALLOCATION_SERVICE_UUID,
                request = any()
            )
        }

        // Inspect the captured HTTP request.
        val capturedReq = requestSlot.captured

        // Method and path
        assertEquals("POST", capturedReq.method)
        assertEquals("/chat", capturedReq.uri.path)

        // Headers
        val hostHeader = capturedReq.headers["Host"]!!.first()
        assertEquals("01-23-45-67-89-AB.bluetooth", hostHeader)

        val contentType = capturedReq.headers["Content-Type"]!!.first()
        assertEquals("application/json", contentType)

        // Deserialize the body into Message and check fields
        val bodyStr = capturedReq.body.map { String(it.asRawBytes()) }.orElse("")
        assertTrue(bodyStr.isNotBlank())

        val msgObj = json.decodeFromString<Message>(bodyStr)
        assertEquals(msgText, msgObj.content)
        assertEquals(mac, msgObj.chat)
        assertEquals(localAddr.hostName, msgObj.sender)   // matches production code
        assertEquals(sendTime, msgObj.dateReceived)
    }

    // Returns false on non-200
    @Test
    fun sendMessageWithStatus_non200() = runBlocking {
        // Arrange
        val sut = BluetoothMessageClient(
            mLogger = logger,
            rawHttp = rawHttp,
            json = json,
            di = testDi(),
            localVirtualAddr = localAddr
        )

        val mac = "AA:BB:CC:DD:EE:FF"

        every {
            mockBtClient.sendRequest(any(), any(), any())
        } returns mockk(relaxed = true) {
            every { response.statusCode } returns 503
            every { response.startLine.reason } returns "Server Busy"
        }

        // Act
        val result = sut.sendBtChatMessageWithStatus(
            macAddress = mac,
            time = 42L,
            message = "busy?",
            f = null
        )

        // Assert
        assertFalse(result)
    }

    // Returns false when sendRequest throws
    @Test
    fun sendMessageWithStatus_exception() = runBlocking {
        // Arrange
        val sut = BluetoothMessageClient(
            mLogger = logger,
            rawHttp = rawHttp,
            json = json,
            di = testDi(),
            localVirtualAddr = localAddr
        )

        val mac = "AA:BB:CC:DD:EE:FF"

        // Simulate any failure inside HttpOverBluetoothClient.
        every {
            mockBtClient.sendRequest(any(), any(), any())
        } throws RuntimeException("Boom")

        // Act
        val result = sut.sendBtChatMessageWithStatus(
            macAddress = mac,
            time = 99L,
            message = "oops",
            f = null
        )

        // Assert
        assertFalse(result)
    }
}