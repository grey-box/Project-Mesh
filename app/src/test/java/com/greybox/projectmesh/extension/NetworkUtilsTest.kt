package com.greybox.projectmesh.extension

import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

class NetworkUtilsTest {

    @Test
    fun getLocalIpFromDI_returnsHostAddressFromAndroidVirtualNode() {
        // Arrange
        val mockAddress = mockk<InetAddress>()
        every { mockAddress.hostAddress } returns "192.168.1.100"

        val mockNode = mockk<AndroidVirtualNode>()
        every { mockNode.address } returns mockAddress

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { mockNode }
        }

        // Act
        val result = getLocalIpFromDI(di)

        // Assert
        assertEquals("192.168.1.100", result)
    }
}