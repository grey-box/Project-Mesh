package com.greybox.projectmesh.arch

import androidx.annotation.StringRes

/**
 * The one-shot-message-as-state helper, reused by any screen that needs a
 * toast/snackbar.
 *
 * A transient user-facing message held in UI state. [id] lets the UI key a
 * LaunchedEffect so the same message shows exactly once.
 *
 * Delivery semantics (single slot, last-writer-wins, and the List queue as
 * the upgrade path) are documented in [StateViewModel]'s contract KDoc.
 */
data class UserMessage(@StringRes val textRes: Int, val id: Long = System.nanoTime())

