package com.greybox.projectmesh.views

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
fun NetworkScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NetworkScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> NetworkScreenViewModel(di, savedStateHandle) },
            defaultArgs = null,
        )
    ),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // display all the connected stations
    LazyColumn {
        items(
            items = uiState.nodes,
            key = { it.address },
        ) { node ->
            WifiListItem(
                wifiAddress = node.address,
                wifiEntry = node.entry,
                onClick = { ipAddress ->
                    viewModel.onNodeClicked(ipAddress)
                    // Navigation stays in the UI layer
                    onNodeClick(ipAddress)
                },
            )
        }
    }
}
