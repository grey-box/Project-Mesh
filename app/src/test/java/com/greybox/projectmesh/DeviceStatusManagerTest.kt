package com.greybox.projectmesh

import com.greybox.projectmesh.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DeviceStatusManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Before
    fun setUp() {
        DeviceStatusManager.clearAllStatuses()
        clearPrivateMutableMap("failureCountMap")

        mockkObject(DeviceStatusManager)
        every { DeviceStatusManager.updateDeviceStatus(any(), any(), any()) } answers { callOriginal() }
        every { DeviceStatusManager.isDeviceOnline(any()) } answers { callOriginal() }
        every { DeviceStatusManager.getOnlineDevices() } answers { callOriginal() }
        every { DeviceStatusManager.clearAllStatuses() } answers { callOriginal() }
        every { DeviceStatusManager.handleNetworkDisconnect(any()) } answers { callOriginal() }

        every { DeviceStatusManager.verifyDeviceStatus(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceStatusManager)
        clearAllMocks()

        DeviceStatusManager.clearAllStatuses()
        clearPrivateMutableMap("failureCountMap")
        clearPrivateMutableMap("lastCheckedTimes")
    }

    @Test
    fun updateDeviceStatus_specialOnlineDevice_alwaysForcesOnlineAndSkipsVerify() = runTest {
        val ip = "192.168.0.99"

        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = false, verified = false)

        assertTrue(DeviceStatusManager.deviceStatusMap.value[ip] == true)
        verify(exactly = 0) { DeviceStatusManager.verifyDeviceStatus(any()) }
    }

    @Test
    fun updateDeviceStatus_specialOfflineDevice_alwaysForcesOfflineAndSkipsVerify() = runTest {
        val ip = "192.168.0.98"

        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = true, verified = true)

        assertTrue(DeviceStatusManager.deviceStatusMap.value[ip] == false)
        verify(exactly = 0) { DeviceStatusManager.verifyDeviceStatus(any()) }
    }

    @Test
    fun updateDeviceStatus_verifiedUpdate_setsStatusAndUpdatesLastCheckedTimes() = runTest {
        val ip = "10.0.0.55"

        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = true, verified = true)

        assertTrue(DeviceStatusManager.deviceStatusMap.value[ip] == true)

        val lastChecked = getPrivateMutableMap<String, Long>("lastCheckedTimes")[ip]
        assertNotNull(lastChecked)
        assertTrue(lastChecked!! > 0L)
    }

    @Test
    fun updateDeviceStatus_unverifiedOnlineFromUnknown_setsOnlineAndTriggersVerify() = runTest {
        val ip = "10.0.0.77"

        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = true, verified = false)

        assertTrue(DeviceStatusManager.deviceStatusMap.value[ip] == true)
        verify(exactly = 1) { DeviceStatusManager.verifyDeviceStatus(ip) }
    }

    @Test
    fun updateDeviceStatus_unverifiedOffline_triggersVerify() = runTest {
        val ip = "10.0.0.88"

        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = false, verified = false)

        verify(exactly = 1) { DeviceStatusManager.verifyDeviceStatus(ip) }
    }

    @Test
    fun isDeviceOnline_whenOnlineAndStale_triggersVerifyAndReturnsTrue() = runTest {
        val ip = "10.0.0.99"

        // mark online
        DeviceStatusManager.updateDeviceStatus(ipAddress = ip, isOnline = true, verified = true)

        // force "stale" last-checked time so isDeviceOnline will verify again
        val lastCheckedTimes = getPrivateMutableMap<String, Long>("lastCheckedTimes")
        lastCheckedTimes[ip] = 0L

        val online = DeviceStatusManager.isDeviceOnline(ip)

        assertTrue(online)
        verify(exactly = 1) { DeviceStatusManager.verifyDeviceStatus(ip) }
    }

    @Test
    fun getOnlineDevices_returnsOnlyIpsMarkedTrue() = runTest {
        DeviceStatusManager.updateDeviceStatus("10.0.0.1", true, verified = true)
        DeviceStatusManager.updateDeviceStatus("10.0.0.2", false, verified = true)
        DeviceStatusManager.updateDeviceStatus("10.0.0.3", true, verified = true)

        val online = DeviceStatusManager.getOnlineDevices()

        assertEquals(setOf("10.0.0.1", "10.0.0.3"), online.toSet())
        assertFalse(online.contains("10.0.0.2"))
    }

    private fun clearPrivateMutableMap(fieldName: String) {
        val map = getPrivateMutableMap<Any, Any>(fieldName)
        map.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V> getPrivateMutableMap(fieldName: String): MutableMap<K, V> {
        val clazz = DeviceStatusManager::class.java
        val field = clazz.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(null) as MutableMap<K, V>
    }
}