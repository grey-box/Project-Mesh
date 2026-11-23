package com.greybox.projectmesh.views

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.PingScreenViewModel
import org.kodein.di.compose.localDI
import java.net.InetAddress
import com.greybox.projectmesh.ui.theme.MeshTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    // Removes the wrong IP address on startup
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // display all ping information
    LazyColumn{
        item{
            Row(modifier = Modifier.fillMaxWidth()){
                Text(text = "Device name: ${uiState.deviceName}, IP address: ${virtualAddress.hostAddress}")
            }
        }
        // Rows only show PingResult values
        items(items = uiState.pingResults) { ping ->
            Spacer(modifier = Modifier.width(MeshTheme.spacing.xs))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Ping: ${ping.pingTimeMs}ms, hops: ${ping.hopCount}, last hop: ${ping.lastHopAddress}, id: ${ping.messageId}")
            }
        }
    }
}