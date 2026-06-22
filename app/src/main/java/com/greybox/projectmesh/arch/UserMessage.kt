package com.greybox.projectmesh.arch

import androidx.annotation.StringRes

/*
 * the one-shot-message-as-state helper, reused by any
 * screen that needs a toast/snackbar:

 *  A transient user-facing message held in UI state. [id] lets the UI key a
 *  LaunchedEffect so the same message shows exactly once. 
 */


data class UserMessage(@StringRes val textRes: Int, val id: Long = System.nanoTime())

