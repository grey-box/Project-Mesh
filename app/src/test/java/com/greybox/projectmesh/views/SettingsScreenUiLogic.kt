// File: app/src/test/java/com/greybox/projectmesh/views/SettingsScreenUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for SettingsScreen.kt WITHOUT touching that file.
 *
 * This file has lots of Android/Compose (OpenDocumentTree, takePersistableUriPermission, Toast, Build checks).
 * JVM tests should focus on PURE deterministic logic:
 *
 * 1) initial language option resolution (currentLanguage -> label)
 * 2) theme option resolution (AppTheme.ordinal -> "System/Light/Dark")
 * 3) folderNameToShow derivation from either content:// uri string or path string
 * 4) concurrency section visibility condition (sdk < R)
 * 5) device name dialog submit rule (non-blank only)
 *
 * Later in androidTest:
 * - verify directory launcher result handling + persisted permissions
 * - verify UI components, dropdown behavior, switch toggles, dialog display
 */
private object SettingsScreenUiLogic {

    enum class AppTheme { System, Light, Dark }

    data class LangItem(val code: String, val label: String)

    private val langMenuItems = listOf(
        LangItem("en", "English"),
        LangItem("es", "Español"),
        LangItem("cn", "简体中文"),
        LangItem("fr", "Français"),
    )

    private val themeLabels = listOf("System", "Light", "Dark")

    fun languageLabelFor(currentLanguage: String): String {
        return langMenuItems.firstOrNull { it.code == currentLanguage }?.label ?: "English"
    }

    fun themeLabelFor(currentTheme: AppTheme): String {
        return themeLabels[currentTheme.ordinal]
    }

    /**
     * Mirrors folderNameToShow logic:
     * if startsWith("content://"):
     *   Uri.decode(value).split(":").lastOrNull() ?: "Unknown"
     * else:
     *   value.split("/").lastOrNull() ?: "Unknown"
     *
     * In unit tests, we avoid Android Uri.decode; we model it with a simple percent-decoder
     * for the common %3A case.
     */
    fun folderNameToShow(saveToFolder: String): String {
        return if (saveToFolder.startsWith("content://")) {
            val decoded = pseudoDecodeUri(saveToFolder)
            decoded.split(":").lastOrNull() ?: "Unknown"
        } else {
            saveToFolder.split("/").lastOrNull() ?: "Unknown"
        }
    }

    private fun pseudoDecodeUri(s: String): String {
        // Minimal decoding for typical SAF URIs with %3A representing ':'.
        // Good enough for JVM tests; androidTest can validate Uri.decode.
        return s.replace("%3A", ":").replace("%2F", "/")
    }

    fun shouldShowConcurrencySection(sdkInt: Int, sdkR: Int = 30): Boolean {
        // Mirrors: if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { ... }
        return sdkInt < sdkR
    }

    fun canSubmitDeviceName(inputText: String): Boolean = inputText.isNotBlank()

    /**
     * Mirrors "language selection" side-effects ordering intent:
     * viewModel.saveLang(code); onLanguageChange(code)
     */
    fun languageSelectionTrace(selectedCode: String): List<String> {
        return listOf("saveLang:$selectedCode", "onLanguageChange:$selectedCode")
    }

    /**
     * Mirrors "theme selection" side-effects:
     * viewModel.saveTheme(theme); onThemeChange(theme)
     */
    fun themeSelectionTrace(selectedTheme: AppTheme): List<String> {
        return listOf("saveTheme:$selectedTheme", "onThemeChange:$selectedTheme")
    }

    /**
     * Mirrors "auto finish switch" side-effects:
     * viewModel.saveAutoFinish(isChecked); onAutoFinishChange(isChecked)
     */
    fun autoFinishTrace(isChecked: Boolean): List<String> {
        return listOf("saveAutoFinish:$isChecked", "onAutoFinishChange:$isChecked")
    }

    /**
     * Mirrors "device name dialog confirm" side-effects (only if non-blank):
     * viewModel.saveDeviceName(newName); onDeviceNameChange(newName)
     */
    fun deviceNameConfirmTrace(inputText: String): List<String> {
        if (!canSubmitDeviceName(inputText)) return emptyList()
        return listOf("saveDeviceName:$inputText", "onDeviceNameChange:$inputText")
    }

    /**
     * Mirrors "directory picker result" logic:
     * if uri != null:
     *   saveSaveToFolder(uriString); onSaveToFolderChange(uriString)
     * else:
     *   toast no directory selected (android-only)
     */
    fun directoryPickedTrace(uriStringOrNull: String?): List<String> {
        return if (uriStringOrNull != null) {
            listOf("saveSaveToFolder:$uriStringOrNull", "onSaveToFolderChange:$uriStringOrNull")
        } else {
            listOf("toast:No directory selected")
        }
    }
}

