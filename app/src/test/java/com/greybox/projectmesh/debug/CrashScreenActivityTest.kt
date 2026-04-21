package com.greybox.projectmesh.debug

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CrashScreenActivityTest {

    @Test
    fun getThrowableFromIntent_returnsThrowable_whenValidJsonProvided() {
        // SAFE: minimal JSON, avoids Gson reflection into private fields.
        val json = """{"detailMessage":"boom-crash"}"""

        val intent = Intent().apply {
            putExtra("CrashData", json)
        }

        val parsed = CrashHandler.getThrowableFromIntent(intent)

        assertNotNull(parsed)
        assertEquals("boom-crash", parsed?.message)
    }

    @Test
    fun getThrowableFromIntent_returnsNull_whenInvalidJsonProvided() {
        val intent = Intent().apply {
            putExtra("CrashData", "{invalid-json}")
        }

        val parsed = CrashHandler.getThrowableFromIntent(intent)

        assertNull(parsed)
    }

    @Test
    fun getThrowableFromIntent_returnsNull_whenNoCrashDataProvided() {
        val intent = Intent()

        val parsed = CrashHandler.getThrowableFromIntent(intent)

        assertNull(parsed)
    }
}
