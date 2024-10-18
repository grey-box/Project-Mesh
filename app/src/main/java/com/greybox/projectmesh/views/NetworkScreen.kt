package com.greybox.projectmesh.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.model.NetworkScreenModel
import com.greybox.projectmesh.viewModel.NetworkScreenViewModel
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import org.kodein.di.compose.localDI

// This is a data class for testing in the emulator
// You can uncomment the data class to test Network Screen ui
//data class TestWifiState(
//    val pingTimeSum: Short,
//    val hopCount: Byte,
//)

@Composable
fun NetworkScreen(viewModel: NetworkScreenViewModel = viewModel(
    factory = ViewModelFactory(
        di = localDI(),
        owner = LocalSavedStateRegistryOwner.current,
        vmFactory = { NetworkScreenViewModel(it) },
        defaultArgs = null))
) {
    // declare the UI state, we can use the uiState to access the current state of the viewModel
    val uiState: NetworkScreenModel by viewModel.uiState.collectAsState(initial = NetworkScreenModel())

    // This variable is only for testing in the emulator
    // You can uncomment these three line to test Network Screen ui
//    val testWifiCollection = mutableMapOf("192.168.0.1" to TestWifiState(20, 1),
//        "192.168.0.2" to TestWifiState(30, 2),
//        "192.168.0.3" to TestWifiState(40, 3))

    // display all the connected station
    LazyColumn{
        items(
            // You can uncomment this line to test Network Screen ui
            // items = testWifiCollection.toList()
            items = uiState.allNodes.entries.toList(),
            key = {it.key}
        ){ eachItem ->
            // You can uncomment this line to test Network Screen ui
            // WifiListItem(eachItem.first, eachItem.second, onClick = {})
            if (!GlobalApp.DeviceInfoManager.deviceNameMap.containsKey(eachItem.key.addressToDotNotation())) {
                viewModel.getDeviceName(eachItem.key)
            }
            WifiListItem(eachItem.key, eachItem.value)
        }
    }
}

@Composable
// Display a single connected wifi station
fun WifiListItem(
    // You can uncomment these two line to test Network Screen ui
//    wifiAddress: String,
//    wifiEntry: TestWifiState,
    wifiAddress: Int,
    wifiEntry: VirtualNode.LastOriginatorMessage,
    onClick: (() -> Unit)? = null,
){
    val wifiAddressDotNotation = wifiAddress.addressToDotNotation()
    ListItem(
        modifier = Modifier.fillMaxWidth().let{
            if(onClick != null){
                it.clickable(onClick = onClick)
            }
            else{
                it
            }
        },
        leadingContent = {
            // The image icon on the left side
            Icon(
                // replace this image with a custom icon or image
                imageVector = Icons.Default.Image,
                contentDescription = "Profile Picture",
                modifier = Modifier.size(28.dp)
            )
        },
        headlineContent = {
            // obtain the device name according to the ip address
            val device = GlobalApp.DeviceInfoManager.getDeviceName(wifiAddressDotNotation)
            if(device != null){
                Text(text= device, fontWeight = FontWeight.Bold)
            }
            else{
                Text(text = "Loading...", fontWeight = FontWeight.Bold)
            }
        },
        supportingContent = {
            // You can uncomment this line to test Network Screen ui
            //Text(text = wifiAddress)
            Text(text = wifiAddressDotNotation)
        },
        trailingContent = {
            // The mesh status with signal bars and text
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalCellular4Bar,
                    contentDescription = "Mesh Signal Strength",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column{
                    Text(text = "Mesh status")
                    Spacer(modifier = Modifier.width(4.dp))
                    // You can uncomment these two line to test Network Screen ui
                    //Text("Ping: ${wifiEntry.pingTimeSum}ms "
                    //+ "Hops: ${wifiEntry.hopCount} ")
                    Text("Ping: ${wifiEntry.originatorMessage.pingTimeSum}ms "
                            + "Hops: ${wifiEntry.hopCount} ")
                }
            }
        }
    )

}