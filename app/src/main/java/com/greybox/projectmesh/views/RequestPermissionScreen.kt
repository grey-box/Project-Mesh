package com.greybox.projectmesh.views

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestPermissionsScreen(skipPermissions: Boolean) {
    val context = LocalContext.current

    // Track permission states
    var currentStep by remember { mutableIntStateOf(if (skipPermissions) 6 else 0) }

    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Move to next permission
        currentStep = 1
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Move to next permission
        currentStep = 2
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Move to next permission
        currentStep = 3
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Move to next step permission
        currentStep = 4
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Move to next step (Battery Optimization)
        currentStep = 5
    }

    LaunchedEffect(currentStep) {
        if (currentStep == 6) return@LaunchedEffect
        when (currentStep) {
            0 -> { // Request Nearby Wi-Fi Permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !hasPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)) {
                    nearbyWifiPermissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else {
                    currentStep = 1
                }
            }
            1 -> { // Request Location Permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    currentStep = 2
                }
            }
            2 -> { // Request Notification Permission (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    currentStep = 3
                }
            }
            3 -> { // Request Storage Permission (Android 13+ has different permissions)
                val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                if (!hasAnyPermission(context, storagePermissions)) {
                    storagePermissionLauncher.launch(storagePermissions)
                } else {
                    currentStep = 4
                }
            }
            4 -> { // Request Camera Permission
                if (!hasPermission(context, Manifest.permission.CAMERA)) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    currentStep = 5
                }
            }
            5 -> { // Handle Battery Optimization
                if (!isBatteryOptimizationDisabled(context)) {
                    promptDisableBatteryOptimization(context)
                }
            }
        }
    }
}

/** Function to Check If Permission is Granted */
fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Function to Check If Any Permission from a List is Granted */
fun hasAnyPermission(context: Context, permissions: Array<String>): Boolean {
    return permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

/** Function to Check If Battery Optimization is Disabled */
fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true // Battery optimization doesn't apply below Android 6.0
    }
}

/** Function to Prompt User to Disable Battery Optimization */
fun promptDisableBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val message = SpannableString(
            "To ensure uninterrupted background functionality and maintain a stable connection, " +
                    "please disable battery optimization for this app.\n\n" +
                    "1. Tap Open Settings below\n\n" +
                    "2. Tap the dropdown menu at top of page and select All Apps\n\n" +
                    "3. Scroll to Project Mesh and tap to turn off battery optimization.\n"
        )

        fun boldify(text: String) {
            val start = message.indexOf(text)
            if (start >= 0) {
                message.setSpan(
                    StyleSpan(Typeface.BOLD),  // Apply bold
                    start,
                    start + text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // Bold specific words
        boldify("Open Settings")
        boldify("dropdown")
        boldify("All Apps")
        boldify("turn off battery optimization")

        AlertDialog.Builder(context)
            .setTitle("Disable Battery Optimization")
            .setMessage(message)
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                try {
                    // Navigate to Battery Optimization Settings
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to App Info screen
                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    context.startActivity(appSettingsIntent)
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}