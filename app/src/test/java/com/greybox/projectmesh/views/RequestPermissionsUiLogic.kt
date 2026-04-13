// File: app/src/test/java/com/greybox/projectmesh/views/RequestPermissionsUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for RequestPermissionsScreen.kt WITHOUT touching that file.
 *
 * The Composable uses Android framework + rememberLauncherForActivityResult, which are instrumentation-only.
 * For JVM tests, we model the deterministic step-machine decisions:
 * - Given SDK level + granted permissions + battery optimization state,
 *   what action should happen at each step?
 *
 * Later in androidTest, you'll verify actual permission launchers and dialogs.
 */
private object RequestPermissionsUiLogic {

    // Mirror steps from the Composable
    const val STEP_NEARBY_WIFI = 0
    const val STEP_LOCATION = 1
    const val STEP_NOTIFICATIONS = 2
    const val STEP_STORAGE = 3
    const val STEP_CAMERA = 4
    const val STEP_BATTERY = 5
    const val STEP_DONE = 6

    /**
     * We model Android version boundaries used in the file:
     * - M = 23 (permission runtime checks)
     * - TIRAMISU = 33 (POST_NOTIFICATIONS + READ_MEDIA_*)
     */
    const val SDK_M = 23
    const val SDK_TIRAMISU = 33

    // Permission names (strings only; no Android dependency in unit tests)
    const val PERM_NEARBY_WIFI = "android.permission.NEARBY_WIFI_DEVICES"
    const val PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val PERM_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val PERM_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val PERM_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val PERM_READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val PERM_CAMERA = "android.permission.CAMERA"

    sealed class Action {
        data class LaunchSingle(val permission: String, val nextStepOnResult: Int) : Action()
        data class LaunchMultiple(val permissions: Array<String>, val nextStepOnResult: Int) : Action()
        data class AdvanceTo(val nextStep: Int) : Action()
        object PromptBatteryOptimization : Action()
        object NoOp : Action() // step==DONE
    }

    /**
     * Computes the next action that LaunchedEffect would take at a given step.
     *
     * Inputs:
     * - sdkInt: device SDK
     * - granted: set of granted permissions
     * - batteryOptimizationDisabled: whether optimization is already disabled (ignoring optimizations)
     */
    fun nextAction(
        currentStep: Int,
        sdkInt: Int,
        granted: Set<String>,
        batteryOptimizationDisabled: Boolean
    ): Action {

        if (currentStep == STEP_DONE) return Action.NoOp

        fun has(p: String) = granted.contains(p)
        fun hasAny(ps: Array<String>) = ps.any { has(it) }

        return when (currentStep) {
            STEP_NEARBY_WIFI -> {
                if (sdkInt >= SDK_M && !has(PERM_NEARBY_WIFI)) {
                    Action.LaunchSingle(PERM_NEARBY_WIFI, nextStepOnResult = STEP_LOCATION)
                } else {
                    Action.AdvanceTo(STEP_LOCATION)
                }
            }

            STEP_LOCATION -> {
                if (sdkInt >= SDK_M && !has(PERM_FINE_LOCATION)) {
                    Action.LaunchSingle(PERM_FINE_LOCATION, nextStepOnResult = STEP_NOTIFICATIONS)
                } else {
                    Action.AdvanceTo(STEP_NOTIFICATIONS)
                }
            }

            STEP_NOTIFICATIONS -> {
                if (sdkInt >= SDK_TIRAMISU && !has(PERM_POST_NOTIFICATIONS)) {
                    Action.LaunchSingle(PERM_POST_NOTIFICATIONS, nextStepOnResult = STEP_STORAGE)
                } else {
                    Action.AdvanceTo(STEP_STORAGE)
                }
            }

            STEP_STORAGE -> {
                val storagePerms = if (sdkInt >= SDK_TIRAMISU) {
                    arrayOf(PERM_READ_MEDIA_IMAGES, PERM_READ_MEDIA_VIDEO)
                } else {
                    arrayOf(PERM_READ_EXTERNAL_STORAGE)
                }

                if (!hasAny(storagePerms)) {
                    Action.LaunchMultiple(storagePerms, nextStepOnResult = STEP_CAMERA)
                } else {
                    Action.AdvanceTo(STEP_CAMERA)
                }
            }

            STEP_CAMERA -> {
                if (!has(PERM_CAMERA)) {
                    Action.LaunchSingle(PERM_CAMERA, nextStepOnResult = STEP_BATTERY)
                } else {
                    Action.AdvanceTo(STEP_BATTERY)
                }
            }

            STEP_BATTERY -> {
                if (!batteryOptimizationDisabled) Action.PromptBatteryOptimization
                else Action.NoOp // real code does nothing else here
            }

            else -> throw IllegalArgumentException("Unknown step: $currentStep")
        }
    }

