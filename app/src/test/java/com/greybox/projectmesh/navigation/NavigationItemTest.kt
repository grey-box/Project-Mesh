package com.greybox.projectmesh.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests for the NavigationItem data class.
 *
 * NOTE:
 *  - Composables & NavController CANNOT be JVM tested.
 *  - We only verify that NavigationItem behaves as a proper
 *    Kotlin data class (copy, equals, hashCode).
 */
class NavigationItemTest {

    // Just need some non-null ImageVector instance
    private val dummyIcon: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,      // <-- Dp, not Float
        defaultHeight = 24.dp,     // <-- Dp, not Float
        viewportWidth = 24f,
        viewportHeight = 24f
    ).build()

    @Test
    fun navigationItem_copyEqualsHashCodeCorrect() {
        val original = NavigationItem(
            route = "home",
            label = "Home",
            icon = dummyIcon
        )

        val copy = original.copy()
        val modified = original.copy(route = "different")

        // Same data → equal
        assertEquals(original, copy)
        assertEquals(original.hashCode(), copy.hashCode())

        // Different route → not equal
        assertNotEquals(original, modified)
        assertEquals("different", modified.route)
    }
}
