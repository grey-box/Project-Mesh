package com.greybox.projectmesh.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.greybox.projectmesh.server.AppServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidReceivedFileSaver(private val context: Context) : ReceivedFileSaver {
    override suspend fun save(
        transfer: AppServer.IncomingTransferInfo,
        destination: String,
    ): FileSaveResult = withContext(Dispatchers.IO) {
        val source = transfer.file
        if (source == null || !source.isFile) {
            return@withContext FileSaveResult.FILE_UNAVAILABLE
        }

        val name = transfer.name
        if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) {
            return@withContext FileSaveResult.SAVE_FAILED
        }

        try {
            when {
                destination.startsWith("content://") -> saveToSelectedFolder(source, name, destination)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToDownloads(source, name)
                else -> saveToPath(source, name, destination)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to save received file")
            FileSaveResult.SAVE_FAILED
        }
    }

    private fun saveToSelectedFolder(source: File, name: String, destination: String): FileSaveResult {
        val tree = Uri.parse(destination)
        val directory = DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        val resolver = context.contentResolver
        val target = DocumentsContract.createDocument(
            resolver, directory, "application/octet-stream", name,
        ) ?: return FileSaveResult.SELECTED_FOLDER_FAILED
        try {
            copyToUri(source, target)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
        return FileSaveResult.SUCCESS
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(source: File, name: String): FileSaveResult {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Project Mesh")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return FileSaveResult.SAVE_FAILED
        try {
            copyToUri(source, target)
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            if (resolver.update(target, ready, null, null) == 0) {
                throw IOException("Could not publish the saved file")
            }
        } catch (e: Exception) {
            runCatching { resolver.delete(target, null, null) }
            throw e
        }
        return FileSaveResult.SUCCESS
    }

    private fun copyToUri(source: File, target: Uri) {
        val output = context.contentResolver.openOutputStream(target)
            ?: throw IOException("Could not open the destination")
        output.use { destination ->
            source.inputStream().use { it.copyTo(destination) }
        }
    }

    private fun saveToPath(source: File, name: String, destination: String): FileSaveResult {
        val directory = File(destination)
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            return FileSaveResult.FOLDER_CREATE_FAILED
        }
        val target = File(directory, name)
        val temporary = File.createTempFile("mesh-", ".tmp", directory)
        try {
            source.copyTo(temporary, overwrite = true)
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        return FileSaveResult.SUCCESS
    }
}