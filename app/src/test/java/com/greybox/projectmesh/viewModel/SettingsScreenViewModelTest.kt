package com.greybox.projectmesh.viewModel

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class SettingsScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var prefs: SharedPreferences
    private lateinit var di: DI

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_settings_vm", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        di = DI {
            bind<SharedPreferences>(tag = "settings") with singleton { prefs }
        }
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun init_whenPrefsEmpty_loadsDefaults() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        assertEquals(AppTheme.SYSTEM, vm.theme.value)
        assertEquals("System", vm.lang.value)
        assertEquals(Build.MODEL, vm.deviceName.value)
        assertFalse(vm.autoFinish.value)
        val expectedDefaultFolder =
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Project Mesh"
        assertEquals(expectedDefaultFolder, vm.saveToFolder.value)
    }

    @Test
    fun init_whenPrefsPopulated_loadsSavedValues() = runTest {
        val expectedDefaultFolder =
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Project Mesh"
        prefs.edit()
            .putString("app_theme", AppTheme.DARK.name)
            .putString("language", "es")
            .putString("device_name", "MyPhone")
            .putBoolean("auto_finish", true)
            .putString("save_to_folder", "$expectedDefaultFolder/custom")
            .commit()

        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        assertEquals(AppTheme.DARK, vm.theme.value)
        assertEquals("es", vm.lang.value)
        assertEquals("MyPhone", vm.deviceName.value)
        assertTrue(vm.autoFinish.value)
        assertEquals("$expectedDefaultFolder/custom", vm.saveToFolder.value)
    }

    @Test
    fun saveTheme_updatesFlowAndPrefs() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        vm.saveTheme(AppTheme.LIGHT)

        assertEquals(AppTheme.LIGHT, vm.theme.value)
        assertEquals(AppTheme.LIGHT.name, prefs.getString("app_theme", null))
    }

    @Test
    fun saveLang_updatesFlowAndPrefs() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        vm.saveLang("ko")

        assertEquals("ko", vm.lang.value)
        assertEquals("ko", prefs.getString("language", null))
    }

    @Test
    fun saveDeviceName_updatesFlowAndPrefs() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        vm.saveDeviceName("Device X")

        assertEquals("Device X", vm.deviceName.value)
        assertEquals("Device X", prefs.getString("device_name", null))
    }

    @Test
    fun saveAutoFinish_updatesFlowAndPrefs() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        vm.saveAutoFinish(true)

        assertTrue(vm.autoFinish.value)
        assertTrue(prefs.getBoolean("auto_finish", false))
    }

    @Test
    fun saveSaveToFolder_updatesFlowAndPrefs() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())
        val folder = "/storage/emulated/0/Download/Project Mesh"

        vm.saveSaveToFolder(folder)

        assertEquals(folder, vm.saveToFolder.value)
        assertEquals(folder, prefs.getString("save_to_folder", null))
    }

    @Test
    fun updateConcurrencySettings_writesBothKeys() = runTest {
        val vm = SettingsScreenViewModel(di, SavedStateHandle())

        vm.updateConcurrencySettings(concurrencyKnown = true, concurrencySupported = false)

        assertTrue(prefs.getBoolean("concurrency_known", false))
        assertFalse(prefs.getBoolean("concurrency_supported", true))
    }
}
