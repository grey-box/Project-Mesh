package com.greybox.projectmesh.messaging.data.entities

import android.util.Log
import org.json.JSONObject
import org.json.JSONException

/**
 * Utility class to validate JSON strings against a predefined JSON schema.
 *
 * The schema enforces required fields and data types for messages.
 */
class JSONSchema {

    private val schemaString = """
{
    "type": "object",
    "required": ["id", "chat", "content", "dateReceived", "sender"],
    "properties": {
        "id": { "type": "integer" },
        "chat": { "type": "string" },
        "content": { "type": "string" },
        "dateReceived": { "type": "integer" },
        "sender": { "type": "string" },
        "file": { "type": "string", "format": "uri" }
    }
}
"""

    /**
     * Validates a JSON string against the internal schema.
     *
     * @param json The JSON string representing a message.
     * @return True if the JSON is valid according to the schema, false otherwise.
     */
    fun schemaValidation(json: String): Boolean {
        //Log.d("JSONSchema", "Validating JSON: $json")
        //Log.d("JSONSchema", "Against schema: $schemaString")
        try {
            val schemaJson = JSONObject(schemaString)
            val jsonObject = JSONObject(json)

            validate(jsonObject, schemaJson)
            return true
        } catch (e: JSONException) {
            Log.e("JSONSchema", "JSON schema validation failed: ${e.message}")
            return false
        }
    }

    /**
     * Checks that the given JSON object contains all required fields as per the schema.
     *
     * @param json The JSON object to validate.
     * @param schema The JSON schema object defining required fields.
     * @throws JSONException If any required field is missing.
     */
    private fun validate(json: JSONObject, schema: JSONObject) {
        val requiredFields = schema.getJSONArray("required")
        for (i in 0 until requiredFields.length()) {
            val field = requiredFields.getString(i)
            if (!json.has(field)) {
                throw JSONException("Missing required field: $field")
            }
        }
    }
}
