// Path: app/src/main/java/com/greybox/projectmesh/messaging/network/MessageNetworkHandler.kt
package com.greybox.projectmesh.messaging.network

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.MainActivity
import com.greybox.projectmesh.R
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.utils.ConversationUtils
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.testing.TestDeviceService
import com.greybox.projectmesh.util.NotificationHelper
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import com.greybox.projectmesh.user.UserRepository
import kotlinx.coroutines.runBlocking
import android.content.SharedPreferences
import android.os.Parcel
import android.os.Parcelable
import java.net.URI

/**
 * Handles sending and receiving chat messages over the network.
 *
 * @property httpClient The OkHttpClient used to make HTTP requests.
 * @property localVirtualAddr The local device's virtual network IP address.
 * @property di The Kodein DI container instance for retrieving dependencies.
 */
class MessageNetworkHandler(
    private val httpClient: OkHttpClient,
    private val localVirtualAddr: InetAddress,
    override val di: DI
) : DIAware {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val conversationRepository: ConversationRepository by di.instance()
    private val settingsPrefs: SharedPreferences by di.instance(tag = "settings")

    /**
     * Sends a chat message to a remote device over HTTP.
     *
     * @param address The target device's IP address.
     * @param time The timestamp of the message in milliseconds.
     * @param message The message text to send.
     * @param file Optional URI of a file to send along with the message.
     */
    fun sendChatMessage(address: InetAddress, time: Long, message: String, file: URI?/* test this*/) {
        scope.launch {
            try {
                val httpUrl = HttpUrl.Builder()
                    .scheme("http")
                    .host(address.hostAddress)
                    .port(AppServer.DEFAULT_PORT)
                    .addPathSegment("chat")
                    .addQueryParameter("chatMessage", message)
                    .addQueryParameter("time", time.toString())
                    .addQueryParameter("senderIp", localVirtualAddr.hostAddress)
                    .apply {
                        if (file != null) {
                            addQueryParameter("incomingfile", file.toString())
                        }
                    }
                    .build()

                val request = Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build()

                Log.d("MessageNetworkHandler", "Request URL: ${request.url}")
                //Turn this into JSON
                /*
                val gs = Gson()
                val msg = Message(
                    id = 0,
                    dateReceived = time,
                    content = message,
                    sender = localVirtualAddr.hostName,
                    chat = address.hostAddress,
                    file = file
                )
                val jsmsg = gs.toJson(msg)
                //send
                */

                // Use try-with-resources pattern to ensure resources are closed
                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("MessageNetworkHandler", "Message sent successfully")
                        } else {
                            Log.e("MessageNetworkHandler", "Failed to send message: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MessageNetworkHandler", "Failed to send message, connection error: ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e("MessageNetworkHandler", "Failed to send message to ${address.hostAddress}")
            }
        }
    }

    companion object {
        /**
         * Processes an incoming message and routes it to the correct conversation.
         *
         * @param chatMessage The message content received.
         * @param time The timestamp when the message was received.
         * @param senderIp The IP address of the sender.
         * @param incomingfile Optional file URI attached to the message.
         * @return The created [Message] object representing the incoming message.
         */
        fun handleIncomingMessage(
            chatMessage: String?,
            time: Long,
            senderIp: InetAddress,
            incomingfile: URI?
        ): Message {
            Log.d(
                "MessageNetworkHandler",
                "Handling incoming message: $chatMessage, from: ${senderIp.hostAddress}, has file: ${incomingfile != null}"
            )

            val ipStr = senderIp.hostAddress
            val user = runBlocking { GlobalApp.GlobalUserRepo.userRepository.getUserByIp(ipStr) }
            val sender = user?.name ?: "Unknown"

            //determine the correct chat name
            val userUuid = when {
                TestDeviceService.isOnlineTestDevice(senderIp) ->
                    "test-device-uuid"

                ipStr == TestDeviceService.TEST_DEVICE_IP_OFFLINE || user?.name == TestDeviceService.TEST_DEVICE_NAME_OFFLINE ->
                    "offline-test-device-uuid"

                else -> user?.uuid ?: "unknown-${senderIp.hostAddress}"
            }

            val localUuid = GlobalApp.GlobalUserRepo.prefs.getString("UUID", null) ?: "local-user"
            val chatName = ConversationUtils.createConversationId(localUuid, userUuid)

            Log.d(
                "MessageNetworkHandler",
                "Creating message with chat name: $chatName, sender: $sender"
            )

            //Create the message
            val message = Message(
                id = 0,
                dateReceived = time,
                content = chatMessage ?: "Error! No message found.",
                sender = sender,
                chat = chatName,
                file = incomingfile//will be null for reg text messages
            )

            //update convo with new message
            if (user != null) {
                try {
                    //get/create convo
                    val conversation = runBlocking {
                        GlobalApp.GlobalUserRepo.conversationRepository.getOrCreateConversation(
                            localUuid = localUuid,
                            remoteUser = user
                        )
                    }

                    //update convo with the new message
                    runBlocking {
                        GlobalApp.GlobalUserRepo.conversationRepository.updateWithMessage(
                            conversationId = conversation.id,
                            message = message
                        )
                    }

                    Log.d("MessageNetworkHandler", "Updated conversation with new message")
                } catch (e: Exception) {
                    Log.e("MessageNetworkHandler", "Failed to update conversation", e)
                }
            }
            return message
        }

        /**
         * Shows a notification for an incoming message and routes to the chat screen.
         *
         * @param conversation The conversation to which the message belongs.
         * @param message The message to display in the notification.
         * @param senderIp The IP address of the sender.
         */
        private fun showMessageNotification(
            conversation: Conversation,
            message: Message,
            senderIp: InetAddress
        ) {
            try {
                // get context
                val context = try {
                    GlobalApp.GlobalUserRepo.userRepository.javaClass.classLoader?.loadClass("com.greybox.projectmesh.GlobalApp")
                        ?.getDeclaredField("INSTANCE")?.get(null) as? Context
                        ?: throw Exception("Cannot get application context")
                } catch (e: Exception) {
                    Log.e("MessageNetworkHandler", "Failed to get application context", e)
                    return
                }

                // Determine title and content based on whether there's a file
                val hasFile = message.file != null
                val title = if (hasFile) "New message with file" else "New message"
                val content = "From ${conversation.userName}: ${message.content}"

                // Create intent that routes to the chat screen
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = "OPEN_CHAT_SCREEN"
                    putExtra("ip", senderIp.hostAddress)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    message.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val channelId = "file_receive_channel"

                val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_SOUND)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(message.hashCode(), notification)

                Log.d("MessageNetworkHandler", "Showed notification for message with file")
            } catch (e: Exception) {
                Log.e("MessageNetworkHandler", "Failed to show notification", e)
            }
        }
    }

}
