package com.greybox.projectmesh.utils

import android.util.Log

/**
 * Centralized logging utility for the app.
 * Provides consistent logging with standardized tags and can be disabled in production.
 */
object Logger {
    private const val LOGGING_ENABLED = true
    private const val TAG_PREFIX = "MeshChat_"

    /**
     * Logs a debug-level message.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     */
    fun d(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Log.d("$TAG_PREFIX$tag", message)
        }
    }

    /**
     * Logs an info-level message.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     */
    fun i(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Log.i("$TAG_PREFIX$tag", message)
        }
    }

    /**
     * Logs a warning-level message.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     */
    fun w(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Log.w("$TAG_PREFIX$tag", message)
        }
    }

    /**
     * Logs an error-level message.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     * @param throwable Optional exception to include in the log output.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (LOGGING_ENABLED) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX$tag", message)
            }
        }
    }

    /**
     * Logs high-importance errors that should always appear even in production.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     * @param throwable Optional exception to include in the log output.
     */
    fun critical(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$TAG_PREFIX${tag}_CRITICAL", message, throwable)
        } else {
            Log.e("$TAG_PREFIX${tag}_CRITICAL", message)
        }
    }
}
