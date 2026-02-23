package com.greybox.projectmesh.testing

import com.ustadmobile.meshrabiya.vnet.Protocol
import com.ustadmobile.meshrabiya.vnet.VirtualPacket
import com.ustadmobile.meshrabiya.vnet.VirtualRouter
import com.ustadmobile.meshrabiya.vnet.socket.ChainSocketNextHop
import java.net.DatagramPacket
import java.net.InetAddress

/**
 * Test implementation of a VirtualRouter for use with test devices.
 *
 * Provides dummy routing behavior and predictable network parameters for testing purposes.
 */
class TestVirtualRouter : VirtualRouter {

    /** Fixed test device address */
    override val address: InetAddress = InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP)

    /** Fixed port for local datagram operations */
    override val localDatagramPort: Int = 4242

    /** Fixed network prefix length for testing */
    override val networkPrefixLength: Int = 16

    /**
     * Route a packet.
     *
     * No-op implementation for test purposes.
     */
    override fun route(
        packet: VirtualPacket,
        datagramPacket: DatagramPacket?,
        virtualNodeDatagramSocket: com.ustadmobile.meshrabiya.vnet.VirtualNodeDatagramSocket?
    ) {
        // no-op for test implementation
    }

    /**
     * Allocate a UDP port or throw exception if unavailable.
     *
     * Always returns the requested port number in the test implementation.
     */
    override fun allocateUdpPortOrThrow(
        virtualDatagramSocketImpl: com.ustadmobile.meshrabiya.vnet.datagram.VirtualDatagramSocketImpl,
        portNum: Int
    ): Int {
        return portNum
    }

    /**
     * Deallocate a port.
     *
     * No-op implementation for test purposes.
     */
    override fun deallocatePort(protocol: Protocol, portNum: Int) {
        // no-op for test implementation
    }

    /**
     * Look up the next hop for a chain socket.
     *
     * Returns a dummy next hop with isFinalDest = true for testing.
     */
    override fun lookupNextHopForChainSocket(address: InetAddress, port: Int): ChainSocketNextHop {
        return ChainSocketNextHop(
            address = address,
            port = port,
            isFinalDest = true,
            network = null // network is null for test purposes
        )
    }

    /** Returns a constant MMCP message ID for testing */
    override fun nextMmcpMessageId(): Int = 1
}
