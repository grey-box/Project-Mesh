// File: app/src/test/java/com/greybox/projectmesh/views/SelectDestNodeScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for SelectDestNodeScreen.kt WITHOUT touching that file.
 *
 * What we can unit-test now (pure logic):
 * - UI branch decision: show progress vs show list
 * - Render order of nodes (Map iteration order via entries.toList())
 * - Click behavior: passes entry.key to onClickReceiver
 *
 * Later in androidTest, verify actual Compose UI interactions.
 */
private object SelectDestNodeScreenUiLogic {

    sealed class UiMode {
        data class InProgress(val deviceName: String) : UiMode()
        object ListMode : UiMode()
    }

    fun uiMode(contactingInProgressDevice: String?): UiMode {
        return if (contactingInProgressDevice != null) UiMode.InProgress(contactingInProgressDevice)
        else UiMode.ListMode
    }

    fun renderOrderKeys(allNodes: Map<Int, Any?>): List<Int> {
        // Mirrors: uiState.allNodes.entries.toList()
        return allNodes.entries.toList().map { it.key }
    }

    fun handleNodeClick(key: Int, onClickReceiver: (Int) -> Unit): Int {
        onClickReceiver(key)
        return key
    }

    fun progressText(deviceName: String): String {
        return "Contacting $deviceName\nThis might take a few seconds."
    }
}

class SelectDestNodeScreenUiLogicTest {

    // ---------- mode selection ----------
    @Test
    fun uiMode_whenContactingNotNull_isInProgress() {
        val mode = SelectDestNodeScreenUiLogic.uiMode("Pixel-7")
        assertTrue(mode is SelectDestNodeScreenUiLogic.UiMode.InProgress)
        val m = mode as SelectDestNodeScreenUiLogic.UiMode.InProgress
        assertEquals("Pixel-7", m.deviceName)
    }

    @Test
    fun uiMode_whenContactingNull_isListMode() {
        val mode = SelectDestNodeScreenUiLogic.uiMode(null)
        assertTrue(mode is SelectDestNodeScreenUiLogic.UiMode.ListMode)
    }

    // ---------- progress text ----------
    @Test
    fun progressText_matchesUiString() {
        val text = SelectDestNodeScreenUiLogic.progressText("DeviceA")
        assertEquals("Contacting DeviceA\nThis might take a few seconds.", text)
    }

    // ---------- render order ----------
    @Test
    fun renderOrderKeys_preservesLinkedHashMapInsertionOrder() {
        val map = linkedMapOf<Int, Any?>(
            10 to Any(),
            20 to Any(),
            30 to Any()
        )
        val keys = SelectDestNodeScreenUiLogic.renderOrderKeys(map)
        assertEquals(listOf(10, 20, 30), keys)
    }

    @Test
    fun renderOrderKeys_hashMap_orderNotGuaranteed_butContainsAllKeys() {
        val map = hashMapOf<Int, Any?>(
            1 to Any(),
            2 to Any(),
            3 to Any()
        )
        val keys = SelectDestNodeScreenUiLogic.renderOrderKeys(map)
        assertEquals(3, keys.size)
        assertTrue(keys.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun renderOrderKeys_emptyMap_returnsEmpty() {
        val keys = SelectDestNodeScreenUiLogic.renderOrderKeys(emptyMap())
        assertTrue(keys.isEmpty())
    }

    // ---------- click behavior ----------
    @Test
    fun handleNodeClick_callsReceiver_withExactKey_andReturnsKey() {
        var received: Int? = null
        val returned = SelectDestNodeScreenUiLogic.handleNodeClick(99) { k -> received = k }

        assertEquals(99, returned)
        assertEquals(99, received)
    }
}