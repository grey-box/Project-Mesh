package com.greybox.projectmesh.debug

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import timber.log.Timber
import java.lang.Exception
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.system.exitProcess

class CrashHandler(private val context: Context, private val defaultHandler: UncaughtExceptionHandler, private val activityToBeLaunched: Class<*>) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            launchActivity(context,activityToBeLaunched,throwable)
            exitProcess(status = 1)
        } catch (e: Exception)
        {
            defaultHandler.uncaughtException(thread,throwable)
        }
    }

    private fun launchActivity(applicationContext: Context, activity: Class<*>, exception: Throwable)
    {
        val crashIntent = Intent(applicationContext, activity).also {
            it.putExtra(CRASH_DATA_KEY, Gson().toJson(exception))
            Timber.tag("Project Mesh Error").e(exception, "Error: ");
        }

        crashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        crashIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        applicationContext.startActivity(crashIntent)
    }

    companion object {
        private const val CRASH_DATA_KEY = "CrashData"

        private data class CrashPayload(
            val detailMessage: String? = null,
            val message: String? = null,
        )

        fun init(applicationContext: Context, activityToBeLaunched: Class<*>)
        {
            val handler = CrashHandler(applicationContext,Thread.getDefaultUncaughtExceptionHandler() as UncaughtExceptionHandler, activityToBeLaunched)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        fun getThrowableFromIntent(intent: Intent): Throwable?
        {
            val crashData = intent.getStringExtra(CRASH_DATA_KEY)?.takeIf { it.isNotBlank() } ?: return null
            return try {
                Gson().fromJson(crashData, Throwable::class.java)
            } catch (_: Exception) {
                try {
                    val payload = Gson().fromJson(crashData, CrashPayload::class.java)
                    payload.detailMessage?.takeIf { it.isNotBlank() }?.let(::Throwable)
                        ?: payload.message?.takeIf { it.isNotBlank() }?.let(::Throwable)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
