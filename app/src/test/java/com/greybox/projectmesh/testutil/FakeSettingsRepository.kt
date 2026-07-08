package com.greybox.projectmesh.testutil

import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory SettingsRepository: same setter->flow behavior as
 * SharedPrefsSettingsRepository, minus the prefs I/O. A hand-written fake
 * (not a mock) because tests exercise its statefulness — a setter call must
 * be observable through the corresponding flow.
 *
 * Constructor defaults are the production defaults; seed tests pass
 * NON-default values so a correct seed is distinguishable from untouched
 * default state.
 */
class FakeSettingsRepository(
    initialTheme: AppTheme = AppTheme.SYSTEM,
    initialLanguage: String = "en",
    initialDeviceName: String = "",
    initialAutoFinish: Boolean = false,
    initialSaveToFolder: String = "",
    initialHasRunBefore: Boolean = false,
    initialConcurrencyKnown: Boolean = false,
    initialConcurrencySupported: Boolean = false,
    override var localUuid: String? = "local-user",
) : SettingsRepository {

    private val _theme = MutableStateFlow(initialTheme)
    override val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _languageCode = MutableStateFlow(initialLanguage)
    override val languageCode: StateFlow<String> = _languageCode.asStateFlow()

    private val _deviceName = MutableStateFlow(initialDeviceName)
    override val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _autoFinish = MutableStateFlow(initialAutoFinish)
    override val autoFinish: StateFlow<Boolean> = _autoFinish.asStateFlow()

    private val _saveToFolder = MutableStateFlow(initialSaveToFolder)
    override val saveToFolder: StateFlow<String> = _saveToFolder.asStateFlow()

    private val _hasRunBefore = MutableStateFlow(initialHasRunBefore)
    override val hasRunBefore: StateFlow<Boolean> = _hasRunBefore.asStateFlow()

    private val _concurrencyKnown = MutableStateFlow(initialConcurrencyKnown)
    override val concurrencyKnown: StateFlow<Boolean> = _concurrencyKnown.asStateFlow()

    private val _concurrencySupported = MutableStateFlow(initialConcurrencySupported)
    override val concurrencySupported: StateFlow<Boolean> = _concurrencySupported.asStateFlow()

    override fun setTheme(theme: AppTheme) { _theme.value = theme }
    override fun setLanguage(languageCode: String) { _languageCode.value = languageCode }
    override fun setDeviceName(deviceName: String) { _deviceName.value = deviceName }
    override fun setAutoFinish(autoFinish: Boolean) { _autoFinish.value = autoFinish }
    override fun setSaveToFolder(path: String) { _saveToFolder.value = path }
    override fun setHasRunBefore(value: Boolean) { _hasRunBefore.value = value }

    override fun setConcurrency(known: Boolean, supported: Boolean) {
        _concurrencyKnown.value = known
        _concurrencySupported.value = supported
    }
}
