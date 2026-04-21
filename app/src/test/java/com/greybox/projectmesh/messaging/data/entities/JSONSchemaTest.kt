package com.greybox.projectmesh.messaging.data.entities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class JSONSchemaTest {

    private val schema = JSONSchema()

    @Test
    fun schemaValidation_returnsTrue_forValidPayload() {
        val json = """
            {
              "id": 1,
              "chat": "convo-a",
              "content": "hello",
              "dateReceived": 12345,
              "sender": "Alice",
              "file": "https://example.com/file.txt"
            }
        """.trimIndent()

        assertTrue(schema.schemaValidation(json))
    }

    @Test
    fun schemaValidation_returnsTrue_whenOptionalFileMissing() {
        val json = """
            {
              "id": 2,
              "chat": "convo-b",
              "content": "no file",
              "dateReceived": 12346,
              "sender": "Bob"
            }
        """.trimIndent()

        assertTrue(schema.schemaValidation(json))
    }

    @Test
    fun schemaValidation_returnsFalse_whenRequiredFieldMissing() {
        val json = """
            {
              "id": 3,
              "chat": "convo-c",
              "content": "missing sender",
              "dateReceived": 12347
            }
        """.trimIndent()

        assertFalse(schema.schemaValidation(json))
    }

    @Test
    fun schemaValidation_returnsFalse_forMalformedJson() {
        val malformed = "{\"id\":1,\"chat\":\"x\",\"content\":\"y\","
        assertFalse(schema.schemaValidation(malformed))
    }

    @Test
    fun schemaValidation_currentlyDoesNotEnforceUriFormatOnFile() {
        val json = """
            {
              "id": 4,
              "chat": "convo-d",
              "content": "bad file uri format",
              "dateReceived": 12348,
              "sender": "Carol",
              "file": "not a uri"
            }
        """.trimIndent()

        assertTrue(schema.schemaValidation(json))
    }
}
