package com.greybox.projectmesh.views

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.greybox.projectmesh.R
import com.greybox.projectmesh.components.MeshScreenScaffold
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme

/**
 * Offline "How Mesh Works" user-education screen.
 *
 * Stateless: the caller decides how to open and close it.
 *  - From Onboarding: toggled by local state in [OnboardingScreen] (no NavHost exists yet).
 *  - From Settings: opened via the "how_mesh_works" route in AppNavHost.
 */
private data class HelpSection(
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val helpSections = listOf(
    HelpSection(R.string.how_mesh_works_q_what, R.string.how_mesh_works_a_what),
    HelpSection(R.string.how_mesh_works_q_phones, R.string.how_mesh_works_a_phones),
    HelpSection(R.string.how_mesh_works_q_vs_wifi, R.string.how_mesh_works_a_vs_wifi),
    HelpSection(R.string.how_mesh_works_q_connect, R.string.how_mesh_works_a_connect),
    HelpSection(R.string.how_mesh_works_q_disconnect, R.string.how_mesh_works_a_disconnect),
    HelpSection(R.string.how_mesh_works_q_privacy, R.string.how_mesh_works_a_privacy),
    HelpSection(R.string.how_mesh_works_q_range, R.string.how_mesh_works_a_range),
    HelpSection(R.string.how_mesh_works_q_battery, R.string.how_mesh_works_a_battery),
    HelpSection(R.string.how_mesh_works_q_when, R.string.how_mesh_works_a_when),
    HelpSection(R.string.how_mesh_works_q_pages, R.string.how_mesh_works_a_pages),
    HelpSection(R.string.how_mesh_works_q_bluetooth, R.string.how_mesh_works_a_bluetooth),
    HelpSection(R.string.how_mesh_works_q_other_phone, R.string.how_mesh_works_a_other_phone),
    HelpSection(R.string.how_mesh_works_q_which_phone, R.string.how_mesh_works_a_which_phone),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowMeshWorksScreen(onBackClick: () -> Unit) {
    BackHandler(onBack = onBackClick)

    MeshScreenScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.how_mesh_works_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MeshTheme.colors.background,
                    titleContentColor = MeshTheme.colors.onBackground,
                    navigationIconContentColor = MeshTheme.colors.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(MeshTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.lg)
        ) {
            items(helpSections) { section ->
                HelpSectionItem(section)
            }
        }
    }
}

@Composable
private fun HelpSectionItem(section: HelpSection) {
    Column {
        Text(
            text = stringResource(section.title),
            style = MeshTheme.typography.titleMedium,
            color = MeshTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.height(MeshTheme.spacing.sm))
        Text(
            text = stringResource(section.body),
            style = MeshTheme.typography.bodyMedium,
            color = MeshTheme.colors.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun HowMeshWorksScreenLightPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) {
        HowMeshWorksScreen(onBackClick = {})
    }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun HowMeshWorksScreenDarkPreview() {
    ProjectMeshTheme(appTheme = AppTheme.DARK) {
        HowMeshWorksScreen(onBackClick = {})
    }
}