    fun initialStep(skipPermissions: Boolean): Int = if (skipPermissions) STEP_DONE else STEP_NEARBY_WIFI
}

class RequestPermissionsUiLogicTest {

    // ---------- initial step ----------
    @Test
    fun initialStep_whenSkip_true_isDone() {
        assertEquals(RequestPermissionsUiLogic.STEP_DONE, RequestPermissionsUiLogic.initialStep(true))
    }

    @Test
    fun initialStep_whenSkip_false_isNearbyWifi() {
        assertEquals(RequestPermissionsUiLogic.STEP_NEARBY_WIFI, RequestPermissionsUiLogic.initialStep(false))
    }

    // ---------- step 0: nearby wifi ----------
    @Test
    fun step0_sdkBelowM_advancesWithoutRequest() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NEARBY_WIFI,
            sdkInt = 22,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_LOCATION), action)
    }

    @Test
    fun step0_sdkAtLeastM_andNotGranted_requestsNearbyWifi() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NEARBY_WIFI,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(
            RequestPermissionsUiLogic.Action.LaunchSingle(
                RequestPermissionsUiLogic.PERM_NEARBY_WIFI,
                nextStepOnResult = RequestPermissionsUiLogic.STEP_LOCATION
            ),
            action
        )
    }

    @Test
    fun step0_whenAlreadyGranted_advances() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NEARBY_WIFI,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_NEARBY_WIFI),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_LOCATION), action)
    }

    // ---------- step 1: location ----------
    @Test
    fun step1_sdkAtLeastM_andNotGranted_requestsLocation() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_LOCATION,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(
            RequestPermissionsUiLogic.Action.LaunchSingle(
                RequestPermissionsUiLogic.PERM_FINE_LOCATION,
                nextStepOnResult = RequestPermissionsUiLogic.STEP_NOTIFICATIONS
            ),
            action
        )
    }

    @Test
    fun step1_whenGranted_advances() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_LOCATION,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_FINE_LOCATION),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_NOTIFICATIONS), action)
    }

    // ---------- step 2: notifications ----------
    @Test
    fun step2_sdkBelowTiramisu_advancesWithoutRequest() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NOTIFICATIONS,
            sdkInt = 32,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_STORAGE), action)
    }

    @Test
    fun step2_sdkAtLeastTiramisu_andNotGranted_requestsNotifications() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NOTIFICATIONS,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(
            RequestPermissionsUiLogic.Action.LaunchSingle(
                RequestPermissionsUiLogic.PERM_POST_NOTIFICATIONS,
                nextStepOnResult = RequestPermissionsUiLogic.STEP_STORAGE
            ),
            action
        )
    }

    @Test
    fun step2_sdkAtLeastTiramisu_andGranted_advances() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_NOTIFICATIONS,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_POST_NOTIFICATIONS),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_STORAGE), action)
    }

    // ---------- step 3: storage ----------
    @Test
    fun step3_sdkBelowTiramisu_requestsReadExternalStorage_ifNoneGranted() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_STORAGE,
            sdkInt = 32,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )

        val expected = RequestPermissionsUiLogic.Action.LaunchMultiple(
            arrayOf(RequestPermissionsUiLogic.PERM_READ_EXTERNAL_STORAGE),
            nextStepOnResult = RequestPermissionsUiLogic.STEP_CAMERA
        )

        // compare arrays safely
        assertTrue(action is RequestPermissionsUiLogic.Action.LaunchMultiple)
        val a = action as RequestPermissionsUiLogic.Action.LaunchMultiple
        assertEquals(expected.nextStepOnResult, a.nextStepOnResult)
        assertArrayEquals(expected.permissions, a.permissions)
    }

    @Test
    fun step3_sdkBelowTiramisu_advances_ifReadExternalStorageGranted() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_STORAGE,
            sdkInt = 32,
            granted = setOf(RequestPermissionsUiLogic.PERM_READ_EXTERNAL_STORAGE),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_CAMERA), action)
    }

    @Test
    fun step3_sdkAtLeastTiramisu_requestsMediaPerms_ifNeitherGranted() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_STORAGE,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )

        assertTrue(action is RequestPermissionsUiLogic.Action.LaunchMultiple)
        val a = action as RequestPermissionsUiLogic.Action.LaunchMultiple
        assertEquals(RequestPermissionsUiLogic.STEP_CAMERA, a.nextStepOnResult)
        assertArrayEquals(
            arrayOf(RequestPermissionsUiLogic.PERM_READ_MEDIA_IMAGES, RequestPermissionsUiLogic.PERM_READ_MEDIA_VIDEO),
            a.permissions
        )
    }

    @Test
    fun step3_sdkAtLeastTiramisu_advances_ifAnyMediaPermGranted() {
        val action1 = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_STORAGE,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_READ_MEDIA_IMAGES),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_CAMERA), action1)

        val action2 = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_STORAGE,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_READ_MEDIA_VIDEO),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_CAMERA), action2)
    }

    // ---------- step 4: camera ----------
    @Test
    fun step4_whenNotGranted_requestsCamera() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_CAMERA,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(
            RequestPermissionsUiLogic.Action.LaunchSingle(
                RequestPermissionsUiLogic.PERM_CAMERA,
                nextStepOnResult = RequestPermissionsUiLogic.STEP_BATTERY
            ),
            action
        )
    }

    @Test
    fun step4_whenGranted_advancesToBattery() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_CAMERA,
            sdkInt = 33,
            granted = setOf(RequestPermissionsUiLogic.PERM_CAMERA),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_BATTERY), action)
    }

    // ---------- step 5: battery ----------
    @Test
    fun step5_whenBatteryOptimizationNotDisabled_prompts() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_BATTERY,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.PromptBatteryOptimization, action)
    }

    @Test
    fun step5_whenBatteryOptimizationAlreadyDisabled_noop() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_BATTERY,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = true
        )
        assertEquals(RequestPermissionsUiLogic.Action.NoOp, action)
    }

    // ---------- done ----------
    @Test
    fun step6_isNoOp() {
        val action = RequestPermissionsUiLogic.nextAction(
            currentStep = RequestPermissionsUiLogic.STEP_DONE,
            sdkInt = 33,
            granted = emptySet(),
            batteryOptimizationDisabled = false
        )
        assertEquals(RequestPermissionsUiLogic.Action.NoOp, action)
    }

    // ---------- scenario: full flow sanity ----------
    @Test
    fun scenario_fullFlow_allGranted_skipsToBattery_thenNoPromptIfDisabled() {
        // If all permissions are granted, LaunchedEffect would advance quickly
        val granted = setOf(
            RequestPermissionsUiLogic.PERM_NEARBY_WIFI,
            RequestPermissionsUiLogic.PERM_FINE_LOCATION,
            RequestPermissionsUiLogic.PERM_POST_NOTIFICATIONS,
            RequestPermissionsUiLogic.PERM_READ_MEDIA_IMAGES,
            RequestPermissionsUiLogic.PERM_CAMERA
        )

        val a0 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_NEARBY_WIFI, 33, granted, false)
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_LOCATION), a0)

        val a1 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_LOCATION, 33, granted, false)
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_NOTIFICATIONS), a1)

        val a2 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_NOTIFICATIONS, 33, granted, false)
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_STORAGE), a2)

        val a3 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_STORAGE, 33, granted, false)
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_CAMERA), a3)

        val a4 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_CAMERA, 33, granted, false)
        assertEquals(RequestPermissionsUiLogic.Action.AdvanceTo(RequestPermissionsUiLogic.STEP_BATTERY), a4)

        val a5 = RequestPermissionsUiLogic.nextAction(RequestPermissionsUiLogic.STEP_BATTERY, 33, granted, true)
        assertEquals(RequestPermissionsUiLogic.Action.NoOp, a5)
    }
}