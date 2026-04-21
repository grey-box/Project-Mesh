package com.greybox.projectmesh.messaging.data.entities

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class MessageTest {

    private val converter = URIConverter()

    @Test
    fun uriConverter_roundTripsNonNullUri() {
        val uri = URI.create("https://example.com/doc.txt")

        val asString = converter.convfromURI(uri)
        val reconstructed = converter.convtoURI(asString)

        assertEquals("https://example.com/doc.txt", asString)
        assertEquals(uri, reconstructed)
    }

    @Test
    fun uriConverter_handlesNulls() {
        assertNull(converter.convfromURI(null))
        assertNull(converter.convtoURI(null))
    }

    @Test
    fun uriSerializable_serializesAndDeserializesUri() {
        val uri = URI.create("file:///tmp/a.txt")

        val encoded = Json.encodeToString(URISerializable, uri)
        val decoded = Json.decodeFromString(URISerializable, encoded)

        assertEquals("\"file:///tmp/a.txt\"", encoded)
        assertEquals(uri, decoded)
    }

    @Test
    fun message_serialization_usesUriSerializer_andPreservesFields() {
        val message = Message(
            id = 7,
            dateReceived = 456L,
            content = "payload",
            sender = "Alice",
            chat = "convo-1",
            file = URI.create("https://example.com/f.png")
        )

        val encoded = Json.encodeToString(Message.serializer(), message)
        val decoded = Json.decodeFromString(Message.serializer(), encoded)

        assertTrue(encoded.contains("\"file\":\"https://example.com/f.png\""))
        assertEquals(message, decoded)
    }

    @Test
    fun message_defaults_fileToNull() {
        val message = Message(
            id = 8,
            dateReceived = 789L,
            content = "no attachment",
            sender = "Bob",
            chat = "convo-2"
        )

        assertNull(message.file)
    }

    @Test
    fun uriConverter_throwsForInvalidUriString() {
        try {
            converter.convtoURI("http://bad uri")
        } catch (expected: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException for invalid URI string")
    }

    @Test
    fun uriSerializable_throwsForInvalidDecodedUri() {
        try {
            Json.decodeFromString(URISerializable, "\"http://bad uri\"")
        } catch (expected: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException for invalid URI string")
    }
}
