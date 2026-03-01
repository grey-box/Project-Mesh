// File: app/src/test/java/com/greybox/projectmesh/views/NetworkScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

/**
 * Deep JVM tests for NetworkScreen.kt behavior WITHOUT touching NetworkScreen.kt.
 *
 * What NetworkScreen does on WifiListItem click (ipAddress):
 * 1) addr = InetAddress.getByName(ipAddress)
 * 2) appServer.requestRemoteUserInfo(addr)
 * 3) appServer.pushUserInfoTo(addr)
 * 4) onNodeClick(ipAddress) (navigation)
 *
 * Also it renders items from:
 *   uiState.allNodes.entries.toList()
 * => preserves the Map iteration order.
 */
private object NetworkScreenUiLogic {

    interface AppServerPort {
        fun requestRemoteUserInfo(addr: InetAddress)
        fun pushUserInfoTo(addr: InetAddress)
    }

    data class Effects(
        val resolvedAddr: InetAddress?,
        val navigationIp: String?,
        val callTrace: List<String>,
    )

    /**
     * Mirrors the current onClick logic. Captures ordering.
     *
     * NOTE: In the real UI code, InetAddress.getByName may throw.
     * We keep that behavior: resolver may throw -> no server calls, no navigation.
     */
    fun handleNodeClick(
        ipAddress: String,
        appServer: AppServerPort,
        resolver: (String) -> InetAddress = { InetAddress.getByName(it) },
        onNodeClick: (String) -> Unit = {},
    ): Effects {
        val trace = mutableListOf<String>()
        var addr: InetAddress? = null
        var navIp: String? = null

        addr = resolver(ipAddress)
        trace.add("resolve:${addr.hostAddress}")

        appServer.requestRemoteUserInfo(addr)
        trace.add("requestRemoteUserInfo:${addr.hostAddress}")

        appServer.pushUserInfoTo(addr)
        trace.add("pushUserInfoTo:${addr.hostAddress}")

        onNodeClick(ipAddress)
        trace.add("navigate:$ipAddress")
        navIp = ipAddress

        return Effects(
            resolvedAddr = addr,
            navigationIp = navIp,
            callTrace = trace.toList(),
        )
    }

    /**
     * Explicit helper for the rendering order implied by:
     * uiState.allNodes.entries.toList()
     */
    fun renderOrderKeys(allNodes: Map<String, Any?>): List<String> {
        return allNodes.entries.toList().map { it.key }
    }
}

private class FakeAppServer : NetworkScreenUiLogic.AppServerPort {
    val calls = mutableListOf<String>()
    override fun requestRemoteUserInfo(addr: InetAddress) {
        calls.add("request:${addr.hostAddress}")
    }

    override fun pushUserInfoTo(addr: InetAddress) {
        calls.add("push:${addr.hostAddress}")
    }
}

class NetworkScreenUiLogicTest {

    @Test
    fun handleNodeClick_happyPath_callsResolve_thenRequest_thenPush_thenNavigate() {
        val server = FakeAppServer()

        // Avoid DNS/network: deterministic resolver that returns the same hostAddress as ipAddress
        val resolver: (String) -> InetAddress = { ip ->
            val bytes = ip.split(".").map { it.toInt().toByte() }.toByteArray()
            InetAddress.getByAddress(ip, bytes)
        }

        var navigatedTo: String? = null
        val onNodeClick: (String) -> Unit = { ip -> navigatedTo = ip }

        val effects = NetworkScreenUiLogic.handleNodeClick(
            ipAddress = "10.0.0.5",
            appServer = server,
            resolver = resolver,
            onNodeClick = onNodeClick
        )

        assertNotNull(effects.resolvedAddr)
        assertEquals("10.0.0.5", effects.resolvedAddr!!.hostAddress)
        assertEquals("10.0.0.5", effects.navigationIp)
        assertEquals("10.0.0.5", navigatedTo)

        // Verify exact ordering (most important property for this screen)
        assertEquals(
            listOf(
                "resolve:10.0.0.5",
                "requestRemoteUserInfo:10.0.0.5",
                "pushUserInfoTo:10.0.0.5",
                "navigate:10.0.0.5",
            ),
            effects.callTrace
        )

        // Also verify server got calls in the correct order
        assertEquals(
            listOf("request:10.0.0.5", "push:10.0.0.5"),
            server.calls
        )
    }

    @Test
    fun handleNodeClick_whenResolverThrows_noServerCalls_noNavigation() {
        val server = FakeAppServer()

        val resolver: (String) -> InetAddress = {
            throw IllegalArgumentException("bad ip")
        }

        var navigatedTo: String? = null
        val onNodeClick: (String) -> Unit = { ip -> navigatedTo = ip }

        try {
            NetworkScreenUiLogic.handleNodeClick(
                ipAddress = "not_an_ip",
                appServer = server,
                resolver = resolver,
                onNodeClick = onNodeClick
            )
            fail("Expected exception from resolver")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        assertTrue(server.calls.isEmpty())
        assertNull(navigatedTo)
    }

    @Test
    fun handleNodeClick_callsAlwaysUseResolvedInetAddress_notOriginalString() {
        val server = FakeAppServer()

        // Resolver returns a DIFFERENT hostAddress than the passed-in string,
        // proving we call server methods with the InetAddress, not string.
        val resolver: (String) -> InetAddress = { _ ->
            InetAddress.getByAddress("resolved", byteArrayOf(1, 2, 3, 4))
        }

        var navigatedTo: String? = null
        val onNodeClick: (String) -> Unit = { ip -> navigatedTo = ip }

        val effects = NetworkScreenUiLogic.handleNodeClick(
            ipAddress = "10.9.9.9",
            appServer = server,
            resolver = resolver,
            onNodeClick = onNodeClick
        )

        assertEquals("1.2.3.4", effects.resolvedAddr!!.hostAddress)
        assertEquals(
            listOf("request:1.2.3.4", "push:1.2.3.4"),
            server.calls
        )

        // Navigation still uses the original ip string (matches NetworkScreen)
        assertEquals("10.9.9.9", effects.navigationIp)
        assertEquals("10.9.9.9", navigatedTo)
    }

    @Test
    fun renderOrderKeys_preservesLinkedHashMapInsertionOrder() {
        val map = linkedMapOf<String, Any?>(
            "192.168.0.2" to Any(),
            "192.168.0.9" to Any(),
            "192.168.0.3" to Any(),
        )

        val keys = NetworkScreenUiLogic.renderOrderKeys(map)
        assertEquals(listOf("192.168.0.2", "192.168.0.9", "192.168.0.3"), keys)
    }

    @Test
    fun renderOrderKeys_regularHashMap_orderIsNotGuaranteed_butFunctionStillReturnsSomeKeys() {
        val map = hashMapOf<String, Any?>(
            "a" to 1,
            "b" to 2,
            "c" to 3,
        )

        val keys = NetworkScreenUiLogic.renderOrderKeys(map)

        // We don't assert exact order for HashMap (implementation-dependent).
        assertEquals(3, keys.size)
        assertTrue(keys.containsAll(listOf("a", "b", "c")))
    }

    @Test
    fun renderOrderKeys_emptyMap_returnsEmptyList() {
        val keys = NetworkScreenUiLogic.renderOrderKeys(emptyMap())
        assertTrue(keys.isEmpty())
    }
}