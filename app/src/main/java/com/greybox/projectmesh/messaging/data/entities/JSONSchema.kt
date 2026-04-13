package com.greybox.projectmesh.messaging.data.entities

import org.json.JSONObject
import org.json.JSONException
import timber.log.Timber

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
    //Takes JSON string and validates it against JSON Schema
    fun schemaValidation(json: String): Boolean {
        //Timber.tag("JSONSchema").d("Validating JSON: $json")
        //Timber.tag("JSONSchema").d("Against schema: $schemaString")
        try {
            val schemaJson = JSONObject(schemaString)
            val jsonObject = JSONObject(json)

            validate(jsonObject, schemaJson)
            return true
        }catch (e: JSONException) {
            Timber.tag("JSONSchema",).e("JSON schema validation failed: ${e.message}")
            return false
        }
    }

    //Validates JSON object against schema
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