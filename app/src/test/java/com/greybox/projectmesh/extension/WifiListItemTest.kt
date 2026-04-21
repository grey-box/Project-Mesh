package com.greybox.projectmesh.extension

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class WifiListItemTest {

    @Before
    fun setUp() {
        val mockRepo = mockk<UserRepository>(relaxed = true)

        // Return null so WifiListItem falls back to "Unknown"
        coEvery { mockRepo.getUserByIp(any()) } returns null

        GlobalApp.GlobalUserRepo.userRepository = mockRepo
    }

    @Test
    fun wifiListItem_composesWithoutCrashing_whenOnClickIsNull() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val wifiEntry = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        every { wifiEntry.hopCount } returns 2
        every { wifiEntry.originatorMessage.pingTimeSum } returns 42

        activity.setContent {
            WifiListItem(
                wifiAddress = 0xC0A80101.toInt(),
                wifiEntry = wifiEntry,
                onClick = null
            )
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun wifiListItem_composesWithoutCrashing_whenOnClickIsProvided() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .get()

        val wifiEntry = mockk<VirtualNode.LastOriginatorMessage>(relaxed = true)
        every { wifiEntry.hopCount } returns 1
        every { wifiEntry.originatorMessage.pingTimeSum } returns 15

        var clickedAddress: String? = null

        activity.setContent {
            WifiListItem(
                wifiAddress = 0xC0A80101.toInt(),
                wifiEntry = wifiEntry,
                onClick = { clickedAddress = it }
            )
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
        assertNull(clickedAddress)
    }
}