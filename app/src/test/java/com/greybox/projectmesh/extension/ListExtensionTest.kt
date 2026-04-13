package com.greybox.projectmesh.extension

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM-only tests for List<T>.updateItem extension.
 *
 * Verifies:
 * - first matching item is updated
 * - only the first match is changed
 * - lists with no match return the same instance
 * - empty lists behave correctly
 * - update function is not invoked when no match exists
 */
class ListExtensionTest {

    // --------------------------------------
    // First match: should update first match
    // --------------------------------------
    @Test
    fun checkUpdateFirstItem() {
        val list = listOf(1, 2, 3, 4)
        val updated = list.updateItem(
            condition = { it % 2 == 0 },
            function = { it * 10 }
        )

        assertEquals(listOf(1, 20, 3, 4), updated)
    }

    // ---------------------------------------------------
    // Only first matching item should be transformed once
    // ---------------------------------------------------
    @Test
    fun checkOnlyFirstMatch() {
        val list = listOf(2, 4, 6)
        val updated = list.updateItem(
            condition = { it % 2 == 0 },
            function = { it * 10 }
        )

        assertEquals(listOf(20, 4, 6), updated)
    }

    // -------------------------------------------------
    // No match: must return same list instance unchanged
    // -------------------------------------------------
    @Test
    fun checkNoMatch() {
        val list = listOf(1, 3, 5)
        val updated = list.updateItem(
            condition = { it % 2 == 0 },
            function = { it * 10 }
        )

        assertSame(list, updated)
        assertEquals(listOf(1, 3, 5), updated)
    }

    // ---------------------------
    // Empty list stays unchanged
    // ---------------------------
    @Test
    fun checkEmptyList() {
        val list = emptyList<Int>()
        val updated = list.updateItem(
            condition = { true },
            function = { it * 10 }
        )

        assertSame(list, updated)
        assertTrue(updated.isEmpty())
    }

    // -------------------------------------------------------
    // No match: function should not be executed even once
    // -------------------------------------------------------
    @Test
    fun checkCallingFunctionNoMatch() {
        val list = listOf(1, 3, 5)
        var called = false

        val updated = list.updateItem(
            condition = { it % 2 == 0 },
            function = {
                called = true
                it * 10
            }
        )

        assertFalse(called)
        assertSame(list, updated)
    }
}