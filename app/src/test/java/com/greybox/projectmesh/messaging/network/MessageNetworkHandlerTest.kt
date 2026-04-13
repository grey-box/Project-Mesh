package com.greybox.projectmesh.messaging.network

import android.content.SharedPreferences
import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MessageNetworkHandlerTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var call: Call
    private lateinit var localAddr: InetAddress

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        httpClient = mockk(relaxed = true)
        call = mockk(relaxed = true)
        localAddr = InetAddress.getByName("10.0.0.100")
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(Log::class)
    }

    @Test
    fun sendChatMessage_buildsExpectedRequest_withoutFile_andExecutesCall() {
        val requestSlot = io.mockk.slot<Request>()
        val latch = CountDownLatch(1)

        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            latch.countDown()
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

        val di = DI {
            bind<ConversationRepository>() with singleton { mockk(relaxed = true) }
            bind<SharedPreferences>(tag = "settings") with singleton { mockk(relaxed = true) }
        }
        val handler = MessageNetworkHandler(httpClient, localAddr, di)

        handler.sendChatMessage(
            address = InetAddress.getByName("10.0.0.50"),
            time = 123L,
            message = "hello",
            file = null
        )

        assertTrue("Expected network call to execute", latch.await(2, TimeUnit.SECONDS))

        val url = requestSlot.captured.url
        assertEquals("http", url.scheme)
        assertEquals("10.0.0.50", url.host)
        assertEquals("/chat", url.encodedPath)
        assertEquals("hello", url.queryParameter("chatMessage"))
        assertEquals("123", url.queryParameter("time"))
        assertEquals("10.0.0.100", url.queryParameter("senderIp"))
        assertNull(url.queryParameter("incomingfile"))
    }

    @Test
    fun sendChatMessage_includesIncomingFileQuery_whenFileProvided() {
        val requestSlot = io.mockk.slot<Request>()
        val latch = CountDownLatch(1)

        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            latch.countDown()
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }

        val di = DI {
            bind<ConversationRepository>() with singleton { mockk(relaxed = true) }
            bind<SharedPreferences>(tag = "settings") with singleton { mockk(relaxed = true) }
        }
        val handler = MessageNetworkHandler(httpClient, localAddr, di)

        val fileUri = URI.create("https://example.com/file.txt")
        handler.sendChatMessage(
            address = InetAddress.getByName("10.0.0.51"),
            time = 456L,
            message = "with file",
            file = fileUri
        )

        assertTrue("Expected network call to execute", latch.await(2, TimeUnit.SECONDS))
        assertEquals(fileUri.toString(), requestSlot.captured.url.queryParameter("incomingfile"))
    }

    @Test
    fun sendChatMessage_whenExecuteThrows_isHandledWithoutCrash() {
        val requestSlot = io.mockk.slot<Request>()
        val latch = CountDownLatch(1)

        every { httpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            latch.countDown()
            throw RuntimeException("boom")
        }

        val di = DI {
            bind<ConversationRepository>() with singleton { mockk(relaxed = true) }
            bind<SharedPreferences>(tag = "settings") with singleton { mockk(relaxed = true) }
        }
        val handler = MessageNetworkHandler(httpClient, localAddr, di)

        handler.sendChatMessage(
            address = InetAddress.getByName("10.0.0.52"),
            time = 789L,
            message = "error case",
            file = null
        )

        assertTrue("Expected attempted network call", latch.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun handleIncomingMessage_withUser_updatesConversation_andReturnsMappedMessage() {
        val userRepo = mockk<UserRepository>()
        val conversationRepo = mockk<ConversationRepository>(relaxed = true)
        val prefs = mockk<SharedPreferences>()

        val senderIp = InetAddress.getByName("10.10.10.2")
        val senderIpStr = senderIp.hostAddress ?: error("senderIp.hostAddress was null")
        val user = UserEntity(uuid = "remote-1", name = "Alice", address = senderIpStr)

        coEvery { userRepo.getUserByIp(senderIpStr) } returns user
        every { prefs.getString("UUID", null) } returns "local-uuid"
        coEvery {
            conversationRepo.getOrCreateConversation("local-uuid", user)
        } returns Conversation(
            id = ConversationUtils.createConversationId("local-uuid", "remote-1"),
            userUuid = "remote-1",
            userName = "Alice",
            userAddress = senderIpStr,
            lastMessage = null,
            lastMessageTime = 0L
        )

        GlobalApp.GlobalUserRepo.userRepository = userRepo
        GlobalApp.GlobalUserRepo.conversationRepository = conversationRepo
        GlobalApp.GlobalUserRepo.prefs = prefs

        val msg = MessageNetworkHandler.handleIncomingMessage(
            chatMessage = "hi there",
            time = 500L,
            senderIp = senderIp,
            incomingfile = URI.create("file:///tmp/a.txt")
        )

        assertEquals("hi there", msg.content)
        assertEquals("Alice", msg.sender)
        assertEquals(
            ConversationUtils.createConversationId("local-uuid", "remote-1"),
            msg.chat
        )
        assertEquals("file:///tmp/a.txt", msg.file.toString())

        coVerify(exactly = 1) { userRepo.getUserByIp(senderIpStr) }
        coVerify(exactly = 1) { conversationRepo.getOrCreateConversation("local-uuid", user) }
        coVerify(exactly = 1) {
            conversationRepo.updateWithMessage(
                conversationId = ConversationUtils.createConversationId("local-uuid", "remote-1"),
                message = msg
            )
        }
    }

    @Test
    fun handleIncomingMessage_withoutUser_usesUnknownSender_andSkipsConversationUpdate() {
        val userRepo = mockk<UserRepository>()
        val conversationRepo = mockk<ConversationRepository>(relaxed = true)
        val prefs = mockk<SharedPreferences>()

        val senderIp = InetAddress.getByName("10.10.10.3")
        val senderIpStr = senderIp.hostAddress ?: error("senderIp.hostAddress was null")

        coEvery { userRepo.getUserByIp(senderIpStr) } returns null
        every { prefs.getString("UUID", null) } returns "local-uuid"

        GlobalApp.GlobalUserRepo.userRepository = userRepo
        GlobalApp.GlobalUserRepo.conversationRepository = conversationRepo
        GlobalApp.GlobalUserRepo.prefs = prefs

        val msg = MessageNetworkHandler.handleIncomingMessage(
            chatMessage = null,
            time = 501L,
            senderIp = senderIp,
            incomingfile = null
        )

        assertEquals("Error! No message found.", msg.content)
        assertEquals("Unknown", msg.sender)
        assertEquals(
            ConversationUtils.createConversationId("local-uuid", "unknown-$senderIpStr"),
            msg.chat
        )

        coVerify(exactly = 0) { conversationRepo.getOrCreateConversation(any(), any()) }
        coVerify(exactly = 0) { conversationRepo.updateWithMessage(any(), any()) }
    }
}
