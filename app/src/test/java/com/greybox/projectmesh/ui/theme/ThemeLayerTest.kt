package com.greybox.projectmesh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests for the theme layer:
 *
 *  - Color constants in Color.kt
 *  - AppTheme enum in Theme.kt
 *  - Typography definition in Type.kt
 *
 * NOTE:
 *  - We do NOT run the ProjectMeshTheme composable here,
 *    because that requires a real Compose runtime / Android.
 *  - Instrumented tests will check the actual MaterialTheme
 *    behavior (dark/light, system theme, etc.).
 */
class ThemeLayerTest {

    // -----------------------------
    // Color constants (Color.kt)
    // -----------------------------

    @Test
    fun colors_haveExpectedArgbValues() {
        // Light variants
        assertEquals(Color(0xFFD0BCFF), Purple80)
        assertEquals(Color(0xFFCCC2DC), PurpleGrey80)
        assertEquals(Color(0xFFEFB8C8), Pink80)

        // Darker variants
        assertEquals(Color(0xFF6650A4), Purple40)
        assertEquals(Color(0xFF625B71), PurpleGrey40)
        assertEquals(Color(0xFF7D5260), Pink40)
    }

    // -----------------------------
    // AppTheme enum (Theme.kt)
    // -----------------------------

    @Test
    fun appTheme_containsExpectedValuesInOrder() {
        val values = enumValues<AppTheme>().toList()

        assertEquals(3, values.size)
        assertEquals(AppTheme.SYSTEM, values[0])
        assertEquals(AppTheme.LIGHT, values[1])
        assertEquals(AppTheme.DARK, values[2])

        val names = values.map { it.name }.toSet()
        assertEquals(setOf("SYSTEM", "LIGHT", "DARK"), names)
    }

    // -----------------------------
    // Typography (Type.kt)
    // -----------------------------

    @Test
    fun typography_bodyLarge_hasExpectedDefaults() {
        // Typography is the object defined in Type.kt
        val body = Typography.bodyLarge

        // Font family & weight
        assertEquals(FontFamily.Default, body.fontFamily)
        assertEquals(FontWeight.Normal, body.fontWeight)

        // Sizes (we compare the .value floats for simplicity)
        assertEquals(16f, body.fontSize.value, 0.0f)
        assertEquals(24f, body.lineHeight.value, 0.0f)
        assertEquals(0.5f, body.letterSpacing.value, 0.0f)
    }
}
