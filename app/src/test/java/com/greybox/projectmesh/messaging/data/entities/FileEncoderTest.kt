package com.greybox.projectmesh.messaging.data.entities

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FileEncoderTest {

    private val encoder = FileEncoder()

    @Test
    fun encodeBytesBase64_encodesNonNullBytes() {
        val original = "hello".toByteArray()
        val result = encoder.encodeBytesBase64(original)
        assertEquals("aGVsbG8=", result)
    }

    @Test
    fun encodeBytesBase64_returnsErrorMessageForNullBytes() {
        val result = encoder.encodeBytesBase64(null)
        assertEquals("Cannot encode file", result)
    }

    @Test
    fun decodeBase64_writesDecodedBytesToFile() {
        val base64 = "aGVsbG8="
        val tempFile = File.createTempFile("fileencoder_test", ".bin")
        tempFile.deleteOnExit()

        encoder.decodeBase64(base64, tempFile)

        val content = tempFile.readBytes()
        assertArrayEquals("hello".toByteArray(), content)
    }

    @Test
    fun decodeBase64_overwritesExistingFileContent() {
        val tempFile = File.createTempFile("fileencoder_overwrite_test", ".bin")
        tempFile.writeText("old-content")
        tempFile.deleteOnExit()

        val base64 = "aGVsbG8="
        encoder.decodeBase64(base64, tempFile)

        val content = tempFile.readBytes()
        assertArrayEquals("hello".toByteArray(), content)
    }
}
