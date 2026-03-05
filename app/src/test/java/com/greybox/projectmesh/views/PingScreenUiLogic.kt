// File: app/src/test/java/com/greybox/projectmesh/views/PingScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

/**
 * JVM-testable logic model for PingScreen.kt rendering strings.
 * We are NOT touching PingScreen.kt right now.
 *
 * PingScreen mainly renders:
 *  - header text built from uiState.deviceName + uiState.virtualAddress.hostAddress
 *  - per-row text built from pingTimeSum, hopCount, lastHopAddr, messageId
 *
 * Since addressToDotNotation is an external extension, we model formatting via a passed formatter.
 */
private object PingScreenUiLogic {

    data class MmcpOriginatorMessageLike(
        val pingTimeSum: Long,
        val messageId: Long
    )

    data class OriginatorMessageItemLike(
        val originatorMessage: MmcpOriginatorMessageLike,
        val hopCount: Int,
        val lastHopAddr: InetAddress
    )

    fun headerText(deviceName: String?, virtualAddress: InetAddress): String {
        return "Device name: $deviceName, IP address: ${virtualAddress.hostAddress}"
    }

    fun rowText(
        item: OriginatorMessageItemLike,
        lastHopFormatter: (InetAddress) -> String = { it.hostAddress }
    ): String {
        val msg = item.originatorMessage
        return "Ping: ${msg.pingTimeSum}ms, hops: ${item.hopCount}, last hop: ${lastHopFormatter(item.lastHopAddr)}, id: ${msg.messageId}"
    }

    fun renderAllRows(
        items: List<OriginatorMessageItemLike>,
        lastHopFormatter: (InetAddress) -> String = { it.hostAddress }
    ): List<String> = items.map { rowText(it, lastHopFormatter) }
}

class PingScreenUiLogicTest {

    private fun addr(bytes: ByteArray, host: String): InetAddress =
        InetAddress.getByAddress(host, bytes)

    @Test
    fun headerText_includesDeviceName_andVirtualAddressHostAddress() {
        val virtualAddr = addr(byteArrayOf(10, 0, 0, 7), "virtual")
        val header = PingScreenUiLogic.headerText(deviceName = "Pixel", virtualAddress = virtualAddr)

        assertEquals("Device name: Pixel, IP address: 10.0.0.7", header)
    }

    @Test
    fun headerText_whenDeviceNameNull_rendersNullLiteral_matchesKotlinStringInterpolation() {
        val virtualAddr = addr(byteArrayOf(192.toByte(), 168.toByte(), 1, 20), "virtual")
        val header = PingScreenUiLogic.headerText(deviceName = null, virtualAddress = virtualAddr)

        // Kotlin string interpolation of null -> "null"
        assertEquals("Device name: null, IP address: 192.168.1.20", header)
    }

    @Test
    fun rowText_formatsAllFields_andUsesFormatterForLastHop() {
        val lastHop = addr(byteArrayOf(1, 2, 3, 4), "lasthop")
        val item = PingScreenUiLogic.OriginatorMessageItemLike(
            originatorMessage = PingScreenUiLogic.MmcpOriginatorMessageLike(
                pingTimeSum = 123,
                messageId = 99
            ),
            hopCount = 5,
            lastHopAddr = lastHop
        )

        val row = PingScreenUiLogic.rowText(item) { inet -> "DOT(${inet.hostAddress})" }

        assertEquals(
            "Ping: 123ms, hops: 5, last hop: DOT(1.2.3.4), id: 99",
            row
        )
    }

    @Test
    fun renderAllRows_preservesItemOrder() {
        val a1 = addr(byteArrayOf(10, 0, 0, 1), "a1")
        val a2 = addr(byteArrayOf(10, 0, 0, 2), "a2")

        val items = listOf(
            PingScreenUiLogic.OriginatorMessageItemLike(
                originatorMessage = PingScreenUiLogic.MmcpOriginatorMessageLike(10, 1),
                hopCount = 1,
                lastHopAddr = a1
            ),
            PingScreenUiLogic.OriginatorMessageItemLike(
                originatorMessage = PingScreenUiLogic.MmcpOriginatorMessageLike(20, 2),
                hopCount = 2,
                lastHopAddr = a2
            )
        )

        val rows = PingScreenUiLogic.renderAllRows(items) { it.hostAddress }

        assertEquals(
            listOf(
                "Ping: 10ms, hops: 1, last hop: 10.0.0.1, id: 1",
                "Ping: 20ms, hops: 2, last hop: 10.0.0.2, id: 2"
            ),
            rows
        )
    }

    @Test
    fun rowText_handlesZeroAndLargeValues() {
        val lastHop = addr(byteArrayOf(8, 8, 8, 8), "dns")
        val item = PingScreenUiLogic.OriginatorMessageItemLike(
            originatorMessage = PingScreenUiLogic.MmcpOriginatorMessageLike(
                pingTimeSum = 0,
                messageId = Long.MAX_VALUE
            ),
            hopCount = 0,
            lastHopAddr = lastHop
        )

        val row = PingScreenUiLogic.rowText(item)

        assertTrue(row.contains("Ping: 0ms"))
        assertTrue(row.contains("hops: 0"))
        assertTrue(row.contains("last hop: 8.8.8.8"))
        assertTrue(row.contains("id: ${Long.MAX_VALUE}"))
    }
}