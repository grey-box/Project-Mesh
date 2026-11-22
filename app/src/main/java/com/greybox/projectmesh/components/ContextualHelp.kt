package com.greybox.projectmesh.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ui.theme.MeshTheme

/** Link-style text: primary + underlined, switches to tertiary once tapped. */
@Composable
fun HelpLink(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MeshTheme.typography.bodyMedium,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    var visited by rememberSaveable { mutableStateOf(false) }
    Text(
        text = text,
        style = style.copy(textDecoration = TextDecoration.Underline),
        color = if (visited) MeshTheme.colors.tertiary else MeshTheme.colors.primary,
        modifier = modifier
            .clickable(role = Role.Button) {
                visited = true
                onClick()
            }
            .then(
                if (compact) Modifier
                else Modifier.heightIn(min = 48.dp).padding(vertical = MeshTheme.spacing.sm)
            ),
    )
}

/** Centered popup. Closes via the close button or by tapping outside. */
@Composable
fun HelpDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MeshTheme.shapes.large, color = MeshTheme.colors.surface) {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(MeshTheme.spacing.md)
                        .padding(end = MeshTheme.spacing.lg),
                ) {
                    Text(
                        text = title,
                        style = MeshTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = MeshTheme.spacing.sm),
                    )
                    content()
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = stringResource(R.string.help_close),
                    )
                }
            }
        }
    }
}

@Composable
fun HelpParagraph(text: String) {
    Text(
        text = text,
        style = MeshTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = MeshTheme.spacing.sm),
    )
}