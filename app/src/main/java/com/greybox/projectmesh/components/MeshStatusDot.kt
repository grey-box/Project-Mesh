package com.greybox.projectmesh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.greybox.projectmesh.ui.theme.MeshTheme

/**
 * The online/offline indicator dot. One implementation, previously open-coded at four
 * separate call sites, each of which got the accessibility of it wrong the same way.
 *
 * **Shape carries the state, not only colour.** Online is a filled dot; offline is a
 * hollow ring. That is the point of this component. The two status colours were measured
 * against each other at **1.04:1 in light and 1.58:1 in dark** — near-identical
 * luminance — so once hue is removed the old green and grey dots were literally the same
 * dot. Anyone with monochromacy, anyone reading the screen in direct sunlight, and
 * anyone using a greyscale display got no information at all. WCAG 2.1 SC 1.4.1 (Use of
 * Color) requires a second channel, and filled-vs-hollow is that channel.
 *
 * Both renderings clear SC 1.4.11 (Non-text Contrast, 3:1) against `surface`:
 * the filled dot at 3.36:1 light / 5.58:1 dark, the ring's stroke at 3.23:1 / 3.54:1.
 *
 * The dot is intentionally invisible to assistive technology — no `contentDescription`.
 * A screen reader should not stop on a bare circle. The state belongs to the row that
 * owns the dot, as a `stateDescription`, so TalkBack announces "Ana, Online" as one
 * focus stop. Callers are responsible for that; see [MeshAvatar] and `ConversationItem`.
 */
@Composable
fun MeshStatusDot(
    isOnline: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (isOnline) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MeshTheme.mesh.statusOnline)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MeshTheme.colors.surface)
                .border(
                    // Twice the M3 hairline. A single 1dp stroke on an 8dp dot reads as a
                    // smudge rather than a ring, which would lose the shape cue this
                    // component exists to provide. Still derived from the library
                    // constant rather than a literal, since 05 defines no border family.
                    width = DividerDefaults.Thickness * 2,
                    color = MeshTheme.mesh.statusOffline,
                    shape = CircleShape
                )
        )
    }
}
