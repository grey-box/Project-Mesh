package com.greybox.projectmesh.bluetooth

import android.content.Context
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.user.UserEntity
import com.ustadmobile.meshrabiya.log.MNetLogger
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import rawhttp.core.RawHttp

/**
 *  This test is a part of a larger set of tests used to validate BluetoothServer's handling of 
 *  remote device user identities, specifically it's ability to deny requests without crashing if a remote device request does 
 *  not match the identity it was expecting. 
 * 
 *  General unit tests for BluetoothServer's user request validation behavior.
 */
class BluetoothServerValidateRemoteDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockLogger: MNetLogger
    private lateinit var mockDb: MeshDatabase
    private lateinit var rawHttp: RawHttp
    private lateinit var json: Json
    private lateinit var server: BluetoothServer

    @Before
    fun setup() {
        mockContext = Mockito.mock(Context::class.java)
        mockLogger = Mockito.mock(MNetLogger::class.java)
        mockDb = Mockito.mock(MeshDatabase::class.java)

        // We don’t care about actual DB operations, just avoid crashes
        val mockMessageDao = Mockito.mock(
            Class.forName("com.greybox.projectmesh.db.MessageDao")
        )
        Mockito.`when`(mockDb.messageDao()).thenReturn(mockMessageDao)

        rawHttp = RawHttp()
        json = Json { ignoreUnknownKeys = true }

        // Default fake user repository (used in MAC mismatch test)
        val fakeUserRepo = object :
            com.greybox.projectmesh.GlobalApp.GlobalUserRepo.UserRepository {
            override fun getUserByIp(ip: String): UserEntity? {
                // returns a user with mismatched MAC so we can trigger 400s easily
                return UserEntity(
                    uuid = "user-1",
                    name = "Test User",
                    ipAddress = ip,
                    macAddress = "11:22:33:44:55:66"
                )
            }
        }
        GlobalApp.GlobalUserRepo.userRepository = fakeUserRepo

        server = BluetoothServer(
            context = mockContext,
            rawHttp = rawHttp,
            logger = mockLogger,
            db = mockDb,
            json = json,
            maxClients = 1
        )
    }

    /** Case 1: MAC mismatch should yield 400. More thorough security testing in BluetoothServerValidateMACMismatch */
    @Test
    fun chatRequest_withMacMismatch_returns400() {
        val request = makeChatRequest(
            sender = "192.168.1.50",
            content = "hello over bluetooth"
        )
        val remoteDeviceAddress = "AA:BB:CC:DD:EE:FF" // mismatched MAC

        val response = server.handleRequest(remoteDeviceAddress, request)
        assertEquals(400, response.statusCode)
    }

    /** Case 2: Sender is missing (null in JSON) → should be 400 "Missing sender parameter". */
    @Test
    fun chatRequest_withNullSender_returns400() {
        // Explicitly send "sender": null
        val jsonBody = """
            {
              "content": "message with no sender",
              "sender": null
            }
        """.trimIndent()

        val request = makeRequestFromJson(jsonBody)
        val response = server.handleRequest("AA:BB:CC:DD:EE:FF", request)

        assertEquals(
            "Expected 400 when sender is null",
            400,
            response.statusCode
        )
    }

    /** Case 3: Sender is invalid (not an IP address) → passes schema but fails validation → 400. */
    @Test
    fun chatRequest_withInvalidSenderFormat_returns400() {
        // Sender string that's not an IP address (but still non-null)
        val jsonBody = """
            {
              "content": "hello world",
              "sender": "not-an-ip"
            }
        """.trimIndent()

        val request = makeRequestFromJson(jsonBody)
        val response = server.handleRequest("AA:BB:CC:DD:EE:FF", request)

        // validateRemoteDevice() will call userRepository.getUserByIp("not-an-ip"),
        // which will return a user but mismatch MAC → still 400.
        assertEquals(
            "Expected 400 when sender is invalid format",
            400,
            response.statusCode
        )
    }

    // -------------------------------
    // Helper functions
    // -------------------------------

    private fun makeChatRequest(sender: String, content: String): rawhttp.core.RawHttpRequest {
        val jsonBody = """
            {
              "content": "$content",
              "sender": "$sender",
              "dateReceived": 1730800000000
            }
        """.trimIndent()
        return makeRequestFromJson(jsonBody)
    }

    private fun makeRequestFromJson(jsonBody: String): rawhttp.core.RawHttpRequest {
        val contentLength = jsonBody.toByteArray(Charsets.UTF_8).size
        val requestText = """
            POST /chat HTTP/1.1
            Host: localhost
            Content-Type: application/json
            Content-Length: $contentLength

            $jsonBody
        """.trimIndent()
        return rawHttp.parseRequest(requestText)
    }
}
