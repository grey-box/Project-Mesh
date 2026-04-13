package com.greybox.projectmesh.extension

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ustadmobile.meshrabiya.MeshrabiyaConstants

/*
context is a class that provides access to application-specific resources and classes.
This File contains several context related extension functions that will use in this app.
*/

/**
 * The permission string required for accessing nearby Wi-Fi devices.
 *
 * On Android 13+ (SDK 33+), uses [Manifest.permission.NEARBY_WIFI_DEVICES].
 * On earlier versions, falls back to [Manifest.permission.ACCESS_FINE_LOCATION].
 */
val NEARBY_WIFI_PERMISSION_NAME = if(Build.VERSION.SDK_INT >= 33){
    Manifest.permission.NEARBY_WIFI_DEVICES
}else {
    Manifest.permission.ACCESS_FINE_LOCATION
}

/**
 * Checks whether the app has permission to access nearby Wi-Fi devices (Android 13+)
 * or fine location (pre-Android 13).
 *
 * @receiver The [Context] used to check permissions.
 * @return `true` if the required permission is granted, `false` otherwise.
 */
fun Context.hasNearbyWifiDevicesOrLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this, NEARBY_WIFI_PERMISSION_NAME
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Checks whether the app has permission to connect to Bluetooth devices.
 *
 * On Android 12+ (SDK 31+), uses [Manifest.permission.BLUETOOTH_CONNECT].
 * On earlier versions, always returns `true`.
 *
 * @receiver The [Context] used to check permissions.
 * @return `true` if the permission is granted or not required, `false` otherwise.
 */
fun Context.hasBluetoothConnectPermission(): Boolean {
    return if(Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

/**
 * Provides a [DataStore] instance named "meshr_settings" for storing persistent
 * network-related preferences used by Meshrabiya.
 */
val Context.networkDataStore: DataStore<Preferences> by preferencesDataStore(name = "meshr_settings")

/**
 * Checks if the device supports Wi-Fi STA/AP concurrency (simultaneous station and access point mode).
 *
 * Requires Android 11+ (SDK 30+).
 *
 * @receiver The [Context] used to access [WifiManager].
 * @return `true` if STA/AP concurrency is supported, `false` otherwise.
 */
fun Context.hasStaApConcurrency(): Boolean {
    return Build.VERSION.SDK_INT >= 30 && getSystemService(WifiManager::class.java).isStaApConcurrencySupported
}

/**
 * Returns a detailed string describing the device and Wi-Fi capabilities.
 *
 * Includes Meshrabiya version, Android version, device manufacturer/model,
 * 5GHz support, local-only station concurrency, STA/AP concurrency, and Wi-Fi Aware support.
 *
 * @receiver The [Context] used to access system services and package manager.
 * @return A formatted [String] describing the device and Wi-Fi features.
 */
fun Context.deviceInfo(): String {
    val wifiManager = getSystemService(WifiManager::class.java)
    val hasStaConcurrency = Build.VERSION.SDK_INT >= 31 &&
            wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported
    val hasStaApConcurrency = Build.VERSION.SDK_INT >= 30 &&
            wifiManager.isStaApConcurrencySupported
    val hasWifiAwareSupport = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)

    return buildString {
        append("Meshrabiya: Version :${MeshrabiyaConstants.VERSION}\n")
        append("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        append("Device: ${Build.MANUFACTURER} - ${Build.MODEL}\n")
        append("5Ghz supported: ${wifiManager.is5GHzBandSupported}\n")
        append("Local-only station concurrency: $hasStaConcurrency\n")
        append("Station-AP concurrency: $hasStaApConcurrency\n")
        append("WifiAware support: $hasWifiAwareSupport\n")
    }
}
