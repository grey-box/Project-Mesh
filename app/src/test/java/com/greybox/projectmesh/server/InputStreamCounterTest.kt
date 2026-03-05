package com.greybox.projectmesh.server

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * JVM-only tests for InputStreamCounter.
 *
 * Verifies:
 * - single-byte reads
 * - buffered reads
 * - offset reads
 * - EOF behavior
 * - close() flag
 */
class InputStreamCounterTest {

    // -----------------------------------------
    // Single-byte read: should count each byte
    // -----------------------------------------
    @Test
    fun countSingleBytes() {
        val data = "hello world".toByteArray()
        val input = ByteArrayInputStream(data)
        val counter = InputStreamCounter(input)

        while (true) {
            val result = counter.read()
            if (result == -1) break
        }

        assertEquals(data.size, counter.bytesRead)
        assertFalse(counter.closed)
    }

    // ----------------------------------------------------------
    // Buffered read: reading in chunks should count total bytes
    // ----------------------------------------------------------
    @Test
    fun readBufferBytes() {
        val data = ByteArray(4096) { it.toByte() }
        val input = ByteArrayInputStream(data)
        val counter = InputStreamCounter(input)

        val buffer = ByteArray(1024)
        while (true) {
            val n = counter.read(buffer)
            if (n == -1) break
        }

        assertEquals(data.size, counter.bytesRead)
    }

    // ----------------------------------------------------------------
    // Offset read: read(buffer, off, len) must still count accurately
    // ----------------------------------------------------------------
    @Test
    fun countOffsetBytes() {
        val data = "abcdefghi".toByteArray()
        val input = ByteArrayInputStream(data)
        val counter = InputStreamCounter(input)

        val buffer = ByteArray(10)
        while (true) {
            val n = counter.read(buffer, 1, 4)
            if (n == -1) break
        }

        assertEquals(data.size, counter.bytesRead)
    }

    // ---------------------------------------------------------
    // EOF behavior: read after EOF should return -1 and not add
    // ---------------------------------------------------------
    @Test
    fun checkEOF() {
        val data = "test".toByteArray()
        val input = ByteArrayInputStream(data)
        val counter = InputStreamCounter(input)

        val buffer = ByteArray(2)
        while (counter.read(buffer) != -1) {
            // consume all data
        }

        val before = counter.bytesRead
        val eofRead = counter.read(buffer)

        assertEquals(-1, eofRead)
        assertEquals(before, counter.bytesRead)
    }

    // --------------------------
    // close(): should set flag
    // --------------------------
    @Test
    fun checkClose() {
        val data = "xyz".toByteArray()
        val input = ByteArrayInputStream(data)
        val counter = InputStreamCounter(input)

        assertFalse(counter.closed)

        counter.close()

        assertTrue(counter.closed)
    }
}