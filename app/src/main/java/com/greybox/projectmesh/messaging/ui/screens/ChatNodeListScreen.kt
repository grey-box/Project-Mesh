package com.greybox.projectmesh.messaging.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.extension.WifiListItem
import com.greybox.projectmesh.viewModel.NetworkScreenViewModel
import org.kodein.di.compose.localDI

@Composable
fun ChatNodeListScreen(
    onNodeSelected: (String) -> Unit,
    // Reuse NetworkScreenViewModel in the same way as NetworkScreen
    viewModel: NetworkScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle ->
                NetworkScreenViewModel(di, savedStateHandle)
            },
            defaultArgs = null,
        )
    ),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn {
        items(
            items = uiState.nodes,
            key = { it.address },
        ) { node ->
            // Tapping a WifiListItem calls 'onNodeSelected' (no user-info exchange here,
            // same as before the migration)
            WifiListItem(
                wifiAddress = node.address,
                wifiEntry = node.entry,
                onClick = { selectedIp -> onNodeSelected(selectedIp) },
            )
        }
    }
}
