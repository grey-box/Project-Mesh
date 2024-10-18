package com.greybox.projectmesh.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.buttonStyle.WhiteButton
import com.greybox.projectmesh.model.SendScreenModel
import com.greybox.projectmesh.viewModel.SendScreenViewModel
import org.kodein.di.compose.localDI

@Composable
fun SendScreen(
    onSwitchToSelectDestNode: (Uri) -> Unit,
    viewModel: SendScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { SendScreenViewModel(it, onSwitchToSelectDestNode) },
            defaultArgs = null,
        )
    ),
) {
    // declare the UI state
    val uiState: SendScreenModel by viewModel.uiState.collectAsState(SendScreenModel())
    // indicate a file has been chosen
    // var isFileChosen = uiState.fileUri != null
    // File picker launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ){ uri ->
        if (uri != null){
            viewModel.onFileChosen(uri)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        WhiteButton(onClick = {
            openDocumentLauncher.launch(arrayOf("*/*"))
            //isFileChosen = true
        },
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(16.dp),
            text = "Send File",
            enabled = true)
    }
    DisplayAllPendingTransfers(uiState)
}

@Composable
// Display all the pending transfers
fun DisplayAllPendingTransfers(
    uiState: SendScreenModel,
){
    LazyColumn {
        items(
            items = uiState.outgoingTransfers,
            key = { it.id }
        ) {transfer ->
            ListItem(
                headlineContent = {
                    Text(transfer.name)
                },
                supportingContent = {
                    Column {
                        val byteTransferred: Int = transfer.transferred
                        val byteSize: Int = transfer.size
                        val toHostAddress = transfer.toHost.hostAddress
                        val deviceName = toHostAddress?.let {
                            GlobalApp.DeviceInfoManager.getDeviceName(it)
                        }
                        if (deviceName != null){
                            Text("To ${deviceName}(${toHostAddress})")
                        }
                        else{
                            Text("To Loading...(${toHostAddress})")
                        }
                        Text("Status: ${transfer.status}")
                        Text("Sent ${autoConvertByte(byteTransferred)} / ${autoConvertByte(byteSize)}")
                    }
                }
            )
        }
    }
}

fun autoConvertByte(byteSize: Int): String{
    val kb = Math.round(byteSize / 1024.0 * 100) / 100.0
    val mb = Math.round((byteSize / (1024.0 * 1024.0) * 100) / 100.0)
    if (byteSize == 0){
        return "0B"
    }
    else if (mb < 1){
        return "${kb}KB"
    }
    return "${mb}MB"
}