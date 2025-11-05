package com.greybox.projectmesh.bluetooth

import android.content.Context
import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.GlobalApp.GlobalUserRepo.userRepository
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.JSONSchema
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.network.MessageNetworkHandler
import com.greybox.projectmesh.user.UserEntity
import com.ustadmobile.meshrabiya.log.MNetLogger
import com.ustadmobile.meshrabiya.server.AbstractHttpOverBluetoothServer
import com.ustadmobile.meshrabiya.vnet.bluetooth.VirtualNodeGattServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import java.net.URI

/**
 * Bluetooth server that handles incoming HTTP requests over Bluetooth Classic.
 * Parallels the Wi-Fi AppServer implementation but uses RawHttp instead of NanoHTTPD.
 */
class BluetoothServer(
    context: Context,
    rawHttp: RawHttp,
    private val logger: MNetLogger,
    private val db: MeshDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxClients: Int = 1
) : AbstractHttpOverBluetoothServer(
    appContext = context,
    rawHttp = rawHttp,
    allocationServiceUuid = BluetoothUuids.ALLOCATION_SERVICE_UUID,
    allocationCharacteristicUuid = BluetoothUuids.ALLOCATION_CHAR_UUID,
    maxClients = maxClients,
    uuidAllocationServerFactory = { appCtx, svcUuid, charUuid, logger, clients, listener ->
        VirtualNodeGattServer(
            appCtx,
            svcUuid,
            charUuid,
            logger,
            clients,
            listener
        )
    }
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun handleRequest(
        remoteDeviceAddress: String,
        request: RawHttpRequest,
    ): RawHttpResponse<*> {
        val path = request.uri.path
        val method = request.method
        // aded logs for debugging
        Log.d("BluetoothServer", "Received ${request.method} request to $path from $remoteDeviceAddress")

        return when {
            // made this more explicit and moved logic to its own function
            path == "/chat" && method == "POST"  -> handleChatRequest(remoteDeviceAddress, request)
            else -> {
                Log.w("BluetoothServer", "Unknown path: $path")
                createErrorResponse(404, "Not Found", "not found: $path")
            }
        }
    }

    private suspend fun validateRemoteDevice(
        senderIP: String,
        claimedIdentifier: String
    ): UserEntity? {
        // get the user based on their senderIP
        val user = userRepository.getUserByIp(senderIP)
        if (user == null) {
            Log.w("BluetoothServer", "Unknown user: $claimedIdentifier")
            return null
        }

        // check if we have a saved MAC address for this user matching the IP
        if (user.macAddress == null) {
            Log.w("BluetoothServer", "User ${user.name} has no linked MAC address")
            return null
        }

        if (!user.macAddress.equals(claimedIdentifier, ignoreCase = true)) {
            Log.e("BluetoothServer", "SECURITY ALERT: MAC mismatch!")
            Log.e("BluetoothServer", "User: ${user.name} (${user.uuid})")
            Log.e("BluetoothServer", "Expected MAC: ${user.macAddress}")
            return null
        }

        Log.d("BluetoothServer", "MAC validation passed for ${user.name}")
        return user
    }

    private fun handleChatRequest(
        remoteDeviceAddress: String,
        request: RawHttpRequest
    ): RawHttpResponse<*> {
        try {
            // Extract JSON body from request
            val body = request.body.orElse(null)?.asRawString(Charsets.UTF_8)

            if (body.isNullOrBlank()) {
                Log.e("BluetoothServer", "Empty or missing JSON payload")
                return createErrorResponse(
                    statusCode = 400,
                    statusMessage = "Bad Request",
                    body = "Empty or missing JSON payload"
                )
            }

            Log.d("BluetoothServer", "Received JSON payload from $remoteDeviceAddress: ${body.take(100)}")

         // Validate JSON schema
            val jsonSchema = JSONSchema()
            if (!jsonSchema.schemaValidation(body)) {
                Log.e("BluetoothServer", "Invalid JSON payload by schema")
                return createErrorResponse(
                    statusCode = 400,
                    statusMessage = "Bad Request",
                    body = """{"error":"Invalid JSON schema"}""",
                    contentType = "application/json"
                )
            }

            Log.d("BluetoothServer", "JSON schema validation passed")

          // Deserialize -> Message and save to DB, like Wi-Fi
            val deserializedJSON = json.decodeFromString<Message>(body)

            val chatMessage = deserializedJSON.content
            val time = deserializedJSON.dateReceived ?: System.currentTimeMillis()
            val senderStr = deserializedJSON.sender

            Log.d("BluetoothServer", "Parsed message - content: '$chatMessage', sender: $senderStr, time: $time")

            // make sure we have a valid sender field
            if (senderStr == null) {
                Log.e("BluetoothServer", "Missing sender in message")
                return createErrorResponse(
                    statusCode = 400,
                    statusMessage = "Bad Request",
                    body = "Missing sender parameter"
                )
            }

            // Handle file <---  I don't think this works in messaging so it won't here either
            // but added to be consistent. For now file will always be passed as null.
            val fileUriStr = deserializedJSON.file?.toString()
            val incomingFile = if (fileUriStr != null) {
                try {
                    URI.create(fileUriStr)
                } catch (e: Exception) {
                    Log.e("BluetoothServer", "Invalid file URI: $fileUriStr", e)
                    null
                }
            } else {
                null
            }

            val validatedUser = runBlocking {
                validateRemoteDevice(senderStr, remoteDeviceAddress)
            }

            // make sure we have a valid sender field
            if (validatedUser == null) {
                Log.e("BluetoothServer", "MAC Address Mismatch!")
                return createErrorResponse(
                    statusCode = 400,
                    statusMessage = "Bad Request",
                    body = "MAC Mismatch"
                )
            }


            // Process the incoming message using our new Bluetooth handler
            // - Looks up user by MAC address using getUserByMac()
            // - Creates conversation ID
            // - Creates Message entity
            // - Updates conversation
            val message = MessageNetworkHandler.handleIncomingBluetoothMessage(
                chatMessage = chatMessage,
                time = time,
                senderMac = remoteDeviceAddress,
                senderName = senderStr,
                incomingFile = incomingFile
            )

            Log.d("BluetoothServer", "Message processed: id=${message.id}, chat=${message.chat}")

            // Save message to database
            // Using a coroutine scope to avoid blocking the Bluetooth thread
            scope.launch {
                try {
                    db.messageDao().addMessage(message)
                    Log.d("BluetoothServer", "Message saved to database successfully")
                } catch (e: Exception) {
                    Log.e("BluetoothServer", "Failed to save message to database", e)
                }
            }
            // Return success response just like wifi
            return createSuccessResponse()
        } catch (e: Exception) {
            Log.e("BluetoothServer", "Error processing chat message from $remoteDeviceAddress", e)
            return createErrorResponse(
                statusCode = 500,
                statusMessage = "Internal Server Error",
                body = "Error processing message: ${e.message}"
            )
        }
    }


    private fun createSuccessResponse(): RawHttpResponse<*> {
        return rawHttp.parseResponse(
            "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 2\r\n" +
                    "\r\n" +
                    "OK"
        )
    }

    private fun createErrorResponse(
        statusCode: Int,
        statusMessage: String,
        body: String,
        contentType: String = "text/plain"
    ): RawHttpResponse<*> {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        return rawHttp.parseResponse(
            "HTTP/1.1 $statusCode $statusMessage\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n" +
                    "\r\n" +
                    body
        )
    }
}