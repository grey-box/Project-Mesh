package com.greybox.projectmesh.util

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    @Test
    @Config(sdk = [26], manifest = Config.NONE)
    fun createNotificationChannel_onApi26Plus_createsExpectedChannel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationHelper.createNotificationChannel(context)

        val channel = manager.getNotificationChannel("file_receive_channel")
        assertNotNull(channel)
        assertEquals("File Receive Notifications", channel?.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        assertEquals("Notifications for receiving file", channel?.description)
    }

    @Test
    @Config(sdk = [25], manifest = Config.NONE)
    fun createNotificationChannel_onApiBelow26_doesNothing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationHelper.createNotificationChannel(context)

        val shadow = Shadows.shadowOf(manager)
        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    @Config(sdk = [29], manifest = Config.NONE)
    fun showFileReceivedNotification_postsExpectedNotification_withIntentExtras() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        NotificationHelper.showFileReceivedNotification(context, "report.pdf")

        val shadow = Shadows.shadowOf(manager)
        val posted = shadow.allNotifications
        assertEquals(1, posted.size)

        val notification = posted.single()
        assertEquals("File Received", notification.extras.getCharSequence("android.title")?.toString())
        assertEquals("Tap to view report.pdf", notification.extras.getCharSequence("android.text")?.toString())
        assertTrue(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)

        val pendingIntent = notification.contentIntent
        assertNotNull(pendingIntent)

        val savedIntent = Shadows.shadowOf(pendingIntent).savedIntent
        assertNotNull(savedIntent)
        assertEquals("OPEN_RECEIVE_SCREEN", savedIntent?.action)
        assertEquals("receive", savedIntent?.getStringExtra("navigateTo"))
        assertEquals(true, savedIntent?.getBooleanExtra("from_notification", false))
    }
}
