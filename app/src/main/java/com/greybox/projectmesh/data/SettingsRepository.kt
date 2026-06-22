package com.greybox.projectmesh.data

import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import com.greybox.projectmesh.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for both SharedPreferences files:
 *  - "settings"          -> theme, language, device_name, auto_finish, save_to_folder, UUID, concurrency
 *  - "project_mesh_prefs" -> hasRunBefore (kept separate so existing installs don't re-onboard)
 *
 * Exposes StateFlows seeded from prefs; each setter writes prefs THEN updates the
 * flow. The repository is the ONLY writer of these keys, so no listener is needed.
 */

 interface SettingsRepository {
    val theme: StateFlow<AppTheme>
    val languageCode: StateFlow<String>
    val deviceName: StateFlow<String>
    val autoFinish: StateFlow<Boolean>
    val saveToFolder: StateFlow<String>
    val hasRunBefore: StateFlow<Boolean>
    val concurrencyKnown: StateFlow<Boolean>
    val concurrencySupported: StateFlow<Boolean>

    // written only once by OnboardingViewModel
    val localUuid: String?

    fun setTheme(theme: AppTheme)
    fun setLanguage(languageCode: String)
    fun setDeviceName(deviceName: String)
    fun setAutoFinish(autoFinish: Boolean)
    fun setSaveToFolder(path: String)
    fun setHasRunBefore(value: Boolean)
    fun setConcurrency(known: Boolean, supported: Boolean)
 }

 class SharedPrefsSettingsRepository(
    private val settingsPrefs: SharedPreferences,
    private val meshPrefs: SharedPreferences,
 ) : SettingsRepository {

       private val _theme = MutableStateFlow(loadTheme())
    override val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _languageCode =
        MutableStateFlow(settingsPrefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE)
    override val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    private val _deviceName =
        MutableStateFlow(settingsPrefs.getString(KEY_DEVICE_NAME, Build.MODEL) ?: Build.MODEL)
    override val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _autoFinish = MutableStateFlow(settingsPrefs.getBoolean(KEY_AUTO_FINISH, false))
    override val autoFinish: StateFlow<Boolean> = _autoFinish.asStateFlow()

    private val _saveToFolder = MutableStateFlow(loadSaveToFolder())
    override val saveToFolder: StateFlow<String> = _saveToFolder.asStateFlow()

    private val _hasRunBefore = MutableStateFlow(meshPrefs.getBoolean(KEY_HAS_RUN_BEFORE, false))
    override val hasRunBefore: StateFlow<Boolean> = _hasRunBefore.asStateFlow()

    private val _concurrencyKnown =
        MutableStateFlow(settingsPrefs.getBoolean(KEY_CONCURRENCY_KNOWN, false))
    override val concurrencyKnown: StateFlow<Boolean> = _concurrencyKnown.asStateFlow()

    private val _concurrencySupported =
        MutableStateFlow(settingsPrefs.getBoolean(KEY_CONCURRENCY_SUPPORTED, false))
    override val concurrencySupported: StateFlow<Boolean> = _concurrencySupported.asStateFlow()

    override val localUuid: String? get() = settingsPrefs.getString(KEY_UUID, null)
 
    
    
    override fun setTheme(theme: AppTheme) {
        settingsPrefs.edit().putString(KEY_THEME, theme.name).apply()
        _theme.value = theme
    }

    override fun setLanguage(languageCode: String) {
        settingsPrefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        _languageCode.value = languageCode
    }

    override fun setDeviceName(deviceName: String) { 
        settingsPrefs.edit().putString(KEY_DEVICE_NAME, deviceName).apply()
        _deviceName.value = deviceName
    }

    override fun setAutoFinish(autoFinish: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_AUTO_FINISH, autoFinish).apply()
        _autoFinish.value = autoFinish
    }

    override fun setSaveToFolder(path: String) {
        settingsPrefs.edit().putString(KEY_SAVE_TO_FOLDER, path).apply()
        _saveToFolder.value = path
    }

    override fun setHasRunBefore(value: Boolean) {
        meshPrefs.edit().putBoolean(KEY_HAS_RUN_BEFORE, value).apply()
        _hasRunBefore.value = value
    }

    override fun setConcurrency(known: Boolean, supported: Boolean) {
        settingsPrefs.edit()
            .putBoolean(KEY_CONCURRENCY_KNOWN, known)
            .putBoolean(KEY_CONCURRENCY_SUPPORTED, supported)
            .apply()
        _concurrencyKnown.value = known
        _concurrencySupported.value = supported
    }

    
    private fun loadTheme(): AppTheme {
        val name = settingsPrefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return AppTheme.valueOf(name)
    }

    private fun loadSaveToFolder(): String {
        val default = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Project Mesh"
        return settingsPrefs.getString(KEY_SAVE_TO_FOLDER, default) ?: default
    }

    companion object {
        private const val KEY_THEME = "app_theme"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_AUTO_FINISH = "auto_finish"
        private const val KEY_SAVE_TO_FOLDER = "save_to_folder"
        private const val KEY_UUID = "UUID"
        private const val KEY_CONCURRENCY_KNOWN = "concurrency_known"
        private const val KEY_CONCURRENCY_SUPPORTED = "concurrency_supported"
        private const val KEY_HAS_RUN_BEFORE = "hasRunBefore"
        private const val DEFAULT_LANGUAGE = "en"
    }

 
 }

