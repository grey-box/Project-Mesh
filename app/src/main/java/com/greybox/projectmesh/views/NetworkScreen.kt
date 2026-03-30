package com.greybox.projectmesh.views


import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.NetworkScreenModel
import com.greybox.projectmesh.viewModel.NetworkScreenViewModel
import org.kodein.di.compose.localDI
import com.greybox.projectmesh.extension.WifiListItem


@Composable
fun NetworkScreen(
    onNodeClick: (String) -> Unit,
    viewModel: NetworkScreenViewModel = viewModel(
    factory = ViewModelFactory(
        di = localDI(),
        owner = LocalSavedStateRegistryOwner.current,
        vmFactory = { di, savedStateHandle -> NetworkScreenViewModel(di, savedStateHandle)},
        defaultArgs = null))
) {
    // declare the UI state, we can use the uiState to access the current state of the viewModel
    val uiState: NetworkScreenModel by viewModel.uiState.collectAsState(initial = NetworkScreenModel())


    // display all the connected station
    LazyColumn{
        items(
            items = uiState.allNodes.entries.toList(),
            key = {it.key}
        ){ eachItem ->
            WifiListItem(
                wifiAddress = eachItem.key,
                wifiEntry = eachItem.value,
                onClick = { ipAddress ->
                    viewModel.onNodeSelected(ipAddress)
                    onNodeClick(ipAddress)
                }
            )
        }
    }
}

