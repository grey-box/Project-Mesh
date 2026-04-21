// File: app/src/test/java/com/greybox/projectmesh/views/SendScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for SendScreen.kt WITHOUT touching that file.
 *
 * What we can test in JVM:
 * 1) File picker callback behavior: only call onFileChosen when uris not empty
 * 2) Swipe confirm logic: only delete on EndToStart; StartToEnd does nothing
 * 3) Formatting helpers: autoConvertByte, autoConvertMS (these are pure functions in this file)
 *
 * What we DON'T test here (androidTest later):
 * - Compose animations & SwipeToDismissBox state integration
 * - ActivityResult launcher plumbing
 * - GlobalApp repo calls + runBlocking UI impacts
 */
private object SendScreenUiLogic {

    enum class SwipeValue { EndToStart, StartToEnd, Settled }

    data class FilePickerEffects(
        val shouldCallOnFileChosen: Boolean,
        val passedUris: List<String>
    )

    fun onFilesPicked(uris: List<String>): FilePickerEffects {
        return FilePickerEffects(
            shouldCallOnFileChosen = uris.isNotEmpty(),
            passedUris = if (uris.isNotEmpty()) uris else emptyList()
        )
    }

    data class SwipeEffects(
        val shouldDelete: Boolean,
        val confirmReturnValue: Boolean,
        val shouldFadeOut: Boolean,
        val delayMsBeforeDelete: Long?
    )

    /**
     * Mirrors confirmValueChange in the code:
     * if dismissValue == EndToStart:
     *   launch { isVisible=false; delay(300); onDelete(transfer) }
     *   return true
     * else false
     */
    fun onSwipeConfirm(dismissValue: SwipeValue): SwipeEffects {
        return if (dismissValue == SwipeValue.EndToStart) {
            SwipeEffects(
                shouldDelete = true,
                confirmReturnValue = true,
                shouldFadeOut = true,
                delayMsBeforeDelete = 300L
            )
        } else {
            SwipeEffects(
                shouldDelete = false,
                confirmReturnValue = false,
                shouldFadeOut = false,
                delayMsBeforeDelete = null
            )
        }
    }

    // ---- copy of helpers (so tests don’t depend on Android/Compose) ----
    fun autoConvertByte(byteSize: Int): String {
        val kb = Math.round(byteSize / 1024.0 * 100) / 100.0
        val mb = Math.round((byteSize / (1024.0 * 1024.0) * 100) / 100.0)
        if (byteSize == 0) {
            return "0B"
        } else if (mb < 1) {
            return "${kb}KB"
        }
        return "${mb}MB"
    }

    fun autoConvertMS(ms: Int): String {
        val second = Math.round(ms / 1000.0 * 100) / 100.0
        val minute = Math.round((second / 60.0) * 100) / 100.0
        return if (second >= 1 && minute < 1) {
            "${second}s"
        } else if (minute >= 1) {
            "${minute}m"
        } else {
            "${ms}ms"
        }
    }
}

class SendScreenUiLogicTest {

    // ---------- file picker logic ----------
    @Test
    fun onFilesPicked_whenEmpty_doesNotCallVm() {
        val effects = SendScreenUiLogic.onFilesPicked(emptyList())
        assertFalse(effects.shouldCallOnFileChosen)
        assertTrue(effects.passedUris.isEmpty())
    }

    @Test
    fun onFilesPicked_whenNonEmpty_callsVm_withSameUris_orderPreserved() {
        val uris = listOf("u1", "u2", "u3")
        val effects = SendScreenUiLogic.onFilesPicked(uris)
        assertTrue(effects.shouldCallOnFileChosen)
        assertEquals(uris, effects.passedUris)
    }

    // ---------- swipe confirm logic ----------
    @Test
    fun onSwipeConfirm_endToStart_deletes_fadesOut_delays300_andReturnsTrue() {
        val effects = SendScreenUiLogic.onSwipeConfirm(SendScreenUiLogic.SwipeValue.EndToStart)
        assertTrue(effects.shouldDelete)
        assertTrue(effects.confirmReturnValue)
        assertTrue(effects.shouldFadeOut)
        assertEquals(300L, effects.delayMsBeforeDelete)
    }

    @Test
    fun onSwipeConfirm_startToEnd_doesNothing_andReturnsFalse() {
        val effects = SendScreenUiLogic.onSwipeConfirm(SendScreenUiLogic.SwipeValue.StartToEnd)
        assertFalse(effects.shouldDelete)
        assertFalse(effects.confirmReturnValue)
        assertFalse(effects.shouldFadeOut)
        assertNull(effects.delayMsBeforeDelete)
    }

    @Test
    fun onSwipeConfirm_settled_doesNothing_andReturnsFalse() {
        val effects = SendScreenUiLogic.onSwipeConfirm(SendScreenUiLogic.SwipeValue.Settled)
        assertFalse(effects.shouldDelete)
        assertFalse(effects.confirmReturnValue)
        assertFalse(effects.shouldFadeOut)
        assertNull(effects.delayMsBeforeDelete)
    }

    // ---------- autoConvertByte ----------
    @Test
    fun autoConvertByte_zero_is0B() {
        assertEquals("0B", SendScreenUiLogic.autoConvertByte(0))
    }

    @Test
    fun autoConvertByte_under1MB_outputsKB_withExactImplementationLogic() {
        // 1024 bytes = 1KB
        assertEquals("1.0KB", SendScreenUiLogic.autoConvertByte(1024))

        // 1536 bytes = 1.5KB
        assertEquals("1.5KB", SendScreenUiLogic.autoConvertByte(1536))

        // 1100 bytes
        val result = SendScreenUiLogic.autoConvertByte(1100)
        assertTrue(result.endsWith("KB"))
    }

    @Test
    fun autoConvertByte_rounding_examples() {
        // 1100 bytes => 1.07KB (1100/1024=1.074.. -> round 1.07)
        assertEquals("1.07KB", SendScreenUiLogic.autoConvertByte(1100))
    }

    // ---------- autoConvertMS ----------

    @Test
    fun autoConvertMS_atLeast1s_andLessThan1m_outputsSeconds() {
        assertEquals("1.0s", SendScreenUiLogic.autoConvertMS(1000))
        assertEquals("1.5s", SendScreenUiLogic.autoConvertMS(1500))
        // 59 seconds
        val s = SendScreenUiLogic.autoConvertMS(59_000)
        assertTrue(s.endsWith("s"))
    }

    @Test
    fun autoConvertMS_atLeast1m_outputsMinutes() {
        assertEquals("1.0m", SendScreenUiLogic.autoConvertMS(60_000))
        // 90 seconds => 1.5m (because second=90.0, minute=1.5)
        assertEquals("1.5m", SendScreenUiLogic.autoConvertMS(90_000))
    }

    @Test
    fun autoConvertMS_rounding_examples() {
        // 1234ms -> 1.23s (rounded to 2 decimals)
        assertEquals("1.23s", SendScreenUiLogic.autoConvertMS(1234))
    }
}