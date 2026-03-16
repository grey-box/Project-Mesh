package com.greybox.projectmesh

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.extension.deviceInfo
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MNetLoggerAndroidTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        mockkStatic("com.greybox.projectmesh.extension.ContextExtKt")
        every { Log.v(any(), any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.i(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.wtf(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic("com.greybox.projectmesh.extension.ContextExtKt")
        clearAllMocks()
    }

    @Test
    fun invoke_belowMinLogLevel_isIgnored() {
        val logger = MNetLoggerAndroid(deviceInfo = "device", minLogLevel = Log.INFO)

        logger.invoke(Log.DEBUG, "debug", null)

        assertEquals(emptyList<com.ustadmobile.meshrabiya.log.LogLine>(), latestLogs(logger))
    }

    @Test
    fun invoke_prependsNewestLogsAndTrimsHistory() {
        val logger = MNetLoggerAndroid(
            deviceInfo = "device",
            minLogLevel = Log.VERBOSE,
            logHistoryLines = 2
        )

        logger.invoke(Log.INFO, "first", null)
        logger.invoke(Log.ERROR, "second", IllegalStateException("boom"))
        logger.invoke(Log.WARN, "third", null)

        val logs = latestLogs(logger)
        assertEquals(2, logs.size)
        assertEquals("third", logs[0].line)
        assertTrue(logs[1].line.contains("second"))
        assertTrue(logs[1].line.contains("boom"))
    }

    @Test
    fun invoke_whenLogFileConfigured_writesSessionHeaderAndMessages() {
        val logFile = File.createTempFile("mesh-logger", ".log").apply {
            delete()
            deleteOnExit()
        }
        val logger = MNetLoggerAndroid(
            deviceInfo = "Device Info",
            minLogLevel = Log.VERBOSE,
            logFile = logFile
        )

        logger.invoke(Log.INFO, "persisted", null)

        val text = eventuallyRead(logFile)
        assertTrue(text.contains("Meshrabiya Session start"))
        assertTrue(text.contains("Device Info"))
        assertTrue(text.contains("persisted"))
    }

    @Test
    fun exportAsString_includesContextDeviceInfoAndLogs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        every { context.deviceInfo() } returns "CTX INFO\n"
        val logger = MNetLoggerAndroid(deviceInfo = "ctor info")
        logger.invoke(Log.INFO, "export me", null)

        val export = logger.exportAsString(context)

        assertTrue(export.contains("CTX INFO"))
        assertTrue(export.contains("==Logs=="))
        assertTrue(export.contains("export me"))
    }

    private fun latestLogs(logger: MNetLoggerAndroid) = kotlinx.coroutines.runBlocking {
        logger.recentLogs.first()
    }

    private fun eventuallyRead(file: File): String {
        repeat(50) {
            if (file.exists()) {
                val text = file.readText()
                if (text.contains("persisted")) {
                    return text
                }
            }
            Thread.sleep(50)
        }
        return if (file.exists()) file.readText() else ""
    }
}
