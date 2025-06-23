package com.greybox.projectmesh.logging

import android.util.Log
import timber.log.Timber

class ReleaseTree : Timber.Tree() {
    @Suppress("LogNotTimber")
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Log WARN and ERROR only to avoid leaking debug info in release
        if (priority == Log.ERROR || priority == Log.WARN) {
            val safeTag = tag ?: "ReleaseTree"
            Log.println(priority, safeTag, message)

            // Log Exceptions if present
            t?.let {
                Log.e(safeTag, "Exception: $message", it)
            }
        }
    }
}