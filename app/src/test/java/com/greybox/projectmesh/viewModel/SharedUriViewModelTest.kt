package com.greybox.projectmesh.viewModel

import android.net.Uri
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SharedUriViewModelTest {

    @Before
    fun setUp() {
        // Avoid any accidental android-main access in unit tests
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(Looper::class)
    }

    @Test
    fun uris_initially_empty() = runBlocking {
        val vm = SharedUriViewModel()
        assertEquals(emptyList<Uri>(), vm.uris.first())
    }

    @Test
    fun setUris_updates_stateflow_value() = runBlocking {
        val vm = SharedUriViewModel()
        val u1 = mockk<Uri>(relaxed = true)
        val u2 = mockk<Uri>(relaxed = true)

        vm.setUris(listOf(u1, u2))

        val v = vm.uris.first()
        assertEquals(2, v.size)
        assertEquals(u1, v[0])
        assertEquals(u2, v[1])
    }
}
