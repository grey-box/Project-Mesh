package com.greybox.projectmesh.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greybox.projectmesh.ui.theme.MeshTheme


@Composable
fun MeshScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
    ) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = MeshTheme.colors.background,
        contentColor = MeshTheme.colors.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content
    )
}

