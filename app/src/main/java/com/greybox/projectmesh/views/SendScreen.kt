package com.greybox.projectmesh.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.SendScreenModel
import com.greybox.projectmesh.viewModel.SendScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kodein.di.compose.localDI

@Composable
fun SendScreen(
    onSwitchToSelectDestNode: (List<Uri>) -> Unit,
    viewModel: SendScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> SendScreenViewModel(di, savedStateHandle, onSwitchToSelectDestNode)},
            defaultArgs = null,
        )
    ),
) {
    // declare the UI state
    val uiState: SendScreenModel by viewModel.uiState.collectAsState(SendScreenModel())
    // File picker launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ){ uris ->
        if (uris.isNotEmpty()){
            viewModel.onFileChosen(uris)
        }
    }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Pick Files")
            }
        }
    ) { innerPadding ->
        DisplayAllPendingTransfers(
            viewModel = viewModel,
            uiState = uiState,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DisplayAllPendingTransfers(
    viewModel: SendScreenViewModel,
    uiState: SendScreenModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    LazyColumn(modifier = modifier.padding(16.dp)) {
        items(uiState.outgoingTransfers, key = { it.id }) { transfer ->
            var isVisible by remember { mutableStateOf(true) }
            val swipeState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        coroutineScope.launch {
                            isVisible = false
                            delay(300)
                            viewModel.onDelete(transfer)
                        }
                        true
                    } else false
                }
            )
            AnimatedVisibility(
                visible = isVisible,
                exit = fadeOut(tween(300)),
                modifier = Modifier.animateItemPlacement()
            ) {
                SwipeToDismissBox(
                    state = swipeState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .height(96.dp)
                                .padding(vertical = 8.dp)
                                .background(Color.Red, shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(end = 20.dp)
                                    .size(32.dp)
                            )
                        }
                    },
                    content = {
                        val byteTransferred = transfer.transferred
                        val byteSize = transfer.size
                        val progress = if (byteSize == 0) 0f else byteTransferred / byteSize.toFloat()
                        val deviceName = GlobalApp.DeviceInfoManager.getDeviceName(transfer.toHost.hostAddress ?: "")

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = transfer.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "To: ${deviceName ?: "Loading..."} (${transfer.toHost.hostAddress})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Status: ${transfer.status}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    when (transfer.status.toString()) {
                                        "COMPLETED" -> Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "IN_PROGRESS" -> Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "In Progress",
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        "FAILED", "DECLINED" -> Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = "Failed",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (transfer.status.toString() == "COMPLETED") Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${autoConvertByte(byteTransferred)} / ${autoConvertByte(byteSize)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                )
            }
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

fun autoConvertMS(ms: Int): String {
    val second = Math.round(ms / 1000.0 * 100) / 100.0
    val minute = Math.round((second / 60.0) * 100) / 100.0
    return if (second >= 1 && minute < 1) {
        "${second}s"
    } else if (minute >= 1) {
        "${minute}m"
    } else {
        "${ms}ms"
    }
}