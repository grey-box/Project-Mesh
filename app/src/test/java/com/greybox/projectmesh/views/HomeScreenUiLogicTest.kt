package com.greybox.projectmesh.views

import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


private object HomeScreenUiLogic {

    fun isStartHotspotEnabled(
        stationStatus: WifiStationState.Status?,
        concurrencySupported: Boolean
    ): Boolean {
        return stationStatus == null ||
                stationStatus == WifiStationState.Status.INACTIVE ||
                concurrencySupported
    }

    fun isConnectActionEnabled(
        hotspotStarted: Boolean,
        concurrencySupported: Boolean
    ): Boolean {
        return !hotspotStarted || concurrencySupported
    }

    fun shouldShowStopHotspotButton(wifiConnectionEnabled: Boolean): Boolean = wifiConnectionEnabled
    fun shouldShowStartHotspotButton(wifiConnectionEnabled: Boolean): Boolean = !wifiConnectionEnabled

    fun shouldShowQrCode(connectUri: String?, wifiConnectionEnabled: Boolean): Boolean {
        return connectUri != null && wifiConnectionEnabled
    }
}

class HomeScreenUiLogicTest {

    @Test
    fun startHotspotEnabled_whenStationStatusNull_true() {
        assertTrue(
            HomeScreenUiLogic.isStartHotspotEnabled(
                stationStatus = null,
                concurrencySupported = false
            )
        )
    }

    @Test
    fun startHotspotEnabled_whenStationInactive_true_evenIfNoConcurrency() {
        assertTrue(
            HomeScreenUiLogic.isStartHotspotEnabled(
                stationStatus = WifiStationState.Status.INACTIVE,
                concurrencySupported = false
            )
        )
    }

    @Test
    fun startHotspotEnabled_whenStationActive_false_ifNoConcurrency() {
        assertFalse(
            HomeScreenUiLogic.isStartHotspotEnabled(
                stationStatus = WifiStationState.Status.CONNECTING,
                concurrencySupported = false
            )
        )
    }

    @Test
    fun startHotspotEnabled_whenStationActive_true_ifConcurrencySupported() {
        assertTrue(
            HomeScreenUiLogic.isStartHotspotEnabled(
                stationStatus = WifiStationState.Status.CONNECTING,
                concurrencySupported = true
            )
        )
    }

    @Test
    fun connectEnabled_whenHotspotNotStarted_true() {
        assertTrue(HomeScreenUiLogic.isConnectActionEnabled(hotspotStarted = false, concurrencySupported = false))
    }

    @Test
    fun connectEnabled_whenHotspotStarted_false_ifNoConcurrency() {
        assertFalse(HomeScreenUiLogic.isConnectActionEnabled(hotspotStarted = true, concurrencySupported = false))
    }

    @Test
    fun connectEnabled_whenHotspotStarted_true_ifConcurrencySupported() {
        assertTrue(HomeScreenUiLogic.isConnectActionEnabled(hotspotStarted = true, concurrencySupported = true))
    }

    @Test
    fun startStop_visibility_rules() {
        assertTrue(HomeScreenUiLogic.shouldShowStartHotspotButton(false))
        assertFalse(HomeScreenUiLogic.shouldShowStopHotspotButton(false))

        assertTrue(HomeScreenUiLogic.shouldShowStopHotspotButton(true))
        assertFalse(HomeScreenUiLogic.shouldShowStartHotspotButton(true))
    }

    @Test
    fun qr_visibility_rules() {
        assertFalse(HomeScreenUiLogic.shouldShowQrCode(connectUri = null, wifiConnectionEnabled = true))
        assertFalse(HomeScreenUiLogic.shouldShowQrCode(connectUri = "mesh://link", wifiConnectionEnabled = false))
        assertTrue(HomeScreenUiLogic.shouldShowQrCode(connectUri = "mesh://link", wifiConnectionEnabled = true))
    }
}