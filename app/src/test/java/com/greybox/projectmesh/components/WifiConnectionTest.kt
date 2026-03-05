package com.greybox.projectmesh.components

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field
import com.ustadmobile.meshrabiya.vnet.wifi.WifiConnectConfig

/**
 * JVM-only tests for simple data + status in WifiConnection.kt.
 * No Android/Compose/Mockito required.
 *
 * NOTE: We allocate a dummy WifiConnectConfig via Unsafe and NEVER call its methods.
 * We also avoid ConnectRequest.equals()/hashCode() because that would call
 * WifiConnectConfig.hashCode() internally (which can NPE if fields are null).
 */
class WifiConnectionTest {

    // -----------------------------
    // Enum: stages must exist
    // -----------------------------
    @Test
    fun checkAllStatusesExist() {
        val all = ConnectWifiLauncherStatus.values().toSet()
        assertTrue(ConnectWifiLauncherStatus.INACTIVE in all)
        assertTrue(ConnectWifiLauncherStatus.REQUESTING_PERMISSION in all)
        assertTrue(ConnectWifiLauncherStatus.LOOKING_FOR_NETWORK in all)
        assertTrue(ConnectWifiLauncherStatus.REQUESTING_LINK in all)
        assertEquals(4, all.size)
    }

    // -----------------------------------------------
    // Result model: failure shape must look correct
    // -----------------------------------------------
    @Test
    fun checkFailureResultLooksRight() {
        val error = Exception("expected failure")
        val result = ConnectWifiLauncherResult(
            hotspotConfig = null,
            exception = error,
            isWifiConnected = false
        )
        assertFalse(result.isWifiConnected)
        assertNull(result.hotspotConfig)
        assertNotNull(result.exception)
        assertEquals("expected failure", result.exception?.message)
    }

    // -------------------------------------------------------
    // Result model: data-class copy/equals/hashCode sanity
    // (safe because hotspotConfig = null)
    // -------------------------------------------------------
    @Test
    fun checkResultCopiesAndComparesCorrectly() {
        val first = ConnectWifiLauncherResult(
            hotspotConfig = null,
            exception = Exception("boom"),
            isWifiConnected = false
        )
        val same = first.copy()
        val different = first.copy(exception = Exception("other"))

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, different)
    }

    // =========================================
    // ConnectRequest: JVM tests (no Mockito)
    // Avoid equals()/hashCode() on the whole object.
    // =========================================

    @Test
    fun connectRequest_defaultTimeIsZero() {
        val cfg = unsafeInstance<WifiConnectConfig>()
        val req = ConnectRequest(connectConfig = cfg)
        assertEquals(0L, req.receivedTime)
        assertSame(cfg, req.connectConfig) // same reference
    }

    @Test
    fun connectRequest_customTimePreserved() {
        val cfg = unsafeInstance<WifiConnectConfig>()
        val req = ConnectRequest(receivedTime = 123456789L, connectConfig = cfg)
        assertEquals(123456789L, req.receivedTime)
        assertSame(cfg, req.connectConfig)
    }

    @Test
    fun connectRequest_copyPreservesConfigAndChangesTime() {
        val cfg = unsafeInstance<WifiConnectConfig>()
        val original = ConnectRequest(receivedTime = 100L, connectConfig = cfg)
        val copiedSame = original.copy()
        val copiedChanged = original.copy(receivedTime = 200L)

        // field-wise checks (no equals()/hashCode())
        assertEquals(100L, copiedSame.receivedTime)
        assertSame(cfg, copiedSame.connectConfig)

        assertEquals(200L, copiedChanged.receivedTime)
        assertSame(cfg, copiedChanged.connectConfig)

        // sanity: copies are distinct instances
        assertNotSame(original, copiedSame)
        assertNotSame(original, copiedChanged)
    }

    // -------------------------------------------------------
    // Tiny Unsafe helper for constructor-less allocation
    // -------------------------------------------------------
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> unsafeInstance(): T {
        val unsafe = getUnsafe()
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, T::class.java) as T
    }

    private fun getUnsafe(): Any {
        val clazz = try {
            Class.forName("sun.misc.Unsafe")
        } catch (_: ClassNotFoundException) {
            Class.forName("jdk.internal.misc.Unsafe")
        }
        val f: Field = clazz.getDeclaredField("theUnsafe")
        f.isAccessible = true
        return f.get(null)
    }
}
