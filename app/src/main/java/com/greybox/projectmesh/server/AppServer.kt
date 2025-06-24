package com.greybox.projectmesh.server

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.MainActivity
import com.greybox.projectmesh.R
import com.greybox.projectmesh.db.MeshDatabase
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.network.MessageNetworkHandler
import com.greybox.projectmesh.extension.updateItem
import com.greybox.projectmesh.testing.TestDeviceService
import com.ustadmobile.meshrabiya.ext.copyToWithProgressCallback
import com.ustadmobile.meshrabiya.util.FileSerializer
import com.ustadmobile.meshrabiya.util.InetAddressSerializer
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.headersContentLength
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import com.greybox.projectmesh.extension.getUriNameAndSize
import com.greybox.projectmesh.messaging.data.entities.JSONSchema
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.net.URI
import okhttp3.RequestBody.Companion.toRequestBody
import com.greybox.projectmesh.util.NotificationHelper
import com.ustadmobile.meshrabiya.log.MNetLogger
import timber.log.Timber

/*
This File is the Server for transferring files
The Meshrabiya test app uses NanoHttpD as the server, OkHttp as the client
*/
class AppServer(
    private val appContext: Context,
    private val httpClient: OkHttpClient,   // OkHttp client for making HTTP requests
    private val mLogger: MNetLogger,
    name: String,
    port: Int = 0,  // Port for NanoHTTPD server, default is 0
    val localVirtualAddr: InetAddress,
    private val receiveDir: File,   // Directory for receiving files
    private val json: Json,
    private val db: MeshDatabase,
    override val di: DI,
    private val userRepository: UserRepository
) : NanoHTTPD(port), Closeable, DIAware {

    private val logPrefix: String = "[AppServer - $name] "

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    enum class Status {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, DECLINED
    }


    private fun handleMyInfoRequest(): Response {
        val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
        val localUuid = sharedPrefs.getString("UUID", null) ?: return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, "application/json", """{"error":"No local UUID found"}"""
        )

        val localUser = runBlocking { userRepository.getUser(localUuid) } ?: return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, "application/json", """{"error":"No local user record in DB"}"""
        )

        // Add the local IP to the address field so it comes through in JSON
        val localUserWithAddr = localUser.copy(
            address = localVirtualAddr.hostAddress // or any IP you want to send
        )

        val userJson = json.encodeToString(UserEntity.serializer(), localUserWithAddr)
        return newFixedLengthResponse(Response.Status.OK, "application/json", userJson)
    }


    // Restart method to stop and start the server with an optional new IP address
    fun restart() {
        stop() // Stop the server using NanoHTTPD's built-in stop method
        Timber.d("AppServer", "Server stopped successfully")
        start(SOCKET_READ_TIMEOUT, false) // Start the server using NanoHTTPD's built-in start method
        Timber.d("AppServer", "Server restarted successfully on port: $localPort")
    }

