package com.greybox.projectmesh.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.PingScreenModel
import com.greybox.projectmesh.viewModel.PingScreenViewModel
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import org.kodein.di.compose.localDI
import java.net.InetAddress
import com.greybox.projectmesh.ui.theme.MeshTheme

@Composable
fun PingScreen(
    virtualAddress: InetAddress,
    viewModel: PingScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> PingScreenViewModel(di, savedStateHandle, virtualAddress) },
            defaultArgs = null
        )
    )
) {
    // declare the UI state, we can use the uiState to access the current state of the viewModel
    val uiState: PingScreenModel by viewModel.uiState.collectAsState(initial = PingScreenModel())

    // display all ping information
    LazyColumn{
        item{
            Row(modifier = Modifier.fillMaxWidth()){
                Text(text = "Device name: ${uiState.deviceName}, IP address: ${uiState.virtualAddress.hostAddress}")
            }
        }
        items(
            items = uiState.allOriginatorMessages
        ){ originatorMessage ->
            val mmcpMessage: MmcpOriginatorMessage = originatorMessage.originatorMessage
            Spacer(modifier = Modifier.width(MeshTheme.spacing.xs))
            Row(modifier = Modifier.fillMaxWidth()){
                Text(text = "Ping: ${mmcpMessage.pingTimeSum}ms, hops: ${originatorMessage.hopCount}, last hop: ${originatorMessage.lastHopAddr.addressToDotNotation()}, id: ${mmcpMessage.messageId}")
            }
        }
    }
}