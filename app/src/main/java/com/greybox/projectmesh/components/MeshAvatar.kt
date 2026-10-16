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
import androidx.compose.ui.tooling.preview.Preview
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import androidx.compose.runtime.remember

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
    val containerColor = if (isOnline) MeshTheme.colors.primary else MeshTheme.colors.secondaryContainer
    val contentColor = if (isOnline) MeshTheme.colors.onPrimary else MeshTheme.colors.onSecondaryContainer

    val initial = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) "?" else String(Character.toChars(trimmed.codePointAt(0)))
    }

    Box(modifier = modifier.size(avatarSize)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = initialStyle,
                color = contentColor
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
                Box(
                    modifier = Modifier
                        .size(MeshTheme.size.statusDot - MeshTheme.spacing.xs)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) MeshTheme.mesh.statusOnline
                            else MeshTheme.mesh.statusOffline
                        )
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