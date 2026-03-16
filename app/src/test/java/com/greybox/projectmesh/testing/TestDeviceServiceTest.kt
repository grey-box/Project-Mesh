package com.greybox.projectmesh.testing

import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TestDeviceServiceTest {

    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        everyLog()
        userRepository = mockk(relaxed = true)
        GlobalApp.GlobalUserRepo.userRepository = userRepository
        setFlag("isInitialized", false)
        setFlag("offlineDeviceInitialized", false)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        clearAllMocks()
        setFlag("isInitialized", false)
        setFlag("offlineDeviceInitialized", false)
    }

    @Test
    fun initialize_whenUsersDoNotExist_insertsOnlineAndOfflineTestUsers() {
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP) } returns null
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP_OFFLINE) } returns null

        TestDeviceService.initialize()

        coVerify(exactly = 1) {
            userRepository.insertOrUpdateUser(
                "temp-${TestDeviceService.TEST_DEVICE_IP}",
                TestDeviceService.TEST_DEVICE_NAME,
                TestDeviceService.TEST_DEVICE_IP
            )
        }
        coVerify(exactly = 1) {
            userRepository.insertOrUpdateUser(
                "temp-offline-${TestDeviceService.TEST_DEVICE_IP_OFFLINE}",
                TestDeviceService.TEST_DEVICE_NAME_OFFLINE,
                null
            )
        }
    }

    @Test
    fun initialize_whenUsersExist_updatesExistingRecords() {
        val online = UserEntity("existing-online", "Old Online", TestDeviceService.TEST_DEVICE_IP)
        val offline = UserEntity("existing-offline", "Old Offline", "10.0.0.2")
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP) } returns online
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP_OFFLINE) } returns offline

        TestDeviceService.initialize()

        coVerify(exactly = 1) {
            userRepository.insertOrUpdateUser(
                "existing-online",
                TestDeviceService.TEST_DEVICE_NAME,
                TestDeviceService.TEST_DEVICE_IP
            )
        }
        coVerify(exactly = 1) {
            userRepository.insertOrUpdateUser(
                "existing-offline",
                TestDeviceService.TEST_DEVICE_NAME_OFFLINE,
                null
            )
        }
    }

    @Test
    fun initialize_calledTwice_isIdempotent() {
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP) } returns null
        coEvery { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP_OFFLINE) } returns null

        TestDeviceService.initialize()
        TestDeviceService.initialize()

        coVerify(exactly = 1) { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP) }
        coVerify(exactly = 1) { userRepository.getUserByIp(TestDeviceService.TEST_DEVICE_IP_OFFLINE) }
    }

    @Test
    fun helperMethods_identifyAddressesAndReturnExpectedIp() {
        val online = InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP)
        val offline = InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP_OFFLINE)

        assertTrue(TestDeviceService.isOnlineTestDevice(online))
        assertTrue(TestDeviceService.isOfflineTestDevice(offline))
        assertTrue(TestDeviceService.isTestDevice(online))
        assertFalse(TestDeviceService.isTestDevice(offline))
        assertEquals(TestDeviceService.TEST_DEVICE_IP, TestDeviceService.getTestDeviceAddress().hostAddress)
    }

    @Test
    fun createEchoResponse_prefixesContentAndPreservesChat() {
        val original = Message(
            id = 5,
            dateReceived = 100L,
            content = "hello",
            sender = "Me",
            chat = "chat-1"
        )

        val echo = TestDeviceService.createEchoResponse(original)

        assertEquals("Echo: hello", echo.content)
        assertEquals(TestDeviceService.TEST_DEVICE_NAME, echo.sender)
        assertEquals("chat-1", echo.chat)
        assertEquals(0, echo.id)
    }

    private fun setFlag(fieldName: String, value: Boolean) {
        val outerField = TestDeviceService::class.java.declaredFields.firstOrNull { it.name == fieldName }
        if (outerField != null) {
            outerField.isAccessible = true
            outerField.setBoolean(null, value)
            return
        }

        val companionField = TestDeviceService.Companion::class.java.getDeclaredField(fieldName)
        companionField.isAccessible = true
        companionField.setBoolean(TestDeviceService.Companion, value)
    }

    private fun everyLog() {
        io.mockk.every { Log.d(any(), any()) } returns 0
        io.mockk.every { Log.e(any(), any()) } returns 0
        io.mockk.every { Log.e(any(), any(), any()) } returns 0
    }
}
