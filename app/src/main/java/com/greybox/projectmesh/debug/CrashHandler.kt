package com.greybox.projectmesh.debug

import android.R.layout
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.google.gson.Gson
import org.xml.sax.helpers.DefaultHandler
import java.lang.Exception
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.system.exitProcess

/**
 * Custom [Thread.UncaughtExceptionHandler] to handle uncaught exceptions in the app.
 *
 * This handler launches a specified activity when a crash occurs, passing the exception
 * details via an Intent, and then terminates the app.
 *
 * @param context The application context used to launch the crash activity.
 * @param defaultHandler The default uncaught exception handler to fallback on.
 * @param activityToBeLaunched The activity class to be launched when a crash occurs.
 */
class CrashHandler(
    private val context: Context,
    private val defaultHandler: UncaughtExceptionHandler,
    private val activityToBeLaunched: Class<*>
) : Thread.UncaughtExceptionHandler {

    /**
     * Handles uncaught exceptions thrown by any thread.
     *
     * @param thread The thread where the exception occurred.
     * @param throwable The uncaught exception.
     */
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            launchActivity(context, activityToBeLaunched, throwable)
            exitProcess(status = 1)
        } catch (e: Exception) {
            defaultHandler.uncaughtException(thread, throwable)
        }
    }

    /**
     * Launches the crash reporting activity with the exception details.
     *
     * @param applicationContext The context used to start the activity.
     * @param activity The activity class to launch.
     * @param exception The exception to pass to the activity.
     */
    private fun launchActivity(applicationContext: Context, activity: Class<*>, exception: Throwable) {
        val crashIntent = Intent(applicationContext, activity).also {
            it.putExtra("CrashData", Gson().toJson(exception))
            Log.e("Project Mesh Error", "Error: ", exception)
        }

        crashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        crashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        applicationContext.startActivity(crashIntent)
    }

    companion object {
        /**
         * Initializes the [CrashHandler] and sets it as the default uncaught exception handler.
         *
         * @param applicationContext The application context used to create the handler.
         * @param activityToBeLaunched The activity class to launch on crash.
         */
        fun init(applicationContext: Context, activityToBeLaunched: Class<*>) {
            val handler = CrashHandler(
                applicationContext,
                Thread.getDefaultUncaughtExceptionHandler() as UncaughtExceptionHandler,
                activityToBeLaunched
            )
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        /**
         * Retrieves a [Throwable] from an intent containing crash data.
         *
         * @param intent The intent containing serialized crash data.
         * @return The deserialized [Throwable], or null if parsing fails.
         */
        fun getThrowableFromIntent(intent: Intent): Throwable? {
            return try {
                Gson().fromJson(intent.getStringExtra("CrashData"), Throwable::class.java)
            } catch (e: Exception) {
                Log.e("CrashHandler", "getThrowableFromIntent: ", e)
                null
            }
        }
    }
}
