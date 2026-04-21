package com.greybox.projectmesh.testing

import android.util.Log
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class TestDeviceEntryTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.println(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        clearAllMocks()
    }

    @Test
    fun createTestEntry_buildsExpectedAddressAndOriginatorMetadata() {
        val (addressInt, originator) = TestDeviceEntry.createTestEntry()

        try {
            assertEquals(TestDeviceService.TEST_DEVICE_IP, originator.lastHopRealInetAddr.hostAddress)
            assertEquals(addressInt, originator.lastHopAddr)
            assertEquals(50.toShort(), originator.originatorMessage.pingTimeSum)
            assertEquals(1.toByte(), originator.hopCount)
            assertEquals(4242, originator.lastHopRealPort)
            assertTrue(originator.timeReceived > 0L)
        } finally {
            originator.receivedFromSocket.close(true)
        }
    }

    @Test
    fun createTestEntry_returnsSocketBoundToLocalPort() {
        val (_, originator) = TestDeviceEntry.createTestEntry()

        try {
            assertTrue(originator.receivedFromSocket.localPort > 0)
            assertEquals(TestDeviceService.TEST_DEVICE_IP, originator.lastHopRealInetAddr.hostAddress)
        } finally {
            originator.receivedFromSocket.close(true)
        }
    }
}
