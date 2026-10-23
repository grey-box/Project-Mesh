package com.greybox.projectmesh.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.components.MeshScreenScaffold
import com.greybox.projectmesh.ui.theme.TransparentButton
import com.greybox.projectmesh.viewModel.SendScreenModel
import com.greybox.projectmesh.viewModel.SendScreenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kodein.di.compose.localDI
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme

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
//    Box(modifier = Modifier.fillMaxSize()){
//        Column(modifier = Modifier
//            .fillMaxSize()
//            .padding(bottom = 72.dp)) {
//            DisplayAllPendingTransfers(viewModel, uiState)
//        }
//        TransparentButton(onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(16.dp),
//            text = stringResource(id = R.string.send_file),
//            enabled = true
//        )
//    }

    MeshScreenScaffold(
        bottomBar = {
            TransparentButton(onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(MeshTheme.spacing.md),
                text = stringResource(id = R.string.send_file),
                enabled = true
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            DisplayAllPendingTransfers(viewModel, uiState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
// Display all the pending transfers
fun DisplayAllPendingTransfers(
    viewModel: SendScreenViewModel,
    uiState: SendScreenModel,
){
    val coroutineScope = rememberCoroutineScope()
    LazyColumn {
        items(
            items = uiState.outgoingTransfers,
            key = { it.id }
        ) {transfer ->
            var isVisible by remember { mutableStateOf(true) }
            val swipeState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        coroutineScope.launch {
                            isVisible = false // Start fade-out animation
                            delay(300)
                            viewModel.onDelete(transfer) // Remove item on swipe
                        }
                        true
                    } else {
                        false
                    }
                }
            )
            AnimatedVisibility(
                visible = isVisible,
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.animateItemPlacement()
            ){
                SwipeToDismissBox(
                    state = swipeState,
                    enableDismissFromStartToEnd = false, // Allow swipe only from right to left
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize() // Matches the height of the item being swiped
                                .padding(vertical = MeshTheme.spacing.sm) // Prevents the background from touching top & bottom
                                .background(MeshTheme.colors.error, shape = MeshTheme.shapes.medium),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MeshTheme.colors.onError,
                                modifier = Modifier.size(MeshTheme.size.iconLg).padding(end = MeshTheme.spacing.sm)
                            )
                        }
                    },
                    content = {
                        ListItem(
                            headlineContent = { Text(transfer.name) },
                            supportingContent = {
                                Column {
                                    val byteTransferred: Int = transfer.transferred
                                    val byteSize: Int = transfer.size
                                    val toHostAddress = transfer.toHost.hostAddress
                                    val deviceName = toHostAddress?.let { ipStr ->
                                        runBlocking {
                                            GlobalApp.GlobalUserRepo.userRepository.getUserByIp(ipStr)?.name
                                        }
                                    }
//                                    val deviceName = toHostAddress?.let {
//                                        GlobalApp.DeviceInfoManager.getDeviceName(it)
//                                    }
                                    if (deviceName != null) {
                                        Text("To: ${deviceName} (${toHostAddress})")
                                    } else {
                                        Text("To: Loading... (${toHostAddress})")
                                    }
                                    Text(stringResource(id = R.string.status) + ": ${transfer.status}")
                                    Text(stringResource(id = R.string.send) + ": ${autoConvertByte(byteTransferred)} / ${autoConvertByte(byteSize)}")
                                }
                            },
                            modifier = Modifier.padding(vertical = MeshTheme.spacing.xs)
                        )
                    },
                )
                HorizontalDivider()
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