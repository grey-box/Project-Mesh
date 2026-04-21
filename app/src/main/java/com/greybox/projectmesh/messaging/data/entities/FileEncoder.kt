package com.greybox.projectmesh.messaging.data.entities

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.net.InetAddress
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL

class FileEncoder {

    @OptIn(ExperimentalEncodingApi::class)
    fun encodebase64(ctxt: Context, inputuri: Uri): String? {
        return try {
            val encodedstrm: InputStream? = ctxt.contentResolver.openInputStream(inputuri)
            val bytes = encodedstrm?.readBytes()
            encodedstrm?.close()
            encodeBytesBase64(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            "Cannot encode file"
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    internal fun encodeBytesBase64(bytes: ByteArray?): String? {
        return if (bytes != null) {
            Base64.encode(bytes)
        } else {
            "Cannot encode file"
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decodeBase64(inputbase64: String, output: File): File {
        val decodedfilebytes = Base64.decode(inputbase64)
        val decodedstrm = FileOutputStream(output)
        decodedstrm.write(decodedfilebytes)
        decodedstrm.close()
        return output
    }

    fun sendImage(imageURI: Uri?, tgtaddress: InetAddress, tgtport: Int, appctxt: Context): Boolean {
        try {
            if (imageURI != null) {
                val fp = encodebase64(appctxt, imageURI)
                if (!fp.equals("Cannot encode file")) {
                    val efp = URLEncoder.encode(fp, "UTF-8")
                    val connection =
                        URL("http://${tgtaddress.hostAddress}:${tgtport}/upload?file=$efp").openConnection() as HttpURLConnection
                    val request = "POST"
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
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
