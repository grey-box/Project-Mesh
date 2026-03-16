package com.greybox.projectmesh.extension

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ContextExtTest {

    @Test
    fun hasNearbyWifiDevicesOrLocationPermission_usesConfiguredPermissionName() {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val granted = testContext(
            wifiManager = wifiManager,
            packageManager = packageManager,
            permissionResults = mapOf(NEARBY_WIFI_PERMISSION_NAME to PackageManager.PERMISSION_GRANTED)
        )
        val denied = testContext(
            wifiManager = wifiManager,
            packageManager = packageManager,
            permissionResults = mapOf(NEARBY_WIFI_PERMISSION_NAME to PackageManager.PERMISSION_DENIED)
        )

        assertTrue(granted.hasNearbyWifiDevicesOrLocationPermission())
        assertFalse(denied.hasNearbyWifiDevicesOrLocationPermission())
    }

    @Test
    fun hasBluetoothConnectPermission_onApi31Plus_requiresPermission() {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val granted = testContext(
            wifiManager = wifiManager,
            packageManager = packageManager,
            permissionResults = mapOf(Manifest.permission.BLUETOOTH_CONNECT to PackageManager.PERMISSION_GRANTED)
        )
        val denied = testContext(
            wifiManager = wifiManager,
            packageManager = packageManager,
            permissionResults = mapOf(Manifest.permission.BLUETOOTH_CONNECT to PackageManager.PERMISSION_DENIED)
        )

        assertTrue(granted.hasBluetoothConnectPermission())
        assertFalse(denied.hasBluetoothConnectPermission())
    }

    @Test
    @Config(sdk = [29], manifest = Config.NONE)
    fun hasBluetoothConnectPermission_belowApi31_returnsTrue() {
        val context = testContext(
            wifiManager = mockk(relaxed = true),
            packageManager = mockk(relaxed = true),
            permissionResults = emptyMap()
        )

        assertTrue(context.hasBluetoothConnectPermission())
    }

    @Test
    fun hasStaApConcurrency_onApi30Plus_usesWifiManagerCapability() {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        every { wifiManager.isStaApConcurrencySupported } returns true
        val context = testContext(
            wifiManager = wifiManager,
            packageManager = mockk(relaxed = true),
            permissionResults = emptyMap()
        )

        assertTrue(context.hasStaApConcurrency())
    }

    @Test
    @Config(sdk = [29], manifest = Config.NONE)
    fun hasStaApConcurrency_belowApi30_returnsFalse() {
        val context = testContext(
            wifiManager = mockk(relaxed = true),
            packageManager = mockk(relaxed = true),
            permissionResults = emptyMap()
        )

        assertFalse(context.hasStaApConcurrency())
    }

    @Test
    fun deviceInfo_includesExpectedCapabilities() {
        val wifiManager = mockk<WifiManager>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { wifiManager.is5GHzBandSupported } returns true
        every { wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported } returns true
        every { wifiManager.isStaApConcurrencySupported } returns false
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) } returns true
        val context = testContext(
            wifiManager = wifiManager,
            packageManager = packageManager,
            permissionResults = emptyMap()
        )

        val info = context.deviceInfo()

        assertTrue(info.contains("5Ghz supported: true"))
        assertTrue(info.contains("Local-only station concurrency: true"))
        assertTrue(info.contains("Station-AP concurrency: false"))
        assertTrue(info.contains("WifiAware support: true"))
    }

    private fun testContext(
        wifiManager: WifiManager,
        packageManager: PackageManager,
        permissionResults: Map<String, Int>
    ): Context {
        return mockk(relaxed = true) {
            every { getSystemService(WifiManager::class.java) } returns wifiManager
            every { this@mockk.packageManager } returns packageManager
            every { checkPermission(any(), any(), any()) } answers {
                permissionResults[firstArg()] ?: PackageManager.PERMISSION_DENIED
            }
        }
    }
}
