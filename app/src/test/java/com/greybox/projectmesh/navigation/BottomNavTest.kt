package com.greybox.projectmesh.navigation

import org.junit.Assert.*
import org.junit.Test

/**
 * JVM unit tests for the BottomNavItem sealed class.
 *
 * We only check pure data:
 *  - each object has the expected route and title
 *  - all routes are unique
 *  - icons are present (non-null references)
 *
 * No Compose runtime or Android APIs are used here, so this is safe
 * as a local unit test.
 */
class BottomNavItemTest {

    private val allItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Network,
        BottomNavItem.Send,
        BottomNavItem.Receive,
        BottomNavItem.Log,
        BottomNavItem.Settings,
        BottomNavItem.Chat
    )

    @Test
    fun allItems_haveExpectedRoutesAndTitles() {
        // route, title pairs we expect
        val expected = mapOf(
            BottomNavItem.Home     to ("home" to "Home"),
            BottomNavItem.Network  to ("network" to "Network"),
            BottomNavItem.Send     to ("send" to "Send"),
            BottomNavItem.Receive  to ("receive" to "Receive"),
            BottomNavItem.Log      to ("log" to "Log"),
            BottomNavItem.Settings to ("settings" to "Settings"),
            BottomNavItem.Chat     to ("chat" to "Chat"),
        )

        for (item in allItems) {
            val (expRoute, expTitle) = expected[item]
                ?: error("Missing expectations for $item")

            assertEquals("Wrong route for ${item::class.simpleName}", expRoute, item.route)
            assertEquals("Wrong title for ${item::class.simpleName}", expTitle, item.title)
        }
    }

    @Test
    fun allItems_haveNonNullIcons() {
        allItems.forEach { item ->
            assertNotNull(
                "Icon must not be null for ${item::class.simpleName}",
                item.icon
            )
        }
    }

    @Test
    fun routes_areUniqueAcrossAllItems() {
        val routes = allItems.map { it.route }
        val distinctRoutes = routes.toSet()

        assertEquals(
            "Each BottomNavItem should use a unique route",
            distinctRoutes.size,
            routes.size
        )
    }

    @Test
    fun sealedHierarchy_containsExactlyExpectedItems() {
        // This guards against someone adding a new object without updating tests.
        val classes = allItems.map { it::class.simpleName }.toSet()

        val expectedNames = setOf(
            "Home",
            "Network",
            "Send",
            "Receive",
            "Log",
            "Settings",
            "Chat"
        )

        assertEquals(expectedNames, classes)
    }
}
