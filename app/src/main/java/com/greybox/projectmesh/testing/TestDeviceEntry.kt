package com.greybox.projectmesh.testing

import android.util.Log
import com.ustadmobile.meshrabiya.log.MNetLogger
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNodeDatagramSocket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Utility class to create test device entries for the mesh network.
 *
 * This simulates a device with a virtual node, logger, and mock network socket,
 * allowing for testing without real devices.
 */
class TestDeviceEntry {
    companion object {
        /** Test logger used for capturing logs during testing */
        private val testLogger = TestMNetLogger()

        /**
         * Creates a simulated test device entry.
         *
         * @return a pair containing the device's integer address and its LastOriginatorMessage
         */
        fun createTestEntry(): Pair<Int, VirtualNode.LastOriginatorMessage> {
            try {
                // Convert the string IP to a byte array
                val testAddressBytes = TestDeviceService.TEST_DEVICE_IP
                    .split(".")
                    .map { it.toInt().toByte() }
                    .toByteArray()

                val testAddress = InetAddress.getByAddress(testAddressBytes)

                // Convert IP address bytes to an Int manually
                val testAddressInt = testAddressBytes.foldIndexed(0) { index, acc, byte ->
                    acc or ((byte.toInt() and 0xFF) shl (24 - (index * 8)))
                }

                Log.d("TestDeviceEntry", "Creating test entry with IP: ${TestDeviceService.TEST_DEVICE_IP}")
                Log.d("TestDeviceEntry", "Test address as int: $testAddressInt")

                // Create a basic MmcpOriginatorMessage
                val mockOriginatorMessage = MmcpOriginatorMessage(
                    messageId = 1,
                    pingTimeSum = 50.toShort(),
                    connectConfig = null,
                    sentTime = System.currentTimeMillis()
                )

                // Create a virtual router for testing
                val testRouter = TestVirtualRouter()

                // Create a mock VirtualNodeDatagramSocket using our test router
                val mockSocket = VirtualNodeDatagramSocket(
                    socket = DatagramSocket(),
                    ioExecutorService = Executors.newSingleThreadExecutor(),
                    router = testRouter,
                    localNodeVirtualAddress = testAddressInt,
                    logger = testLogger
                )

                // Build the LastOriginatorMessage object
                val lastOriginatorMessage = VirtualNode.LastOriginatorMessage(
                    originatorMessage = mockOriginatorMessage,
                    timeReceived = System.currentTimeMillis(),
                    lastHopAddr = testAddressInt,
                    hopCount = 1,
                    lastHopRealInetAddr = testAddress,
                    receivedFromSocket = mockSocket,
                    lastHopRealPort = 4242
                )

                return Pair(testAddressInt, lastOriginatorMessage)
            } catch (e: Exception) {
                Log.e("TestDeviceEntry", "Error creating test entry", e)
                throw e
            }
        }
    }
}
