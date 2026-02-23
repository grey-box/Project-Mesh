package com.greybox.projectmesh.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.greybox.projectmesh.MainActivity
import com.greybox.projectmesh.R
import com.greybox.projectmesh.navigation.BottomNavItem

/**
 * Utility object for creating and showing system notifications related to file receiving.
 */
object NotificationHelper {

    // Notification channel identifiers
    private const val CHANNEL_ID = "file_receive_channel"
    private const val CHANNEL_NAME = "File Receive Notifications"

    /**
     * Creates the notification channel if the device runs Android O or higher.
     *
     * @param context Application context used to access the system NotificationManager
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for receiving file"
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Displays a notification that informs the user a file has been received.
     * Tapping the notification opens the Receive screen inside MainActivity.
     *
     * @param context Context for creating intents and notifications
     * @param fileName Name of the received file to display in the message
     */
    fun showFileReceivedNotification(context: Context, fileName: String) {

        // Intent that navigates user into MainActivity → Receive screen
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_RECEIVE_SCREEN"
            putExtra("navigateTo", BottomNavItem.Receive.route) // Set target screen
            putExtra("from_notification", true) // Skip permission prompt when opened from notif
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // PendingIntent for launching the activity from the notification
        val pendingIntent = PendingIntent.getActivity(
            context,
            1003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construct the actual notification UI
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("File Received")
            .setContentText("Tap to view $fileName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Trigger the notification
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1003, notification)
    }
}
