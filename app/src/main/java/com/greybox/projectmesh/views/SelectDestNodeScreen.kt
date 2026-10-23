package com.greybox.projectmesh.views

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.PingScreenViewModel
import com.greybox.projectmesh.viewModel.SelectDestNodeScreenModel
import com.greybox.projectmesh.viewModel.SelectDestNodeScreenViewModel
import org.kodein.di.compose.localDI
import com.greybox.projectmesh.extension.WifiListItem
import com.greybox.projectmesh.ui.theme.MeshTheme
@Composable
fun SelectDestNodeScreen(
    uris: List<Uri>,
    popBackWhenDone: () -> Unit,
    viewModel: SelectDestNodeScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> SelectDestNodeScreenViewModel(di, savedStateHandle, uris, popBackWhenDone) },
            defaultArgs = null,
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState(SelectDestNodeScreenModel())
    DisplayAllNodesToSelect(
        uiState = uiState,
        onClickReceiver = viewModel::onClickReceiver,
    )
}

@Composable
fun DisplayAllNodesToSelect(
    uiState: SelectDestNodeScreenModel,
    onClickReceiver: (Int) -> Unit,
) {
    val inProgressDevice = uiState.contactingInProgressDevice
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ){
        if(inProgressDevice != null) {
            item("inprogress") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(MeshTheme.spacing.sm),
                    )
                    Text(
                        modifier = Modifier.padding(MeshTheme.spacing.sm),
                        text = "Contacting $inProgressDevice\nThis might take a few seconds.",
                    )
                }
            }
        }
        else{
            items(
                items = uiState.allNodes.entries.toList(),
                key = {it.key}
            ){ entry ->
                WifiListItem(
                    wifiAddress = entry.key,
                    wifiEntry = entry.value,
                    onClick = {
                        onClickReceiver(entry.key)
                    }
                )
            }
        }
    }
}