//change to json
    /*
    This data class contains all the information about the outgoing transfer (Sending a file)
    Why not using Serializable annotation?
    -> Considering the use case of Outgoing Transfer, we only upload a file, and sending this file.
    -> The file will be temporary stored in the local memory.
     */
    data class OutgoingTransferInfo(
        val id: Int,                          // Transfer ID
        val name: String,                     // Name of the file being sent
        val uri: Uri,                         // URI of the file to be sent
        val toHost: InetAddress,              // Destination address of the transfer
        val status: Status = Status.PENDING,  // Initial transfer status is PENDING
        val size: Int,                        // Size of the file in bytes
        val transferred: Int = 0,             // Bytes transferred so far
    )

    /*
    This data class contains all the information about the incoming transfer (Receiving a file)
    By using Serializable annotation, The IncomingTransferInfo object will be serialized to JSON file,
    this will:
    1. persist the state of transfer, even if the app is closed or crashed
    2. be necessary for sending and receiving structured data over network
    Why there are two parameters (fromHost, file) use custom serializers?
    -> These are complex types, Kotlin's serialization framework doesn't know how to serialize them.
    -> The custom serializers are from Meshrabiya library.
    */

    @Serializable
    data class IncomingTransferInfo(
        val id: Int,
        val requestReceivedTime: Long = System.currentTimeMillis(),
        @Serializable(with = InetAddressSerializer::class)
        val fromHost: InetAddress,  // Address of the sender
        val deviceName: String = "Unknown Device",
        val name: String,   // Name of the file being received
        val status: Status = Status.PENDING,
        val size: Int,  // size of the file in byte
        val transferred: Int = 0,   // Bytes transferred so far
        val transferTime: Int = 1,  // Time taken to transfer the file in ms
        @Serializable(with = FileSerializer::class)
        val file: File? = null,
    )

    // Atomic Integer is a thread-safe integer, using incrementAndGet() method to
    // generate unique transfer ID without worrying about race conditions
    private val transferIdAtomic = AtomicInteger()


    // This is a MutableStateFlow holding a list of OutgoingTransferInfo object, It used to hold
    // and manage a list of ongoing file transfers.
    private val _outgoingTransfers = MutableStateFlow(emptyList<OutgoingTransferInfo>())
    // This is the public accessible Flow, used to observe the list of ongoing file transfers
    val outgoingTransfers: Flow<List<OutgoingTransferInfo>> = _outgoingTransfers.asStateFlow()

    // similar to the previous one, it holds a list of IncomingTransferInfo object.
    private val _incomingTransfers = MutableStateFlow(emptyList<IncomingTransferInfo>())
    val incomingTransfers: Flow<List<IncomingTransferInfo>> = _incomingTransfers.asStateFlow()

    // This method (getListeningPort) is provided by the NanoHTTPD to get the port number which the
    // server is listening.
    val localPort: Int
        get() = super.getListeningPort()

    /*
    Initialization Block:
    Fetches all qualified files and combined with previous list of files, then sorted by
    requestReceivedTime in descending order.
     */
    init {
        scope.launch {
            // fetch all files in receiveDir directory that end with .rx.json extension
            val incomingFiles = receiveDir.listFiles { _ , fileName: String? ->
                fileName?.endsWith(".rx.json") == true
            }?.map {
                // It deserializes the JSON file into an IncomingTransferInfo object
                json.decodeFromString(IncomingTransferInfo.serializer(), it.readText())
                // if no files match the criteria, return an empty list
            } ?: emptyList()
            NotificationHelper.createNotificationChannel(appContext)
            /*
             updates the _incomingTransfers MutableStateFlow with the list of incoming files
             It combines the previous list with newly read files from the directory
             All the files are sorted by requestReceivedTime in descending order, which means
             the latest request will be listed first
             */
            _incomingTransfers.update { prev ->
                buildList {
                    addAll(prev)
                    addAll(incomingFiles.sortedByDescending { it.requestReceivedTime })
                }
            }
        }
    }

    /*
    This is a crucial function that implement an HTTP Server
     */
    override fun serve(session: IHTTPSession): Response {
        // Extracts the URI from the session, which is the path of the request
        val path = session.uri
        mLogger(Log.INFO, "$logPrefix : ${session.method} ${session.uri}")
        //Create instance of the JSON Schema
        val JSONschema = JSONSchema()

        if (path.startsWith("/ping")) {
            //Simple endpoint to check if server is responsive
            return newFixedLengthResponse("PONG")
        }

        // check if the path is for download, indicating the request wants to download a file
            // 1) /myinfo route
        if (path.startsWith("/myinfo")) {
                return handleMyInfoRequest()
            }
            else if (path.startsWith("/updateUserInfo")) {
                // Read the POST body (assumed JSON)
                val postData = session.inputStream.bufferedReader().readText()

                //Validates JSON payload
                if(!JSONschema.schemaValidation(postData)){
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid JSON schema"}"""
                    )
                }

                try {
                    // Decode the JSON payload into a UserEntity
                    val updatedUser = json.decodeFromString(UserEntity.serializer(), postData)
                    // Update or insert the user info in the database
                    runBlocking {
                        userRepository.insertOrUpdateUser(updatedUser.uuid, updatedUser.name, updatedUser.address)
                    }
                    return newFixedLengthResponse(
                        Response.Status.OK, "application/json", """{"status":"OK"}"""
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid user data"}"""
                    )
                }
            }
        else if (path.startsWith("/updateUserInfo")) {
            // Read the POST body (assumed JSON)
            val postData = session.inputStream.bufferedReader().readText()
            try {
                // Decode the JSON payload into a UserEntity
                val updatedUser = json.decodeFromString(UserEntity.serializer(), postData)
                // Update or insert the user info in the database
                runBlocking {
                    userRepository.insertOrUpdateUser(updatedUser.uuid, updatedUser.name, updatedUser.address)
                }
                return newFixedLengthResponse(
                    Response.Status.OK, "application/json", """{"status":"OK"}"""
                )
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid user data"}"""
                )
            }
        }
            // 2) /download/
        else if (path.startsWith("/download/")) {
            // Extracts the transfer ID (Integer)from the path by taking the last part of the path
            val xferId = path.substringAfterLast("/").toInt()
            // Find the outgoing transfer with the given xferId
            val outgoingXfer = _outgoingTransfers.value.first {
                it.id == xferId
            }
            // This tracks how many bytes have been read from the stream
            val contentIn = appContext.contentResolver.openInputStream(outgoingXfer.uri)?.let {
                InputStreamCounter(it.buffered())
            }

            // If the input stream could not be opened, the function returns an HTTP response
            // with a status of INTERNAL_ERROR and an error message
            if(contentIn == null) {
                mLogger(Log.ERROR, "$logPrefix Failed to open input stream to serve $path - ${outgoingXfer.uri}")
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "text/plain",
                    "Failed to open InputStream")
            }
            mLogger(Log.INFO, "$logPrefix Sending file for xfer #$xferId")
            // Preparing the file for download
            val response = newFixedLengthResponse(
                Response.Status.OK, "application/octet-stream",
                contentIn,
                outgoingXfer.size.toLong()
            )

            //Provide status updates by checking how many bytes have been read periodically
            /*
            The loop runs until the stream (contentIn) is closed. Every 500 milliseconds,
            the code updates the _outgoingTransfers list by checking how many bytes have been read
            so far (contentIn.bytesRead).
            (.updateItem): This is a custom extension function from List
             */
            scope.launch {
                while(!contentIn.closed) {
                    _outgoingTransfers.update{ prev ->
                        prev.updateItem(
                            condition = { it.id == xferId },
                            function = { item ->
                                item.copy(
                                    transferred = contentIn.bytesRead,
                                )
                            }
                        )
                    }
                    delay(500)
                }

                /*
                After the file transfer completes, it checks if the number of bytes read equals
                the file size. If they match, the status is set to COMPLETED,
                Otherwise, it's set to FAILED.
                 */
                val status = if(contentIn.bytesRead == outgoingXfer.size) {
                    Status.COMPLETED
                }else {
                    Status.FAILED
                }
                mLogger(Log.INFO, "$logPrefix Sending file for xfer #$xferId")
                /*
                 Updating _outgoingTransfers again to set the final transferred bytes and status
                 (COMPLETED or FAILED).
                 */
                _outgoingTransfers.update { prev ->
                    prev.updateItem(
                        condition = { it.id == xferId },
                        function = { item ->
                            item.copy(
                                transferred = contentIn.bytesRead,
                                status = status
                            )
                        }
                    )
                }
            }
            // Returns the prepared response to initiate the file download.
            return response
        }
        // Check if it is a sending request
        else if(path.startsWith("/send")) {
            mLogger(Log.INFO, "$logPrefix Received incoming transfer request")
            // Parse the query parameters from the URL, converting them to a key-value map
            val searchParams = session.queryParameterString.split("&")
                .map {
                    it.substringBefore("=") to it.substringAfter("=")
                }.toMap()

            // Extract the values for "id", "filename", "size", and "from" from the query parameters
            val id = searchParams["id"]
            val filename = URLDecoder.decode(searchParams["filename"], "UTF-8")
            // if size is missing or invalid, then defaults to -1
            val size = searchParams["size"]?.toInt() ?: -1
            val fromAddr = searchParams["from"]


            // if everything is ready, create a new incomingTransferInfo object that contains all the
            // info extract from the query parameters
            if(id != null && filename != null && fromAddr != null) {
                val ipStr = fromAddr
                val userName = runBlocking {
                    val user = userRepository.getUserByIp(ipStr)
                    user?.name ?: "Unknown Device"
                }

                val incomingTransfer = IncomingTransferInfo(
                    id = id.toInt(),
                    fromHost = InetAddress.getByName(fromAddr),
                    name = filename,
                    size = size,
                    deviceName = userName
                )

                //find if theres an existing convo from sender
                val user = runBlocking { userRepository.getUserByIp(ipStr) }

                var routeToChatEnabled = false
                var chatConversationId: String? = null

                if (user != null ) {
                    val localUuid = GlobalApp.GlobalUserRepo.prefs.getString("UUID", null) ?: "local-user"
                    try {
                        val conversation = runBlocking {
                            GlobalApp.GlobalUserRepo.conversationRepository.getOrCreateConversation(
                                localUuid = localUuid,
                                remoteUser = user
                            )
                        }
                        routeToChatEnabled = true
                        chatConversationId = conversation.id
                    } catch (e: Exception) {
                        Timber.tag("AppServer").e(e, "Failed to find conversation for file " +
                                "sender")
                    }
                }

                //update the _incomingTransfers list with new incoming transers
                //then the new list is added to the top
                _incomingTransfers.update { prev ->
                    buildList {
                        add(incomingTransfer)
                        addAll(prev)
                    }
                }

                //show notification based on whether we have a conversation from the sender
                if (routeToChatEnabled && chatConversationId != null) {
                    showFileInChatNotification(incomingTransfer, chatConversationId)
                } else {
                    // Fall back to the regular file notification
                    NotificationHelper.showFileReceivedNotification(appContext, filename)
                    mLogger(Log.INFO, "$logPrefix Added request id $id for $filename from ${incomingTransfer.fromHost}")
                }
                return newFixedLengthResponse("OK")
            }else {
                mLogger(Log.INFO, "$logPrefix incomin transfer request - bad request - missing params")
                // Return an error response if any of the required parameters are missing
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad request")
            }
        }
        // Handle decline request
        else if(path.startsWith("/decline")){
            // get the transfer id from the last part of path
            // For example: path is "/decline/123", then we get 123 and converts to int
            val xferId = path.substringAfterLast("/").toInt()
            // Update _outgoingTransfers list, setting the specific item status to DECLINED
            _outgoingTransfers.update { prev ->
                prev.updateItem(
                    condition = { it.id == xferId },
                    function = {
                        it.copy(
                            status = Status.DECLINED
                        )
                    }
                )
            }
            // return "OK", indicating the decline request has been handled
            return newFixedLengthResponse("OK")
        }
        else if(path.startsWith("/getDeviceName")){
            Timber.tag("AppServer").d("local ip address: ${localVirtualAddr.hostAddress}")
            val settingPref: SharedPreferences by di.instance(tag="settings")
            return newFixedLengthResponse(settingPref.getString("device_name", Build.MODEL) ?: Build.MODEL)
        }
        else if(path.startsWith("/chat")) {
            //Timber.d("AppServer", "Received chat message*******")
            //Processes JSON payload into useable data
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            val jsonPayload = files["postData"]  // This is where the full body lives
            //Timber.d("AppServer", "JSON Payload******: $jsonPayload")

            //Checks if payload is null/blank
            if (jsonPayload.isNullOrBlank()) {
                Timber.tag("AppServer").e("Empty or missing JSON payload")
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Empty or missing JSON payload")
            }else{
                Timber.tag("AppServer").d("JSON payload not blank")
            }

            //Validates validity of JSON payload
            val JSONschema = JSONSchema()
            Timber.d("AppServer", "Validating JSON payload: ${jsonPayload::class.simpleName}")
            if (!JSONschema.schemaValidation(jsonPayload)) {
                Timber.e("AppServer", "Invalid JSON payload")
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Invalid JSON schema"}""")
            }else{
                Timber.d("AppServer", "Valid JSON payload")
            }

            //Deserialize the JSON payload
            val deserialzedJSON = json.decodeFromString<Message>(jsonPayload)

            val chatMessage = deserialzedJSON.content ?: null
            val time = deserialzedJSON.dateReceived ?: System.currentTimeMillis()
            val senderIpStr = deserialzedJSON.sender ?: null

            Timber.tag("AppServer").d("Received chat message: '$chatMessage' from $senderIpStr at" +
                    " " +
                    "$time")
