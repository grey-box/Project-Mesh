package com.greybox.projectmesh.messaging.data.entities

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.greybox.projectmesh.GlobalApp
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
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import com.greybox.projectmesh.extension.getUriNameAndSize
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLDecoder

/**
 * Utility class for encoding and decoding files using Base64.
 *
 * This class provides functions to encode files to Base64 strings, decode
 * Base64 strings back to files, and send files over HTTP as encoded strings.
 */
class FileEncoder { //Made by Craig. Encodes via base64.

    /**
     * Encodes the file located at the given URI into a Base64 string.
     *
     * @param ctxt The application context used to access the content resolver.
     * @param inputuri The URI of the file to encode.
     * @return The Base64-encoded string of the file contents, or a message
     * indicating encoding failure.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodebase64(ctxt: Context, inputuri: Uri): String? {
        try {
            val encodedstrm: InputStream? = ctxt.contentResolver.openInputStream(inputuri)
            val bytes = encodedstrm?.readBytes()
            encodedstrm?.close()
            return if (bytes != null) {
                Base64.encode(bytes)
            } else {
                "Cannot encode file"
            }
        } catch(e: Exception){
            e.printStackTrace()
            return "Cannot encode file"
        }
    }

    /**
     * Decodes a Base64-encoded string and writes it to the specified file.
     *
     * @param inputbase64 The Base64 string to decode.
     * @param output The file to write the decoded bytes to.
     * @return The file containing the decoded data.
     */
    @OptIn(ExperimentalEncodingApi::class) //Made by Craig
    fun decodeBase64(inputbase64:String, output: File): File { //Decodes to a file. Uses base64
        val decodedfilebytes = Base64.decode(inputbase64)
        val decodedstrm = FileOutputStream(output)
        decodedstrm.write(decodedfilebytes)
        decodedstrm.close()
        return output
    }

    /**
     * Sends an image file to a target host and port using HTTP POST with Base64 encoding.
     *
     * @param imageURI The URI of the image to send. If null, the function returns false.
     * @param tgtaddress The target host's InetAddress.
     * @param tgtport The target port to send the image to.
     * @param appctxt The application context used to access content resolver streams.
     * @return True if the image was successfully sent, false otherwise.
     */
    fun sendImage(imageURI: Uri?, tgtaddress: InetAddress, tgtport:Int, appctxt: Context): Boolean { //Testing sending images
        try { //we can utilize this if we opt not to use JSON
            if(imageURI != null){
                val fp = encodebase64(appctxt, imageURI) //encodes file to base64
                if(!fp.equals("Cannot encode file")) {
                    val efp = URLEncoder.encode(fp, "UTF-8") //ensures that the file URI is utf-8 encoded
                    val connection =
                        URL("http://${tgtaddress.hostAddress}:${tgtport}/upload?file=$efp").openConnection() as HttpURLConnection
                    val request = "POST" //Specifies the request as a POST
                    connection.doOutput = true
                    connection.requestMethod = request
                    connection.setChunkedStreamingMode(0)
                    val instream = appctxt.contentResolver.openInputStream(imageURI)
                    val outstream = connection.outputStream
                    val readingbuffer = ByteArray(1024)
                    var finishedreading: Int
                    while (instream?.read(readingbuffer).also { finishedreading = it!! } != -1) {
                        outstream.write(readingbuffer, 0, finishedreading)
                    }
                    outstream.close()
                    instream?.close()

                } else {
                    return false
                }
            } else {
                return false
            }
        } catch(e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
