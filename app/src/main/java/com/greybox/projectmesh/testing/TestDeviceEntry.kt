package com.greybox.projectmesh.testing

import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNodeDatagramSocket
import timber.log.Timber
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class TestDeviceEntry {
    companion object {
        // Create a test logger
        private val testLogger = TestMNetLogger()

        fun createTestEntry(): Pair<Int, VirtualNode.LastOriginatorMessage> {
            try {
                //convert string IP to bytes
                val testAddressBytes = TestDeviceService.TEST_DEVICE_IP
                    .split(".")
                    .map { it.toInt().toByte() }
                    .toByteArray()

                val testAddress = InetAddress.getByAddress(testAddressBytes)

                // Convert IP address to Int manually
                val testAddressInt = testAddressBytes.foldIndexed(0) { index, acc, byte ->
                    acc or ((byte.toInt() and 0xFF) shl (24 - (index * 8)))
                }

                Timber.tag("TestDeviceEntry").d("Creating test entry with IP: ${TestDeviceService
                    .TEST_DEVICE_IP}")
                Timber.tag("TestDeviceEntry").d("Test address as int: $testAddressInt")


                //create basic MmcpOriginatorMessage
                val mockOriginatorMessage = MmcpOriginatorMessage(
                    messageId = 1,
                    pingTimeSum = 50.toShort(),
                    connectConfig = null,
                    sentTime = System.currentTimeMillis()
                )

                //create a virtual router for testing
                val testRouter = TestVirtualRouter()

                //create a mock VirtualNodeDatagramSocket with our test router
                val mockSocket = VirtualNodeDatagramSocket(
                    socket = DatagramSocket(),
                    ioExecutorService = Executors.newSingleThreadExecutor(),
                    router = testRouter,
                    localNodeVirtualAddress = testAddressInt,
                    logger = testLogger
                )

                // Create LastOriginatorMessage with all required parameters
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
                Timber.tag("TestDeviceEntry").e(e,"Error creating test entry")
                throw e
            }
        }
    }
}