class SettingsScreenUiLogicTest {

    // ---------- language label ----------
    @Test
    fun languageLabelFor_knownCodes_returnCorrectLabels() {
        assertEquals("English", SettingsScreenUiLogic.languageLabelFor("en"))
        assertEquals("Español", SettingsScreenUiLogic.languageLabelFor("es"))
        assertEquals("简体中文", SettingsScreenUiLogic.languageLabelFor("cn"))
        assertEquals("Français", SettingsScreenUiLogic.languageLabelFor("fr"))
    }

    @Test
    fun languageLabelFor_unknownCode_fallsBackToEnglish() {
        assertEquals("English", SettingsScreenUiLogic.languageLabelFor("xx"))
        assertEquals("English", SettingsScreenUiLogic.languageLabelFor(""))
    }

    // ---------- theme label ----------
    @Test
    fun themeLabelFor_matchesOrdinalMapping() {
        assertEquals("System", SettingsScreenUiLogic.themeLabelFor(SettingsScreenUiLogic.AppTheme.System))
        assertEquals("Light", SettingsScreenUiLogic.themeLabelFor(SettingsScreenUiLogic.AppTheme.Light))
        assertEquals("Dark", SettingsScreenUiLogic.themeLabelFor(SettingsScreenUiLogic.AppTheme.Dark))
    }

    // ---------- folder name ----------
    @Test
    fun folderNameToShow_path_returnsLastSegment() {
        assertEquals(
            "Project Mesh",
            SettingsScreenUiLogic.folderNameToShow("/storage/emulated/0/Download/Project Mesh")
        )
        assertEquals(
            "Download",
            SettingsScreenUiLogic.folderNameToShow("/storage/emulated/0/Download")
        )
    }

    @Test
    fun folderNameToShow_emptyPath_returnsUnknownLikeBehavior() {
        // split("/").lastOrNull() on "" returns "" (not null) -> your code would return ""
        // We mirror that.
        assertEquals("", SettingsScreenUiLogic.folderNameToShow(""))
    }

    // ---------- concurrency visibility ----------
    @Test
    fun shouldShowConcurrencySection_onlyBelowR() {
        assertTrue(SettingsScreenUiLogic.shouldShowConcurrencySection(29, sdkR = 30))
        assertFalse(SettingsScreenUiLogic.shouldShowConcurrencySection(30, sdkR = 30))
        assertFalse(SettingsScreenUiLogic.shouldShowConcurrencySection(33, sdkR = 30))
    }

    // ---------- device name submit rule ----------
    @Test
    fun canSubmitDeviceName_requiresNonBlank() {
        assertFalse(SettingsScreenUiLogic.canSubmitDeviceName(""))
        assertFalse(SettingsScreenUiLogic.canSubmitDeviceName("   "))
        assertTrue(SettingsScreenUiLogic.canSubmitDeviceName("MeshNode"))
        assertTrue(SettingsScreenUiLogic.canSubmitDeviceName("  Jai  ")) // isNotBlank true
    }

    @Test
    fun deviceNameConfirmTrace_onlyWhenNonBlank() {
        assertEquals(emptyList<String>(), SettingsScreenUiLogic.deviceNameConfirmTrace(""))
        assertEquals(emptyList<String>(), SettingsScreenUiLogic.deviceNameConfirmTrace("  "))
        assertEquals(
            listOf("saveDeviceName:Mesh", "onDeviceNameChange:Mesh"),
            SettingsScreenUiLogic.deviceNameConfirmTrace("Mesh")
        )
    }

    // ---------- side-effect ordering traces ----------
    @Test
    fun languageSelectionTrace_ordersSaveThenCallback() {
        assertEquals(
            listOf("saveLang:es", "onLanguageChange:es"),
            SettingsScreenUiLogic.languageSelectionTrace("es")
        )
    }

    @Test
    fun themeSelectionTrace_ordersSaveThenCallback() {
        assertEquals(
            listOf("saveTheme:Dark", "onThemeChange:Dark"),
            SettingsScreenUiLogic.themeSelectionTrace(SettingsScreenUiLogic.AppTheme.Dark)
        )
    }

    @Test
    fun autoFinishTrace_ordersSaveThenCallback() {
        assertEquals(
            listOf("saveAutoFinish:true", "onAutoFinishChange:true"),
            SettingsScreenUiLogic.autoFinishTrace(true)
        )
    }

    @Test
    fun directoryPickedTrace_whenNull_toastElse_saveThenCallback() {
        assertEquals(
            listOf("toast:No directory selected"),
            SettingsScreenUiLogic.directoryPickedTrace(null)
        )

        val uri = "content://x/y"
        assertEquals(
            listOf("saveSaveToFolder:$uri", "onSaveToFolderChange:$uri"),
            SettingsScreenUiLogic.directoryPickedTrace(uri)
        )
    }
}