//            val chatMessage = session.parameters["chatMessage"]?.firstOrNull()
//            val timeParam = session.parameters["time"]?.firstOrNull()
//            val time = timeParam?.toLongOrNull() ?: System.currentTimeMillis()
//            val senderIpStr = session.parameters["senderIp"]?.firstOrNull()

            if (senderIpStr == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing senderIp parameter")
            }

            val senderIp = try {
                InetAddress.getByName(senderIpStr)
            } catch (e: Exception) {
                Timber.tag("AppServer").e(e,"Invalid sender IP address: $senderIpStr")
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid sender IP")
            }

            // Handle optional file parameter
            //val fileUriStr = session.parameters["incomingfile"]?.firstOrNull()
            val fileUriStr = deserialzedJSON.file?.toString()
            val incomingfile = if (fileUriStr != null) {
                try {
                    URI.create(fileUriStr)
                } catch (e: Exception) {
                    Timber.tag("AppServer").e(e,"Invalid file URI: $fileUriStr")
                    null
                }
            } else {
                null
            }

            Timber.tag("AppServer").d("Received chat message: '$chatMessage' from $senderIpStr")

            try {
                val message = MessageNetworkHandler.handleIncomingMessage(
                    chatMessage,
                    time,
                    senderIp,
                    incomingfile
                )

                scope.launch {
                    db.messageDao().addMessage(message)
                    Timber.tag("AppServer").d("Message saved to database: $message")
                }

                // Change response type to ensure it's properly formatted
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "OK")
            } catch (e: Exception) {
                Timber.e("AppServer", "Error processing chat message", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error processing message")
            }

        }
        else {
            mLogger(Log.INFO, "$logPrefix : $path - NOT FOUND")
            // Returns a NOT_FOUND response indicating that the requested path could not be found.
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "not found: $path"
            )
        }
    }

    suspend fun sendDeviceName(wifiAddress: InetAddress, port: Int = DEFAULT_PORT){
        scope.launch {
            try {
                val ipStr = wifiAddress.hostAddress
                Timber.tag("AppServer").d("wifiAddress: $ipStr")

                // GET /getDeviceName
                val uri = "http://$ipStr:$port/getDeviceName"
                val request = Request.Builder().url(uri).build()
                Timber.tag("AppServer").d("Request: $request")
                val response = httpClient.newCall(request).execute()
                Timber.tag("AppServer").d("Response: $response")

                // The remote device's name
                val remoteDeviceName = response.body?.string()
                Timber.tag("AppServer").d("Remote device name: $remoteDeviceName")

                response.close() // best practice: close the response

                if (!remoteDeviceName.isNullOrEmpty()) {
                    runBlocking {
                        val existingUser = userRepository.getUserByIp(ipStr)
                        if (existingUser == null) {
                            // No user? Insert with a temporary or random UUID
                            val pseudoUuid = "temp-$ipStr"
                            userRepository.insertOrUpdateUser(
                                uuid = pseudoUuid,
                                name = remoteDeviceName,
                                address = ipStr
                            )
                        } else {
                            // Already have a user? Update the name
                            userRepository.insertOrUpdateUser(
                                uuid = existingUser.uuid,
                                name = remoteDeviceName,
                                address = existingUser.address
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Timber.tag("AppServer").e("Failed to get device name from ${wifiAddress
                    .hostAddress}")
            }
        }
    }
    /*fun requestRemoteUserInfo(remoteAddr: InetAddress, port: Int = DEFAULT_PORT) {//old
        scope.launch {
            try {
                val url = "http://${remoteAddr.hostAddress}:$port/myinfo"
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                val userJson = response.body?.string()
                response.close()

                if (!userJson.isNullOrEmpty()) {
                    // Decode JSON into a UserEntity
                    val remoteUser = json.decodeFromString(UserEntity.serializer(), userJson)
                    // Insert or update in DB
                    userRepository.insertOrUpdateUser(remoteUser.uuid, remoteUser.name)
                    // Possibly store lastSeen, remote IP, etc., if your entity includes those fields
                }
            } catch (e: Exception) {
                Timber.e("AppServer", "Failed to fetch /myinfo from $remoteAddr", e)
            }
        }
    }*/

    /**
     * Add an outgoing transfer. This is done using a Uri so that we don't have to make our own
     * copy of the file the user wants to transfer.
     */

    // Add a new function to show notifications that route to the chat screen
    private fun showFileInChatNotification(transfer: IncomingTransferInfo, conversationId: String) {
        try {
            val title = "File Received"
            val content = "Tap to view ${transfer.name} in chat"

            val intent = Intent(appContext, MainActivity::class.java).apply {
                action = "OPEN_CHAT_CONVERSATION"
                putExtra("conversationId", conversationId)
                putExtra("from_notification", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext, 1005, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "file_receive_channel"

            val notification = NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(1005, notification)

            Timber.d("AppServer", "Showed notification for file in chat")
        } catch (e: Exception) {
            Timber.e("AppServer", "Failed to show file in chat notification", e)
            // Fall back to regular notification
            NotificationHelper.showFileReceivedNotification(appContext, transfer.name)
        }
    }

    fun addOutgoingTransfer(//change to json
        uri: Uri,   // The uri of the file to be transferred
        toNode: InetAddress,    // The recipient's IP address
        toPort: Int = DEFAULT_PORT, // The recipient's port number
    ): OutgoingTransferInfo {
        // generate a unique transfer ID
        val transferId = transferIdAtomic.incrementAndGet()
        // Get the name and size of the file or content using
        // customized extension function of ContentResolver
        val nameAndSize = appContext.contentResolver.getUriNameAndSize(uri)
        val validName = nameAndSize.name ?: "unknown"
        mLogger(Log.INFO, "$logPrefix adding outgoing transfer of $uri " +
                "(name=${nameAndSize.name} size=${nameAndSize.size} to $toNode:$toPort")

        // create an OutgoingTransferInfo object with all transfer information
        val outgoingTransfer = OutgoingTransferInfo(
            id = transferId,
            name = validName,
            uri = uri ,
            toHost = toNode,
            size = nameAndSize.size.toInt(),
        )

        scope.launch(Dispatchers.IO){
            try{

                val gs = Gson()
                val json = gs.toJson(outgoingTransfer)

                val bodtype = "application/json; charset=utf-8".toMediaTypeOrNull()
                val bod = json.toRequestBody(bodtype)
                val request = Request.Builder().url("http://${toNode.hostAddress}:$toPort/" +
                        "send?id=$transferId&filename=${URLEncoder.encode(validName, "UTF-8")}" +
                        "&size=${nameAndSize.size}&from=${localVirtualAddr.hostAddress}")
                    .post(bod)
                    .build()//changed this to send a post request

                mLogger(Log.INFO, "$logPrefix notifying $toNode of incoming transfer")

                val resp = httpClient.newCall(request).execute()
                val serverResponse = resp.body?.string()
                mLogger(Log.INFO, "$logPrefix - received response: $serverResponse")

            } catch(e: Exception){
                mLogger(Log.ERROR, "$logPrefix - exception: $e")
            }
        }

        _outgoingTransfers.update { prev ->
            buildList {
                add(outgoingTransfer)
                addAll(prev)
            }
        }
        return outgoingTransfer
    }

    fun removeOutgoingTransfer(transferId: Int) {
        _outgoingTransfers.update { prev ->
            prev.filter { it.id != transferId }
        }
    }

    /*
     Accept an incoming transfer
     */
    fun acceptIncomingTransfer(
        transfer: IncomingTransferInfo,
        destFile: File,
        fromPort: Int = DEFAULT_PORT,
    ) {
        // record the current time
        val startTime = System.currentTimeMillis()
        // Update the _incomingTransfers list, setting the status to IN_PROGRESS
        _incomingTransfers.update { prev ->
            prev.updateItem(
                condition = { it.id == transfer.id },
                function = { item -> item.copy(
                    status = Status.IN_PROGRESS,
                )
                }
            )
        }
        try {
            // Build the request to download the file from the sender
            val request = Request.Builder()
                .url("http://${transfer.fromHost.hostAddress}:$fromPort/download/${transfer.id}")
                .build()
            // sending the request using OkHttp3
            val response = httpClient.newCall(request).execute()
            // Get the size of the file that will be downloaded from response
            val fileSize = response.headersContentLength()
            // 0L -> L indicates type Long
            var lastUpdateTime = 0L
            /*
            Download file,Writes it to destFile, and reports progress every 500 ms
             */
            val totalTransferred = response.body?.byteStream()?.use { responseIn ->
                FileOutputStream(destFile).use { fileOut ->
                    responseIn.copyToWithProgressCallback(
                        out = fileOut,
                        onProgress = { bytesTransferred ->
                            val timeNow = System.currentTimeMillis()
                            if(timeNow - lastUpdateTime > 500) {
                                _incomingTransfers.update { prev ->
                                    prev.updateItem(
                                        condition = { it.id == transfer.id },
                                        function = { item ->
                                            item.copy(
                                                transferred = bytesTransferred.toInt()
                                            )
                                        }
                                    )
                                }
                                lastUpdateTime = System.currentTimeMillis()
                            }
                        }
                    )
                }
            }
            response.close()
            // calculate the total time taken for downloading the file
            val transferDurationMs = (System.currentTimeMillis() - startTime).toInt()
            // Update the status of the transfer based on whether the entire file was
            // successfully downloaded or not
            val incomingTransfersVal = _incomingTransfers.updateAndGet { prev ->
                prev.updateItem(
                    condition = { it.id == transfer.id },
                    function = { item ->
                        item.copy(
                            transferTime = transferDurationMs,
                            status = if(totalTransferred == fileSize) {
                                Status.COMPLETED
                            }else {
                                Status.FAILED
                            },
                            file = destFile,
                            transferred = totalTransferred?.toInt() ?: item.transferred
                        )
                    }
                )
            }

            //Write JSON to file so received files can be listed after app restarts etc.
            val incomingTransfer = incomingTransfersVal.firstOrNull {
                it.id == transfer.id
            }
            // Create a JSON file and write the serialized transfer info to the file
            if(incomingTransfer != null) {
                val jsonFile = File(receiveDir, "${incomingTransfer.name}.rx.json")
                jsonFile.writeText(json.encodeToString(IncomingTransferInfo.serializer(), incomingTransfer))
            }
            val speedKBS = transfer.size / transferDurationMs
            mLogger(Log.INFO, "$logPrefix acceptIncomingTransfer successful: Downloaded " +
                    "${transfer.size}bytes in ${transfer.transferTime}ms ($speedKBS) KB/s")
        }
        catch(e: Exception) {
            mLogger(Log.ERROR, "$logPrefix acceptIncomingTransfer ($transfer) FAILED", e)
            _incomingTransfers.update { prev ->
                prev.updateItem(
                    condition = { it.id == transfer.id },
                    function = { item -> item.copy(
                        transferred = destFile.length().toInt(),
                        status = Status.FAILED,
                    )
                    }
                )
            }
        }
    }

    /**
     * Checks if a device is reachable at the application level
     * This performs a lightweight check without doing a full user info exchange
     */
    fun checkDeviceReachable(remoteAddr: InetAddress, port: Int = DEFAULT_PORT): Boolean {
        try {
            val url = "http://${remoteAddr.hostAddress}:$port/ping"
            val request = Request.Builder().url(url).build()

            httpClient.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag("AppServer").e(e, "Failed to check if device ${remoteAddr.hostAddress} is " +
                    "reachable")
            return false
        }
    }

    /*
    Decline an incoming transfer
    It sends an HTTP request to notify the sender that the file transfer has been
    declined and updates the status of the transfer to DECLINED in the local list
    of incoming transfers.
     */
    suspend fun onDeclineIncomingTransfer(
        transfer: IncomingTransferInfo,
        fromPort: Int = DEFAULT_PORT,
    ) {
        /*
         This block ensures that the network request is performed on the IO thread pool,
         which is optimized for input/output operations like networking and file handling.
         */
        withContext(Dispatchers.IO) {
            // Construct an HTTP request to decline the incoming transfer(Using OkHTTP3)
            val request = Request.Builder()
                .url("http://${transfer.fromHost.hostAddress}:$fromPort/decline/${transfer.id}")
                .build()
            try {
                // Send the request to the sender and get the response
                val response = httpClient.newCall(request).execute()
                val strResponse = response.body?.string()
                mLogger(Log.DEBUG, "$logPrefix - onDeclineIncomingTransfer - request to: ${request.url} : response = $strResponse")
            }catch(e: Exception) {
                mLogger(Log.WARN, "$logPrefix - onDeclineIncomingTransfer : exception- request to: ${request.url} : FAIL", e)
            }
        }
        // update the _incomingTransfers list, setting the status to DECLINED
        _incomingTransfers.update { prev ->
            prev.updateItem(
                condition = { it.id == transfer.id },
                function = {
                    it.copy(
                        status = Status.DECLINED,
                    )
                }
            )
        }
    }

    /*
    Delete an incoming transfer
    It removes both file associated with the transfer and the metadata (stored in a JSON file)
    from the device, then updates the internal state to remove the transfer from the list of
    incoming transfers.
     */
    suspend fun onDeleteIncomingTransfer(
        incomingTransfer: IncomingTransferInfo
    ) {
        /*
        Since file operations can be slow and block the main thread,
        it is important to run them on the IO thread pool,
        which is optimized for disk and network I/O tasks.
         */
        withContext(Dispatchers.IO) {
            // It will create a File object pointing to the .rx.json file
            // that stores metadata about the transfer.
            val jsonFile = incomingTransfer.file?.let {
                File(it.parentFile, it.name + ".rx.json")
            }
            // Delete both the file and the JSON file associated with the transfer
            incomingTransfer.file?.delete()
            jsonFile?.delete()
            // Update the _incomingTransfers list, removing the transfer that was just deleted
            _incomingTransfers.update { prev ->
                prev.filter { it.id != incomingTransfer.id }
            }

            // Ensure Jetpack Compose recomposes UI before handling another delete
            withContext(Dispatchers.Main) {
                delay(150)
            }
        }
    }

    /**
     * Send a chat message and return delivery status
     */
    suspend fun sendChatMessageWithStatus(address: InetAddress, time: Long, message: String, f: URI?): Boolean {

        try {
            if (TestDeviceService.isTestDevice(address)) {
                // Create an echo response from our test device
                val testMessage = Message(
                    id = 0,
                    dateReceived = System.currentTimeMillis(),
                    content = "Echo: $message",
                    sender = TestDeviceService.TEST_DEVICE_NAME,
                    chat = TestDeviceService.TEST_DEVICE_NAME,
                    file = f //dont send file with echo messages
                )

                // Store the echo response in our database
                db.messageDao().addMessage(testMessage)
                Timber.tag("AppServer").d("Test device echoed message: $message")
                return true
            }

            // Original code for real devices
            val httpUrl = HttpUrl.Builder()
                .scheme("http")
                .host(address.hostAddress)
                .port(DEFAULT_PORT)
                .addPathSegment("chat")
                .addQueryParameter("chatMessage", message)
                .addQueryParameter("time", time.toString())
                .addQueryParameter("senderIp", localVirtualAddr.hostAddress)
                .build()

            Timber.tag("AppServer").d("Request URL: $httpUrl")

            val gs = Gson()
            val msg = Message(//test this
                id = 0,
                dateReceived = time,
                sender = localVirtualAddr.hostName,
                chat = address.hostAddress,
                content = message,
                file = null//made the file null so that the file doesn't send
            )
            Timber.d("AppServer", "Messagefile: ${msg.file.toString()}")
            val msgJson = gs.toJson(msg)
            //modified http
            val httpURL = HttpUrl.Builder() .scheme("http") .host(address.hostAddress) .port(DEFAULT_PORT) .addPathSegment("chat").build()
            //Timber.d("AppServer", "HTTP URL: $httpURL")
            //post request body
            val mt = "application/json; charset=utf-8".toMediaType()
            val rbody = msgJson.toRequestBody(mt)
            //Timber.d("Appserver", "HTTP URL: $httpUrl")

            val request = Request.Builder()
                .url(httpUrl)
                .post(rbody)
                .addHeader("Content-Type", "application/json")
                .build()

            // Execute the request with proper error handling
            try {
                httpClient.newCall(request).execute().use { response ->
                    val successful = response.isSuccessful
                    if (successful) {
                        Timber.tag("AppServer").d("Message successfully sent to ${address
                            .hostAddress}")
                    } else {
                        Timber.tag("AppServer").d("Failed to send message to ${address
                            .hostAddress}, status code: ${response.code}")
                    }
                    return successful
                }
            } catch (e: Exception) {
                Timber.tag("AppServer").e(e,"Failed to send message to ${address.hostAddress}: ${e
                    .message}")
                return false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Timber.tag("AppServer").d("Failed to send message to ${address.hostAddress}: ${e.message}")
            return false
        }
    }

    /*

    fun sendChatMessage(address: InetAddress, time: Long, message: String, f: URI?) {//need to test this
        scope.launch {//check to see if this accommodates for json
            //not sending URI, i don't think the json is working
            try {
                Timber.tag("AppServer").d("chat message: $message")
                if (TestDeviceService.isTestDevice(address)) {
                    // Create an echo response from our test device
                    val testMessage = Message(
                        id = 0,
                        dateReceived = System.currentTimeMillis(),
                        content = "Echo: $message",
                        sender = TestDeviceService.TEST_DEVICE_NAME,
                        chat = TestDeviceService.TEST_DEVICE_NAME,
                        file = f//ok this is working
                    )

                    // Store the echo response in our database
                    db.messageDao().addMessage(testMessage)
                    Timber.tag("AppServer").d("Test device echoed message: $message")
                    return@launch
                }

                //original code for real devices
                val httpUrl = HttpUrl.Builder()
                    .scheme("http")
                    .host(address.hostAddress)
                    .port(DEFAULT_PORT)
                    .addPathSegment("chat")
                    .addQueryParameter("chatMessage", message)
                    .addQueryParameter("time", time.toString())
                    .addQueryParameter("senderIp", localVirtualAddr.hostAddress)
                    .build()

                Timber.tag("AppServer").d("Request URL: $httpUrl")

                val request = Request.Builder()
                    .url(httpUrl)
                    .build()

                val response = httpClient.newCall(request).execute()
                Timber.tag("AppServer").d("Response: ${response.code}")

            }
            catch (e: Exception) {
                e.printStackTrace()
                Timber.tag("AppServer").d("Failed to send message to ${address.hostAddress}")
            }
        }
    }

     */
    fun pushUserInfoTo(remoteAddr: InetAddress, port: Int = DEFAULT_PORT) {
        scope.launch {
            // Retrieve your local user info (assume you store your UUID in SharedPreferences)
            val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
            val localUuid = sharedPrefs.getString("UUID", null)
            if (localUuid == null) {
                Timber.tag("AppServer").e("Local UUID not found, cannot push user info")
                return@launch
            }
            val localUser = runBlocking { userRepository.getUser(localUuid) }
            if (localUser == null) {
                Timber.tag("AppServer").e("Local user info not found in DB, cannot push user info")
                return@launch
            }

            // Convert the user info to JSON
            val userJson = json.encodeToString(UserEntity.serializer(), localUser)
            val url = "http://${remoteAddr.hostAddress}:$port/updateUserInfo"
            val requestBody = userJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            Timber.tag("AppServer").d("Pushing user info to $url with payload: $userJson")

            try {
                val response = httpClient.newCall(request).execute()
                // Timber the response code and body (if any)
                val responseBody = response.body?.string()
                Timber.tag("AppServer").d("Response from ${remoteAddr.hostAddress}: Code=${response
                    .code}, Body=$responseBody")
            } catch (e: Exception) {
                Timber.tag("AppServer").e(e,"Failed to push user info to ${remoteAddr
                    .hostAddress}")
            }
        }
    }
    fun requestRemoteUserInfo(remoteAddr: InetAddress, port: Int = DEFAULT_PORT) {
        //Special handling for test devices
        val ipAddress = remoteAddr.hostAddress

        // Online test device should always be "online"
        if (ipAddress == TestDeviceService.TEST_DEVICE_IP) {
            DeviceStatusManager.updateDeviceStatus(ipAddress, true)
            return
        }

        // Offline test device should always be "offline"
        if (ipAddress == TestDeviceService.TEST_DEVICE_IP_OFFLINE) {
            DeviceStatusManager.updateDeviceStatus(ipAddress, false)
            return
        }
        scope.launch {
            try {
                val url = "http://${remoteAddr.hostAddress}:$port/myinfo"
                val request = Request.Builder().url(url).build()
                Timber.tag("AppServer").d("Requesting remote user info from $url")

                val response = httpClient.newCall(request).execute()
                val userJson = response.body?.string()
                Timber.tag("AppServer").d("Received user info from ${remoteAddr.hostAddress}: " +
                        "$userJson")
                response.close()

                if (!userJson.isNullOrEmpty()) {
                    // 1) Decode JSON
                    val remoteUser = json.decodeFromString(UserEntity.serializer(), userJson)

                    // 2) Optionally override with the *actual* IP of the request
                    //    if you prefer forcing the discovered IP over the user’s self-reported IP.
                    val remoteUserWithIp = remoteUser.copy(
                        address = remoteAddr.hostAddress
                    )

                    // 3) Insert or update that IP in the DB
                    userRepository.insertOrUpdateUser(
                        remoteUserWithIp.uuid,
                        remoteUserWithIp.name,
                        remoteUserWithIp.address
                    )

                    // 4) update user connection status to online
                    updateUserOnlineStatus(
                        userUuid = remoteUserWithIp.uuid,
                        isOnline = true,
                        userAddress = remoteUserWithIp.address
                    )

                    // 5) update device status manager to show device online
                    DeviceStatusManager.updateDeviceStatus(remoteAddr.hostAddress, true)

                    //try to create a conversation
                    try {
                        //get local UUID
                        val sharedPrefs: SharedPreferences by di.instance(tag = "settings")
                        val localUuid = sharedPrefs.getString("UUID", null)
                        if ( localUuid != null){
                            //create convo
                            GlobalApp.GlobalUserRepo.conversationRepository.getOrCreateConversation(
                                localUuid = localUuid,
                                remoteUser = remoteUserWithIp
                            )
                            Timber.tag("AppServer").d("Created conversation with ${remoteUserWithIp
                                .name}")
                        }
                    }catch (e: Exception) {
                        Timber.tag("AppServer").e(e, "Failed to create conversation")

                        //Update Device Status Manager to show device is offline when connection fails
                        DeviceStatusManager.updateDeviceStatus(remoteAddr.hostAddress, false)

                        //update convo status for this user
                        val user = runBlocking { userRepository.getUserByIp(remoteAddr.hostAddress) }
                        if (user != null) {
                            updateUserOnlineStatus(
                                userUuid = user.uuid,
                                isOnline = false,
                                userAddress = null
                            )
                        }
                    }

                    Timber.tag("AppServer").d("Updated local DB with remote user info: " +
                            "$remoteUserWithIp")
                }
            } catch (e: Exception) {
                Timber.tag("AppServer").e(e,"Failed to fetch /myinfo from ${remoteAddr.hostAddress}")
            }
        }
    }

    fun updateUserConnectionStatus(userUuid: String, isOnline: Boolean, userAddress: String?) {
        scope.launch {
            try {
                GlobalApp.GlobalUserRepo.conversationRepository.updateUserStatus(
                    userUuid = userUuid,
                    isOnline = isOnline,
                    userAddress = userAddress
                )
                Timber.tag("AppServer").d("Updated user $userUuid connection status: " +
                        "online=$isOnline, address=$userAddress")
            }catch (e: Exception){
                Timber.tag("AppServer").e(e,"Failed to update user connection status")
            }
        }
    }

    fun updateUserOnlineStatus(userUuid: String, isOnline: Boolean, userAddress: String?){
        scope.launch {
            try {
                GlobalApp.GlobalUserRepo.conversationRepository.updateUserStatus(
                    userUuid = userUuid,
                    isOnline = isOnline,
                    userAddress = userAddress
                )
                Timber.tag("AppServer").d("Updated user $userUuid connection status: " +
                        "online=$isOnline, address=$userAddress")
            } catch (e: Exception){
                Timber.tag("AppServer").e(e,"Failed to update user connection status")
            }
        }
    }

    fun markAllUsersOffline(){
        scope.launch {
            try {
                val allUsers = GlobalApp.GlobalUserRepo.userRepository.getAllConnectedUsers()
                for (user in allUsers) {
                    GlobalApp.GlobalUserRepo.conversationRepository.updateUserStatus(
                        userUuid = user.uuid,
                        isOnline = false,
                        userAddress = null
                    )
                }
                Timber.tag("AppServer").d("Marked all users as offline")
            } catch (e: Exception) {
                Timber.tag("AppServer").e(e,"Failed to mark all users as offline")
            }
        }
    }


    // Stop the server and cancel any coroutine that are running within the CoroutineScope
    override fun close() {
        stop()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_PORT = 9614//MAIN HAD MODIFIED IT TO 9614 FROM 4242
        val CHAT_TYPE_PLAINTEXT = "text/plain; charset=utf-8".toMediaType()
    }
}