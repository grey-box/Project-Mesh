package com.greybox.projectmesh.utils

import android.util.Log

/**
 * Centralized logging utility for the app.
 * Provides consistent logging with standardized tags and can be disabled in production.
 */
object Logger {
    internal const val TAG_PREFIX = "MeshChat_"
    private const val LOGGING_ENABLED = true

    internal fun buildTag(tag: String): String {
        return "$TAG_PREFIX$tag"
    }

    internal fun buildCriticalTag(tag: String): String {
        return "${TAG_PREFIX}${tag}_CRITICAL"
    }

    /**
     * Logs a debug-level message.
     *
     * @param tag The log tag used to identify the source.
     * @param message The message to log.
     */
    fun d(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Log.d(buildTag(tag), message)
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
            Log.i(buildTag(tag), message)
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
            Log.w(buildTag(tag), message)
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
                Log.e(buildTag(tag), message, throwable)
            } else {
                Log.e(buildTag(tag), message)
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
        val criticalTag = buildCriticalTag(tag)
        if (throwable != null) {
            Log.e(criticalTag, message, throwable)
        } else {
            Log.e(criticalTag, message)
        }
    }
}
