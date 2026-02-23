package com.greybox.projectmesh.extension

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile

/**
 * Represents the name and size of a file referenced by a [Uri].
 *
 * @property name The display name of the file, or null if it cannot be determined.
 * @property size The size of the file in bytes, or -1 if unknown.
 */
data class UriNameAndSize(
    val name: String?,
    val size: Long,
)

/*
This is an extension function for ContentResolver class
It will return a UriNameAndSize object that contains the name and size of the file
Two Condition:
1. The uri is a file uri
2. The uri is a content uri
*/

/**
 * Retrieves the name and size of a file referenced by the given [uri].
 *
 * Supports both "file" scheme URIs and "content" scheme URIs.
 *
 * @receiver The [ContentResolver] used to query content URIs.
 * @param uri The [Uri] pointing to the file.
 * @return A [UriNameAndSize] object containing the file's name and size, or
 *         null name and -1 size if the information cannot be determined.
 */
fun ContentResolver.getUriNameAndSize(uri: Uri): UriNameAndSize {
    return if(uri.scheme == "file") {
        val uriFile = uri.toFile()
        UriNameAndSize(uriFile.name, uriFile.length())
    } else {
        query(
            uri, null, null, null, null
        )?.use { cursor ->
            var nameIndex = 0
            var sizeIndex = 0
            if(cursor.moveToFirst() &&
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).also { nameIndex = it } >= 1 &&
                cursor.getColumnIndex(OpenableColumns.SIZE).also { sizeIndex = it } >= 1
            ) {
                val size = if(cursor.isNull(sizeIndex)) { null } else {
                    cursor.getString(sizeIndex)
                }
                UriNameAndSize(cursor.getString(nameIndex), size?.toLong() ?: -1L)
            } else {
                null
            }
        } ?: UriNameAndSize(null, -1)
    }
}
