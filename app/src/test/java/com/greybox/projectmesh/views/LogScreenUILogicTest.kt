// File: app/src/test/java/com/greybox/projectmesh/views/LogScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JVM-testable logic extracted from LogScreen.kt behavior.
 * We are NOT modifying LogScreen.kt right now.
 *
 * We model only the selection + formatting rules so we can later wire androidTest UI tests.
 */
private object LogScreenUiLogic {

    data class LogLine(val lineId: Int, val time: Long, val line: String)

    fun selectAll(logs: List<LogLine>): Set<Int> = logs.map { it.lineId }.toSet()

    fun onLongPress(selectionMode: Boolean, lineId: Int): Pair<Boolean, Set<Int>> {
        return if (!selectionMode) true to setOf(lineId) else selectionMode to emptySet()
    }

    fun toggleOnTap(selectionMode: Boolean, currentlySelected: Set<Int>, lineId: Int): Set<Int> {
        if (!selectionMode) return currentlySelected
        return if (currentlySelected.contains(lineId)) currentlySelected - lineId else currentlySelected + lineId
    }

    fun applyCheckbox(checked: Boolean, currentlySelected: Set<Int>, lineId: Int): Set<Int> {
        return if (checked) currentlySelected + lineId else currentlySelected - lineId
    }

    fun copyPayload(
        logs: List<LogLine>,
        selectedLineIds: Set<Int>,
        formatter: SimpleDateFormat
    ): String {
        return logs
            .filter { selectedLineIds.contains(it.lineId) }
            .joinToString("\n") { line ->
                "[${formatter.format(Date(line.time))}] ${line.line}"
            }
    }

    fun afterCopyReset(): Pair<Boolean, Set<Int>> = false to emptySet()
    fun afterCancelReset(): Pair<Boolean, Set<Int>> = false to emptySet()
}

class LogScreenUiLogicTest {

    private val formatter = SimpleDateFormat("HH:mm:ss.SS", Locale.US)

    @Test
    fun selectAll_returnsAllLineIds() {
        val logs = listOf(
            LogScreenUiLogic.LogLine(1, 1000L, "A"),
            LogScreenUiLogic.LogLine(2, 2000L, "B"),
            LogScreenUiLogic.LogLine(3, 3000L, "C")
        )
        assertEquals(setOf(1, 2, 3), LogScreenUiLogic.selectAll(logs))
    }

    @Test
    fun onLongPress_whenNotInSelection_entersSelection_andSelectsThatLine() {
        val (mode, selected) = LogScreenUiLogic.onLongPress(selectionMode = false, lineId = 42)
        assertTrue(mode)
        assertEquals(setOf(42), selected)
    }

    @Test
    fun onLongPress_whenAlreadyInSelection_noStateChangeSuggested() {
        val (mode, selected) = LogScreenUiLogic.onLongPress(selectionMode = true, lineId = 42)
        assertTrue(mode)
        assertEquals(emptySet<Int>(), selected) // we return emptySet to signal "no-op" in this helper design
    }

    @Test
    fun toggleOnTap_whenNotInSelectionMode_noChange() {
        val selected = LogScreenUiLogic.toggleOnTap(
            selectionMode = false,
            currentlySelected = setOf(1),
            lineId = 2
        )
        assertEquals(setOf(1), selected)
    }

    @Test
    fun toggleOnTap_whenSelected_removesIt() {
        val selected = LogScreenUiLogic.toggleOnTap(
            selectionMode = true,
            currentlySelected = setOf(1, 2),
            lineId = 2
        )
        assertEquals(setOf(1), selected)
    }

    @Test
    fun toggleOnTap_whenNotSelected_addsIt() {
        val selected = LogScreenUiLogic.toggleOnTap(
            selectionMode = true,
            currentlySelected = setOf(1),
            lineId = 2
        )
        assertEquals(setOf(1, 2), selected)
    }

    @Test
    fun applyCheckbox_checkedTrue_addsLineId() {
        val selected = LogScreenUiLogic.applyCheckbox(
            checked = true,
            currentlySelected = setOf(1),
            lineId = 2
        )
        assertEquals(setOf(1, 2), selected)
    }

    @Test
    fun applyCheckbox_checkedFalse_removesLineId() {
        val selected = LogScreenUiLogic.applyCheckbox(
            checked = false,
            currentlySelected = setOf(1, 2),
            lineId = 2
        )
        assertEquals(setOf(1), selected)
    }

    @Test
    fun copyPayload_formatsOnlySelectedLines_inOriginalOrder() {
        val logs = listOf(
            LogScreenUiLogic.LogLine(10, 0L, "first"),
            LogScreenUiLogic.LogLine(20, 1234L, "second"),
            LogScreenUiLogic.LogLine(30, 5678L, "third"),
        )
        val selectedIds = setOf(10, 30)

        val payload = LogScreenUiLogic.copyPayload(
            logs = logs,
            selectedLineIds = selectedIds,
            formatter = formatter
        )

        // We don't hardcode exact formatted time string (locale/timezone can vary);
        // instead assert structure and inclusion.
        assertTrue(payload.contains("] first"))
        assertTrue(payload.contains("] third"))
        assertFalse(payload.contains("] second"))
        // ensures newline between two selected lines
        assertTrue(payload.contains("\n"))
    }

    @Test
    fun afterCopyReset_clearsSelectionMode_andSelectedIds() {
        val (mode, selected) = LogScreenUiLogic.afterCopyReset()
        assertFalse(mode)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun afterCancelReset_clearsSelectionMode_andSelectedIds() {
        val (mode, selected) = LogScreenUiLogic.afterCancelReset()
        assertFalse(mode)
        assertTrue(selected.isEmpty())
    }
}