package com.greybox.projectmesh.utils

import timber.log.Timber

/**
 * Centralized logging utility for the app.
 * Provides consistent logging with standardized tags and can be disabled in production.
 */

object Logger {
    private const val LOGGING_ENABLED = true
    private const val TAG_PREFIX = "MeshChat_"

    fun d(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag("$TAG_PREFIX$tag").d(message)
        }
    }

    fun i(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag("$TAG_PREFIX$tag").i(message)
        }
    }

    fun w(tag: String, message: String) {
        if (LOGGING_ENABLED) {
            Timber.tag("$TAG_PREFIX$tag").w(message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (LOGGING_ENABLED) {
            if (throwable != null) {
                Timber.tag("$TAG_PREFIX$tag").e(throwable, message)
            } else {
                Timber.tag("$TAG_PREFIX$tag").e(message)
            }
        }
    }

    // Log important events that should be visible even in production
    fun critical(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Timber.tag("$TAG_PREFIX${tag}_CRITICAL").e(throwable, message)
        } else {
            Timber.tag("$TAG_PREFIX${tag}_CRITICAL").e(message)
        }
    }
}