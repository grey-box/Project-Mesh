package com.greybox.projectmesh.utils

import timber.log.Timber

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

    fun d(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag(buildTag(tag)).d(message)
        }
    }

    fun i(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag(buildTag(tag)).i(message)
        }
    }

    fun w(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag(buildTag(tag)).w(message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (LOGGING_ENABLED) {
            if (throwable != null) {
                Timber.tag(buildTag(tag)).e(throwable, message)
            } else {
                Timber.tag(buildTag(tag)).e(message)
            }
        }
    }

    // Log important events that should be visible even in production
    fun critical(tag: String, message: String, throwable: Throwable? = null) {
        val criticalTag = buildCriticalTag(tag)
        if (throwable != null) {
            Timber.tag(criticalTag).e(throwable, message)
        } else {
            Timber.tag(criticalTag).e(message)
        }
    }
}