package com.greybox.projectmesh.util

import android.content.Context
import java.util.Locale

object LocaleUtil {
    fun applyLocale(context: Context, languageCode: String): Locale {
        val locale = Locale(languageCode)
        val config = context.resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        return locale
    }
}