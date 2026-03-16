package com.greybox.projectmesh.server

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.dao.MessageDao
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.network.MessageNetworkHandler
import com.greybox.projectmesh.testing.TestDeviceService
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.log.MNetLogger
import fi.iki.elonen.NanoHTTPD
import io.mockk.Call
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AppServerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var httpClient: OkHttpClient
    private lateinit var call: okhttp3.Call
    private lateinit var logger: MNetLogger
    private lateinit var db: MeshDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var userRepository: UserRepository
    private lateinit var receiveDir: File
    private lateinit var server: AppServer

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("app_server_test", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("UUID", "local-uuid").commit()

        httpClient = mockk(relaxed = true)
        call = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        db = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        receiveDir = File(context.cacheDir, "appserver-test-${System.nanoTime()}").apply {
            mkdirs()
        }

        every { db.messageDao() } returns messageDao

        val di = DI {
            bind<SharedPreferences>(tag = "settings") with singleton { prefs }
        }

        server = AppServer(
            appContext = context,
            httpClient = httpClient,
            mLogger = logger,
            name = "test-node",
            port = 0,
            localVirtualAddr = InetAddress.getByName("10.0.0.1"),
            receiveDir = receiveDir,
            json = Json { encodeDefaults = true },
            db = db,
            di = di,
            userRepository = userRepository
        )
    }

    @After
    fun tearDown() {
        runCatching { unmockkObject(MessageNetworkHandler.Companion) }
        unmockkStatic(Log::class)
        clearAllMocks()
        server.close()
        receiveDir.deleteRecursively()
        prefs.edit().clear().commit()
    }

    @Test
    fun serve_ping_returnsPong() {
        val response = server.serve(session(uri = "/ping"))

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("PONG", readBody(response))
    }

    @Test
    fun serve_myInfo_returnsLocalUserJsonWithVirtualAddress() {
        coEvery { userRepository.getUser("local-uuid") } returns UserEntity(
            uuid = "local-uuid",
            name = "Local User",
            address = null
        )

        val response = server.serve(session(uri = "/myinfo"))
        val body = readBody(response)

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(body.contains("\"uuid\":\"local-uuid\""))
        assertTrue(body.contains("\"name\":\"Local User\""))
        assertTrue(body.contains("\"address\":\"10.0.0.1\""))
    }

    @Test
    fun serve_updateUserInfo_withNormalUserPayload_returnsBadRequest() {
        val payload = """{"uuid":"remote-1","name":"Alice","address":"10.0.0.5"}"""

        val response = server.serve(
            session(
                uri = "/updateUserInfo",
                method = NanoHTTPD.Method.POST,
                body = payload
            )
        )

        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
        assertTrue(readBody(response).contains("Invalid JSON schema"))
        coVerify(exactly = 0) { userRepository.insertOrUpdateUser(any(), any(), any()) }
    }

    @Test
    fun serve_chat_withEmptyPayload_returnsBadRequest() {
        val response = server.serve(
            session(
                uri = "/chat",
                method = NanoHTTPD.Method.POST,
                postData = ""
            )
        )

        assertEquals(NanoHTTPD.Response.Status.BAD_REQUEST, response.status)
        assertEquals("Empty or missing JSON payload", readBody(response))
    }

    @Test
    fun serve_chat_withValidPayload_savesHandledMessage() {
        mockkObject(MessageNetworkHandler.Companion)
        val handledMessage = Message(
            id = 0,
            dateReceived = 1_000L,
            content = "handled",
            sender = "Alice",
            chat = "chat-1"
        )
        every {
            MessageNetworkHandler.handleIncomingMessage(any(), any(), any(), any())
        } returns handledMessage
        coEvery { messageDao.addMessage(any()) } returns Unit

        val payload = """
            {"id":0,"chat":"chat-1","content":"hello","dateReceived":1000,"sender":"10.0.0.50"}
        """.trimIndent()

        val response = server.serve(
            session(
                uri = "/chat",
                method = NanoHTTPD.Method.POST,
                postData = payload
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("OK", readBody(response))
        coVerify(timeout = 2_000, exactly = 1) { messageDao.addMessage(handledMessage) }
    }

    @Test
    fun addOutgoingTransfer_withFileUri_tracksTransferAndMakesSendRequest() {
        val file = File.createTempFile("app-server", ".txt", context.cacheDir).apply {
            writeText("mesh payload")
            deleteOnExit()
        }
        val requestSlot = slot<Request>()
        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("OK".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val transfer = server.addOutgoingTransfer(
            uri = Uri.fromFile(file),
            toNode = InetAddress.getByName("10.0.0.55")
        )

        assertEquals(file.name, transfer.name)
        assertEquals(file.length().toInt(), transfer.size)
        assertEquals(AppServer.Status.PENDING, transfer.status)

        val tracked = runBlocking { server.outgoingTransfers.first() }
        assertEquals(1, tracked.size)
        assertEquals(transfer, tracked.single())

        io.mockk.verify(timeout = 2_000, exactly = 1) { httpClient.newCall(any()) }
        assertTrue(requestSlot.captured.url.toString().contains("/send?id=${transfer.id}"))
        assertEquals("POST", requestSlot.captured.method)
    }

    @Test
    fun sendChatMessageWithStatus_forTestDevice_savesEchoAndReturnsTrue() = runBlocking {
        coEvery { messageDao.addMessage(any()) } returns Unit

        val fileUri = URI.create("file:///tmp/sample.txt")
        val delivered = server.sendChatMessageWithStatus(
            address = InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP),
            time = 123L,
            message = "ping",
            f = fileUri
        )

        assertTrue(delivered)
        coVerify(exactly = 1) {
            messageDao.addMessage(
                match {
                    it.content == "Echo: ping" &&
                        it.sender == TestDeviceService.TEST_DEVICE_NAME &&
                        it.file == fileUri
                }
            )
        }
    }

    @Test
    fun sendChatMessageWithStatus_forRealDevice_postsJsonAndReturnsTrue() = runBlocking {
        val requestSlot = slot<Request>()
        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("OK".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val delivered = server.sendChatMessageWithStatus(
            address = InetAddress.getByName("10.0.0.8"),
            time = 456L,
            message = "hello world",
            f = null
        )

        assertTrue(delivered)
        assertEquals("POST", requestSlot.captured.method)
        assertEquals("hello world", requestSlot.captured.url.queryParameter("chatMessage"))
        assertEquals("456", requestSlot.captured.url.queryParameter("time"))
        assertEquals("10.0.0.1", requestSlot.captured.url.queryParameter("senderIp"))

        val body = Buffer().also { requestSlot.captured.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("\"content\":\"hello world\""))
        assertTrue(body.contains("\"chat\":\"10.0.0.8\""))
    }

    @Test
    fun checkDeviceReachable_whenCallThrows_returnsFalse() {
        every { httpClient.newCall(any()) } returns call
        every { call.execute() } throws IOException("network down")

        val reachable = server.checkDeviceReachable(InetAddress.getByName("10.0.0.9"))

        assertFalse(reachable)
    }

    private fun session(
        uri: String,
        method: NanoHTTPD.Method = NanoHTTPD.Method.GET,
        body: String = "",
        query: String = "",
        postData: String? = null
    ): NanoHTTPD.IHTTPSession {
        val session = mockk<NanoHTTPD.IHTTPSession>(relaxed = true)
        every { session.uri } returns uri
        every { session.method } returns method
        every { session.queryParameterString } returns query
        every { session.inputStream } returns ByteArrayInputStream(body.toByteArray())
        if (postData != null) {
            every { session.parseBody(any()) } answers {
                firstArg<MutableMap<String, String>>()["postData"] = postData
            }
        }
        return session
    }

    private fun readBody(response: NanoHTTPD.Response): String {
        return response.data.bufferedReader().use { it.readText() }
    }
}
