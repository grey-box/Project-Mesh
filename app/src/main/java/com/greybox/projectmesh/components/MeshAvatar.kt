package com.greybox.projectmesh.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme

enum class MeshAvatarSize { Sm, Md }



@Composable
fun MeshAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: MeshAvatarSize = MeshAvatarSize.Md,
    isOnline: Boolean = false,
    showStatusDot: Boolean = false,
){
    val avatarSize = when (size) {
        MeshAvatarSize.Sm -> MeshTheme.size.avatarSm
        MeshAvatarSize.Md -> MeshTheme.size.avatarMd
    }
    val initialStyle = when (size) {
        MeshAvatarSize.Sm -> MeshTheme.typography.titleMedium
        MeshAvatarSize.Md -> MeshTheme.typography.titleLarge
    }
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer

    val initial = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) "?" else String(Character.toChars(trimmed.codePointAt(0)))
    }

    Box(modifier = modifier.size(avatarSize)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(container),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = initialStyle,
                color = content,
                // The initial is a visual stand-in for the name, and the name is always
                // rendered next to this avatar at both call sites. Left in the semantics
                // tree it makes TalkBack announce every row as "A, Ana, Online" — one
                // stray letter in front of the thing it abbreviates. Cleared, not
                // described, because there is nothing here a screen reader needs.
                modifier = Modifier.clearAndSetSemantics { }
            )
        }

        if (showStatusDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(MeshTheme.size.statusDot)
                    .clip(CircleShape)
                    .background(MeshTheme.colors.surface),
                contentAlignment = Alignment.Center
            ) {
                // Filled when online, a hollow ring when offline. The two status colours
                // measure 1.04:1 against each other in light, so colour alone conveyed
                // nothing in greyscale or sunlight; MeshStatusDot adds the shape channel
                // that SC 1.4.1 requires.
                MeshStatusDot(
                    isOnline = isOnline,
                    size = MeshTheme.size.statusDot - MeshTheme.spacing.xs
                )
            }
        }
    }
}

@Composable
private fun MeshAvatarSamples() {
    Surface {
        Row(
            modifier = Modifier.padding(MeshTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MeshTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeshAvatar(name = "Alex", size = MeshAvatarSize.Sm)
            MeshAvatar(name = "Alex", size = MeshAvatarSize.Sm, isOnline = true)
            MeshAvatar(name = "Blair", size = MeshAvatarSize.Md)
            MeshAvatar(name = "Blair", size = MeshAvatarSize.Md, isOnline = true, showStatusDot = true)
            MeshAvatar(name = "", size = MeshAvatarSize.Md, showStatusDot = true)
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun MeshAvatarLightPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) { MeshAvatarSamples() }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun MeshAvatarDarkPreview() {
    ProjectMeshTheme(appTheme = AppTheme.DARK) { MeshAvatarSamples() }
}

// Android allows a font scale up to 200%, and not one preview in this project exercised
// it, so text that clipped or overflowed at large sizes was invisible during development
// and only reachable by changing a system setting on a device. Anything with a fixed
// height or a single-line label needs this preview beside the light and dark ones.
@Preview(showBackground = true, name = "Large text (200%)", fontScale = 2f)
@Composable
private fun MeshAvatarLargeTextPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) { MeshAvatarSamples() }
}