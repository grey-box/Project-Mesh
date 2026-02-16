package com.greybox.projectmesh.extension

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

class ContentResolverExtensionTest {

    @Test
    fun getUriNameAndSize_whenFileScheme_returnsFileNameAndLength() {
        val tmp = File.createTempFile("pm_test_", ".bin")
        tmp.writeBytes(ByteArray(5) { 1 })
        tmp.deleteOnExit()

        val uri = Uri.fromFile(tmp)
        val resolver = mockk<ContentResolver>(relaxed = true)

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(tmp.name, result.name)
        assertEquals(tmp.length(), result.size)
    }

    @Test
    fun getUriNameAndSize_whenQueryReturnsNull_returnsNullAndMinusOne() {
        val uri = Uri.parse("content://test/nope")
        val resolver = mockk<ContentResolver>()
        every { resolver.query(uri, null, null, null, null) } returns null

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(UriNameAndSize(null, -1L), result)
        verify(exactly = 1) { resolver.query(uri, null, null, null, null) }
    }

    @Test
    fun getUriNameAndSize_whenCursorMoveToFirstFalse_returnsNullAndMinusOne() {
        val uri = Uri.parse("content://test/empty")
        val resolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>(relaxed = true)

        every { resolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns false

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(UriNameAndSize(null, -1L), result)
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun getUriNameAndSize_whenColumnIndicesAreZero_returnsNullAndMinusOne_dueToIndexCheck() {
        val uri = Uri.parse("content://test/cols0")
        val resolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>(relaxed = true)

        every { resolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 0

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(UriNameAndSize(null, -1L), result)
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun getUriNameAndSize_whenSizeIsNull_returnsNameAndMinusOne() {
        val uri = Uri.parse("content://test/sizeNull")
        val resolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>(relaxed = true)

        every { resolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 1
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 2
        every { cursor.isNull(2) } returns true
        every { cursor.getString(1) } returns "hello.txt"

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(UriNameAndSize("hello.txt", -1L), result)
        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun getUriNameAndSize_whenSizeIsPresent_returnsNameAndSize() {
        val uri = Uri.parse("content://test/sizeOk")
        val resolver = mockk<ContentResolver>()
        val cursor = mockk<Cursor>(relaxed = true)

        every { resolver.query(uri, null, null, null, null) } returns cursor
        every { cursor.moveToFirst() } returns true
        every { cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 1
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 2
        every { cursor.isNull(2) } returns false
        every { cursor.getString(1) } returns "world.bin"
        every { cursor.getString(2) } returns "12345"

        val result = resolver.getUriNameAndSize(uri)

        assertEquals(UriNameAndSize("world.bin", 12345L), result)
        verify(exactly = 1) { cursor.close() }
    }
}
