package com.greybox.projectmesh

import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.greybox.projectmesh.data.AndroidReceivedFileSaver
import com.greybox.projectmesh.data.FileSaveResult
import com.greybox.projectmesh.server.AppServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class ReceivedFileSaverDeviceTest {
    @Test
    fun savesExactBytesInDownloads() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val downloads = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val name = "mesh-test-${UUID.randomUUID()}.txt"
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(name)
        val source = File.createTempFile("mesh-test", ".txt", context.cacheDir)
        val contents = "Project Mesh Receive test".toByteArray()
        source.writeBytes(contents)
        val transfer = AppServer.IncomingTransferInfo(
            id = 1,
            fromHost = InetAddress.getLoopbackAddress(),
            name = name,
            status = AppServer.Status.COMPLETED,
            size = contents.size,
            file = source,
        )

        try {
            assertEquals(FileSaveResult.SUCCESS, AndroidReceivedFileSaver(context).save(transfer, ""))
            val columns = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.IS_PENDING,
                MediaStore.Downloads.RELATIVE_PATH,
            )
            requireNotNull(resolver.query(downloads, columns, selection, args, null)).use { rows ->
                assertTrue("The file should be present in Downloads", rows.moveToFirst())
                assertEquals(0, rows.getInt(1))
                assertEquals("Download/Project Mesh/", rows.getString(2))
                val saved = ContentUris.withAppendedId(downloads, rows.getLong(0))
                val actual = requireNotNull(resolver.openInputStream(saved)).use { it.readBytes() }
                assertArrayEquals(contents, actual)
            }
        } finally {
            resolver.delete(downloads, selection, args)
            source.delete()
        }
    }
}