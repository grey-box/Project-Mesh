package com.greybox.projectmesh

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertFalse
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.ui.screens.ConversationItem
import com.greybox.projectmesh.messaging.ui.screens.MessageBubble
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.GradientButton
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import com.greybox.projectmesh.views.SectionHeader
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Accessibility semantics that cannot be checked any other way.
 *
 * `uiautomator dump` projects Compose's UNMERGED layout tree and omits `stateDescription`,
 * `heading` and `liveRegion` entirely, so a manual dump can confirm touch target sizes and
 * roles but is blind to exactly the properties below. These are the ones that need a
 * semantics-level assertion — and having them as tests means a later refactor that drops
 * a `stateDescription` fails the build instead of silently un-fixing a screen reader.
 */
class AccessibilitySemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun conversation(online: Boolean) = Conversation(
        id = "a:b",
        userUuid = "b",
        userName = "Ana",
        userAddress = "192.168.0.9",
        lastMessage = "See you at six",
        lastMessageTime = 1_700_000_000_000L,
        unreadCount = 0,
        isOnline = online
    )

    /**
     * The conversation list conveyed reachability only through the colour of an 8dp dot,
     * and the two status colours measure 1.04:1 against each other. A screen reader user
     * had no way at all to tell an offline contact from an online one.
     */
    @Test
    fun conversationRow_announcesOnlineState() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                ConversationItem(conversation = conversation(online = true), onClick = {})
            }
        }
        rule.onNode(hasText("Ana", substring = true), useUnmergedTree = false)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription, "Online"
                )
            )
    }

    @Test
    fun conversationRow_announcesOfflineState() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                ConversationItem(conversation = conversation(online = false), onClick = {})
            }
        }
        rule.onNode(hasText("Ana", substring = true), useUnmergedTree = false)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription, "Offline"
                )
            )
    }

    /**
     * A message bubble is four Texts (sender, body, optional attachment, timestamp). Left
     * unmerged that is four separate TalkBack stops per message, with the timestamp
     * arriving detached from the message it belongs to.
     */
    @Test
    fun messageBubble_isASingleFocusStop() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                MessageBubble(
                    chatMessage = Message(
                        id = 1,
                        dateReceived = 1_700_000_000_000L,
                        content = "See you at six",
                        sender = "Ana",
                        chat = "a:b"
                    ),
                    sentBySelf = false,
                    messageContent = {},
                    sender = "Ana",
                    modifier = androidx.compose.ui.Modifier
                )
            }
        }
        // The merged tree should expose the sender and the timestamp on ONE node.
        val merged = rule.onRoot(useUnmergedTree = false).printToString(maxDepth = 10)
        val stops = Regex("Text = '").findAll(merged).count()
        assertTrue(
            "Expected the bubble to merge into a single focus stop, tree was:\n$merged",
            stops <= 1
        )
    }

    /**
     * GradientButton is a Box with a clickable, not a Material Button. Without an explicit
     * Role, TalkBack read its label as static text with no "button" and no hint that it
     * could be activated. Six of these render in SettingsScreen.
     */
    @Test
    fun gradientButton_hasButtonRole() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                Column { GradientButton(text = "Restart", onClick = {}) }
            }
        }
        rule.onNodeWithText("Restart")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button
                )
            )
    }

    /**
     * Headings were visual only — bold and larger, but structurally plain text. With no
     * heading anywhere in the app, TalkBack's navigate-by-heading gesture had nothing to
     * land on, so the only way through a long settings list was to swipe past every
     * single control in order.
     */
    @Test
    fun settingsSectionHeader_isAHeading() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                Column { SectionHeader(title = R.string.network) }
            }
        }
        rule.onNode(hasText("Network", substring = true))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /**
     * A long label must not be truncated at a large font scale. maxLines = 1 with an
     * ellipsis was losing content outright at the 200% scale Android permits.
     *
     * The old version of this test only checked that the text existed, which passes even
     * when the label is cut off on screen: semantics always carry the full string. This
     * renders at 200% and asks the text layout itself whether anything overflowed.
     */
    @Test
    fun gradientButton_doesNotTruncateLongLabelsAt200Percent() {
        val long = "Reset STA/AP Concurrency Status"
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f)
            ) {
                ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                    Column { GradientButton(text = long, onClick = {}) }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(long), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(
            "Label overflowed its bounds at 200% font scale",
            layouts.single().hasVisualOverflow
        )
    }

    /** The chat bubble's tap-for-menu must be published, not just wired to a gesture. */
    @Test
    fun messageBubble_withCopy_isClickableAndOffersCopyAction() {
        rule.setContent {
            ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
                MessageBubble(
                    chatMessage = Message(
                        id = 1,
                        dateReceived = 1_700_000_000_000L,
                        content = "See you at six",
                        sender = "Ana",
                        chat = "a:b"
                    ),
                    sentBySelf = false,
                    messageContent = { androidx.compose.material3.Text("See you at six") },
                    sender = "Ana",
                    modifier = androidx.compose.ui.Modifier,
                    onCopy = {},
                    copyLabel = "Copy message"
                )
            }
        }
        rule.onNode(hasText("See you at six", substring = true))
            .assertHasClickAction()
            .assert(
                SemanticsMatcher("has a Copy message custom action") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)
                        ?.any { it.label == "Copy message" } == true
                }
            )
    }
}
