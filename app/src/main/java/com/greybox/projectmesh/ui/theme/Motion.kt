package com.greybox.projectmesh.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// Reduced-motion support (WCAG 2.1 SC 2.3.3, Animation from Interactions).
//
// Android exposes a system-wide "Remove animations" switch under
// Settings > Accessibility. Compose honours it for a few of its own built-in
// transitions, but never for animations we write ourselves, so a user who turned
// animations off because motion makes them nauseous or dizzy still received every
// hand-rolled animation in this app. Reading the setting is the only way to respect it.
//
// ANIMATOR_DURATION_SCALE is 0f once the user disables animations and 1f normally. It can
// also be 0.5f or 2f from developer options; those are speed preferences, not an
// accessibility request, so only an exact 0f counts as "remove motion".
//
// Read once per composition rather than observed: the platform requires a process restart
// for this setting to take effect app-wide anyway, so polling it would buy nothing.
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
