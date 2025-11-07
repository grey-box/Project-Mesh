//package com.greybox.projectmesh.bluetooth
//
//import android.content.Context
//import com.greybox.projectmesh.GlobalApp
//import com.greybox.projectmesh.db.MeshDatabase
//import com.greybox.projectmesh.messaging.data.entities.Message
//import com.greybox.projectmesh.messaging.network.MessageNetworkHandler
//import com.greybox.projectmesh.user.UserEntity
//import com.ustadmobile.meshrabiya.log.MNetLogger
//import kotlinx.serialization.json.Json
//import org.junit.Assert.assertEquals
//import org.junit.Test
//import org.mockito.Mockito
//import rawhttp.core.RawHttp
//
///**
// *  This test is a part of a larger set of tests used to validate BluetoothServer's handling of
// *  remote device user identities, specifically it's ability to deny requests without crashing if a remote device request does
// *  not match the identity it was expecting.
// *
// *  This security test validates BluetoothServer's handling of mismatched or incorrect MAC addresses from a remote device
// *  when a MAC address is already established for such device. Such situations are common in MAC spoofing scenarios.
// */
//class BluetoothServerValidateMACMismatch {
//
//    /**
//     *  Security check : Tests BluetoothServer's handling remote device requests when a MAC address is different
//     *  or invalid from what it currently had saved.
//     */
//    @Test
//    fun chatRequest_withMacMismatch_returns400() {
//        //  Mocks and fakes for test ---
//        val mockContext = Mockito.mock(Context::class.java)
//        val mockLogger = Mockito.mock(MNetLogger::class.java)
//
//        // Mock DB + DAO since BluetoothServer will try to save the message
//        val mockDb = Mockito.mock(MeshDatabase::class.java)
//        // we don't actually care about DAO calls, just don't crash
//        val mockMessageDao = Mockito.mock(
//            Class.forName("com.greybox.projectmesh.db.MessageDao")
//        )
//        Mockito.`when`(mockDb.messageDao()).thenReturn(mockMessageDao)
//
//        val rawHttp = RawHttp()
//        val json = Json { ignoreUnknownKeys = true }
//
//        //        Fake the global user repository so that lookup-by-IP works,
//        //        but the MAC does NOT match the remote device address.
//        //
//        // This is the tricky part because BluetoothServer calls:
//        // GlobalApp.GlobalUserRepo.userRepository.getUserByIp(senderIP)
//        //
//        // So we install a fake one that returns a UserEntity with a DIFFERENT MAC.
//        val fakeUserRepo = object : com.greybox.projectmesh.GlobalApp.GlobalUserRepo.UserRepository {
//            override fun getUserByIp(ip: String): UserEntity? {
//                // pretend the sender is known by IP, but has a different MAC saved
//                return UserEntity(
//                    uuid = "user-1",
//                    name = "Test User",
//                    ipAddress = ip,
//                    macAddress = "11:22:33:44:55:66" // <-- expected MAC
//                )
//            }
//        }
//        // assign it to the global spot used by BluetoothServer
//        GlobalApp.GlobalUserRepo.userRepository = fakeUserRepo
//
//        // Create an instance of BluetoothServer with out mock parameters ---
//        val server = BluetoothServer(
//            context = mockContext,
//            rawHttp = rawHttp,
//            logger = mockLogger,
//            db = mockDb,
//            json = json,
//            maxClients = 1
//        )
//
//        // Build a valid-looking POST /chat request ---
//        // This JSON shape should pass JSON -> Message deserialization
//        // and then proceed to validateRemoteDevice().
//        val jsonBody = """
//            {
//              "content": "hello over bluetooth",
//              "sender": "192.168.1.50",
//              "dateReceived": 1730800000000
//            }
//        """.trimIndent()
//
//        val contentLength = jsonBody.toByteArray(Charsets.UTF_8).size
//
//        val requestText = """
//            POST /chat HTTP/1.1
//            Host: localhost
//            Content-Type: application/json
//            Content-Length: $contentLength
//
//            $jsonBody
//        """.trimIndent()
//
//        val request = rawHttp.parseRequest(requestText)
//
//        // This is the actual Bluetooth MAC of the connecting device.
//        // It does NOT match the one we put in the fake user above ("11:22:33:44:55:66"),
//        // so validateRemoteDevice(...) should fail and we should get 400.
//        val remoteDeviceAddress = "AA:BB:CC:DD:EE:FF"
//
//        // --- 5) Act: handle the request ---
//        val response = server.handleRequest(remoteDeviceAddress, request)
//
//        // --- 6) Assert: HTTP 400 because MAC mismatch ---
//        assertEquals(400, response.statusCode)
//    }
//}
