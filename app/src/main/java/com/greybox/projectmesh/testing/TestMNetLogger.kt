package com.greybox.projectmesh.testing

import android.util.Log
import com.ustadmobile.meshrabiya.log.MNetLogger

/**
 * Logger implementation for test devices.
 *
 * Redirects all log messages to Android's Log system with a fixed "TestDevice" tag.
 * Supports both direct message strings and lambda message providers.
 */
class TestMNetLogger : MNetLogger() {

    /**
     * Logs a message and optional exception with a given priority.
     *
     * @param priority the log priority (Log.VERBOSE, Log.DEBUG, etc.)
     * @param message the message to log
     * @param exception optional exception to log
     */
    override fun invoke(priority: Int, message: String, exception: Exception?) {
        Log.println(priority, "TestDevice", message)
        exception?.let {
            Log.println(priority, "TestDevice", it.toString())
        }
    }

    /**
     * Logs a lazily evaluated message and optional exception with a given priority.
     *
     * @param priority the log priority (Log.VERBOSE, Log.DEBUG, etc.)
     * @param message lambda returning the message to log
     * @param exception optional exception to log
     */
    override fun invoke(priority: Int, message: () -> String, exception: Exception?) {
        Log.println(priority, "TestDevice", message())
        exception?.let {
            Log.println(priority, "TestDevice", it.toString())
        }
    }
}
