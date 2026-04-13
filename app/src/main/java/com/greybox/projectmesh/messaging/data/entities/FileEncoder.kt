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

//Use this to encode files not just images
//Needs to be tested sometime
//Can I modify this so that Http transfer does the majority of the encoding?
class FileEncoder {//Made by Craig. Encodes via base64.

    @OptIn(ExperimentalEncodingApi::class)
    fun encodebase64(ctxt: Context, inputuri: Uri): String?{
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
            return "Cannot encode file"}

    }

    @OptIn(ExperimentalEncodingApi::class)//Made by Craig
    fun decodeBase64(inputbase64:String, output: File): File{//Decodes to a file. Uses base64
        val decodedfilebytes = Base64.decode(inputbase64)
        val decodedstrm = FileOutputStream(output)
        decodedstrm.write(decodedfilebytes)
        decodedstrm.close()
        return output
    }
    fun sendImage(imageURI: Uri?, tgtaddress: InetAddress, tgtport:Int, appctxt: Context): Boolean{//Testing sending images
        try{//we can utilize this if we opt not to use JSON
            if(imageURI != null){
            val fp = encodebase64(appctxt, imageURI)//encodes file to base64
            if(!fp.equals("Cannot encode file")) {
                val efp = URLEncoder.encode(fp, "UTF-8")//ensures that the file URI is utf-8 encoded
                val connection =
                    URL("http://${tgtaddress.hostAddress}:${tgtport}/upload?file=$efp").openConnection() as HttpURLConnection
                val request = "POST"//Specifies the request as a POST
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
            }} else {
                return false
            }
        }
        catch(e: Exception){
            e.printStackTrace()
            return false
        }
        return true
    }
}