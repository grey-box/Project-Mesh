package com.greybox.projectmesh.views

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.greybox.projectmesh.NEARBY_WIFI_PERMISSION_NAME
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.buttonStyle.WhiteButton
//import com.greybox.projectmesh.components.ConnectWifiLauncherResult
//import com.greybox.projectmesh.components.ConnectWifiLauncherStatus
//import com.greybox.projectmesh.components.meshrabiyaConnectLauncher
import com.greybox.projectmesh.hasNearbyWifiDevicesOrLocationPermission
import com.greybox.projectmesh.hasStaApConcurrency
import com.greybox.projectmesh.model.HomeScreenModel
import com.greybox.projectmesh.viewModel.HomeScreenViewModel
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.MeshrabiyaConnectLink
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import com.yveskalume.compose.qrpainter.rememberQrBitmapPainter
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance

@Composable
// We customize the viewModel since we need to inject dependencies
fun HomeScreen(viewModel: HomeScreenViewModel = viewModel(
    factory = ViewModelFactory(
        di = localDI(),
        owner = LocalSavedStateRegistryOwner.current,
        vmFactory = { HomeScreenViewModel(it) },
        defaultArgs = null)))
{
    val di = localDI()
    val uiState: HomeScreenModel by viewModel.uiState.collectAsState(initial = HomeScreenModel())
    val node: VirtualNode by di.instance()
    val context = LocalContext.current

    // Request nearby wifi permission
    val requestNearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){ granted -> if (granted){
        if(context.hasNearbyWifiDevicesOrLocationPermission()){
            viewModel.onSetIncomingConnectionsEnabled(true)
        }
    } }

    // Launch the home screen
    StartHomeScreen(
        uiState = uiState,
        node = node as AndroidVirtualNode,
        onSetIncomingConnectionsEnabled = {enabled ->
            if(enabled && !context.hasNearbyWifiDevicesOrLocationPermission()){
                requestNearbyWifiPermissionLauncher.launch(NEARBY_WIFI_PERMISSION_NAME)
            }
            else{
                viewModel.onSetIncomingConnectionsEnabled(enabled)
            }
        },
        onClickDisconnectWifiStation = viewModel::onClickDisconnectStation,
    )
}

// Display the home screen
@Composable
fun StartHomeScreen(
    uiState: HomeScreenModel,
    node: AndroidVirtualNode,
    onSetIncomingConnectionsEnabled: (Boolean) -> Unit = { },
    onClickDisconnectWifiStation: () -> Unit = { },
    viewModel: HomeScreenViewModel = viewModel(),
){
    val di = localDI()
    val barcodeEncoder = remember { BarcodeEncoder() }
    val context = LocalContext.current
    var userEnteredConnectUri by rememberSaveable { mutableStateOf("") }
    // initialize the QR code scanner
    val qrScannerLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        // Get the contents of the QR code
        val link = result.contents
        if (link != null) {
            try {
                // Parse the link, get the wifi connect configuration.
                val hotSpot = MeshrabiyaConnectLink.parseUri(
                    uri = link,
                    json = di.direct.instance()
                ).hotspotConfig
                // if the configuration is valid, connect to the device.
                if (hotSpot != null) {
                    // Connect device thru wifi connection
                    viewModel.onConnectWifi(hotSpot)
                } else {
                    // Link doesn't have a connect config
                }
            } catch (e: Exception) {
                // Invalid Link
            }
        }
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            // Display the device IP
            LongPressCopyableText(
                context = context,
                text = "",
                textCopyable = Build.BRAND.substring(0, 1).uppercase() +
                        Build.BRAND.substring(1).lowercase() + " " + Build.MODEL,
                textSize = 15
            )
            Spacer(modifier = Modifier.height(6.dp))
            LongPressCopyableText(
                context = context,
                text = "IP Address: ",
                textCopyable = uiState.localAddress.addressToDotNotation(),
                textSize = 15
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Display the "Start Hotspot" button
            val stationState = uiState.wifiState?.wifiStationState
            if (!uiState.wifiConnectionsEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WhiteButton(
                        onClick = { onSetIncomingConnectionsEnabled(true) },
                        modifier = Modifier.padding(4.dp),
                        text = "Start Hotspot",
                        // If not connected to a WiFi, enable the button
                        // Else, check if the device supports WiFi STA/AP Concurrency
                        // If it does, enable the button. Otherwise, disable it
                        enabled = if(stationState == null || stationState.status == WifiStationState.Status.INACTIVE) true else context.hasStaApConcurrency()
                    )
                }
            }
            // Display the "Stop Hotspot" button
            else{
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    WhiteButton(
                        onClick = { onSetIncomingConnectionsEnabled(false) },
                        modifier = Modifier.padding(4.dp),
                        text = "Stop Hotspot",
                        enabled = true
                    )
                }
            }

            // Generating QR CODE
            val connectUri = uiState.connectUri
            if (connectUri != null && uiState.wifiConnectionsEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                QRCodeView(
                    connectUri,
                    barcodeEncoder,
                    uiState.wifiState?.connectConfig?.ssid,
                    uiState.wifiState?.connectConfig?.passphrase,
                    uiState.wifiState?.connectConfig?.bssid,
                    uiState.wifiState?.connectConfig?.port.toString())
                // Display connectUri
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "To share the connect URI:\n" +
                        "1. Make sure Bluetooth is enabled on this device.\n" +
                        "2. Click \"Share connect URI\"\n" +
                        "3. Select Quick Share\n" +
                        "4. Choose a nearby device you want to share the URI with")
                Button(onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, connectUri)
                        type = "text/plain"
                    }

                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }, modifier = Modifier.padding(4.dp), enabled = true) {
                    Text("Share connect URI")
                }
            }
            // Scan the QR CODE
            // If the stationState is not null and its status is INACTIVE,
            // It will display the option to connect via a QR code scan.
            if (stationState != null){
                if (stationState.status == WifiStationState.Status.INACTIVE){
                    Column (modifier = Modifier.fillMaxWidth()){
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Wifi Station (Client) Connection", style = TextStyle(fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row (modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center)
                        {
                            WhiteButton(onClick = {
                                qrScannerLauncher.launch(ScanOptions().setOrientationLocked(false)
                                    .setPrompt("Scan another device to join the Mesh")
                                    .setBeepEnabled(true)
                                )},
                                modifier = Modifier.padding(4.dp),
                                text = "Connect via QR Code Scan",
                                // If the hotspot isn't started, enable the button
                                // Else, check if the device supports WiFi STA/AP Concurrency
                                // If it does, enable the button. Otherwise, disable it
                                enabled = if(!uiState.hotspotStatus) true else context.hasStaApConcurrency()
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Or, enter the connect URI below:\n" +
                                "1. Make sure Bluetooth is enabled\n" +
                                "2. Allow everyone to share data via Quick Share\n" +
                                "3. After getting the URI, click copy\n" +
                                "4. Paste the URI into the text field below\n" +
                                "5. Click \"Connect via Entering Connect URI\"")
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = userEnteredConnectUri,
                            onValueChange = {
                                userEnteredConnectUri = it
                            },
                            label = { Text("Enter Connect URI (Starts with \"meshrabiya://\")") }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        WhiteButton(
                            onClick = {
                                try {
                                    // Parse the link, get the wifi connect configuration.
                                    val hotSpot = MeshrabiyaConnectLink.parseUri(
                                        uri = userEnteredConnectUri,
                                        json = di.direct.instance()
                                    ).hotspotConfig
                                    // if the configuration is valid, connect to the device.
                                    if (hotSpot != null) {
                                        // Connect device thru wifi connection
                                        viewModel.onConnectWifi(hotSpot)
                                    } else {
                                        // Link doesn't have a connect config
                                    }
                                } catch (e: Exception) {
                                    // Invalid Link
                                }
                            },
                            modifier = Modifier.padding(4.dp),
                            text = "Connect via Entering Connect URI",
                            // If the hotspot isn't started, enable the button
                            // Else, check if the device supports WiFi STA/AP Concurrency
                            // If it does, enable the button. Otherwise, disable it
                            enabled = if (!uiState.hotspotStatus) true else context.hasStaApConcurrency()
                        )
                    }
                }
                // If the stationState is not INACTIVE, it displays a ListItem that represents
                // the current connection status.
                else{
                    ListItem(
                        headlineContent = {
                            Text(stationState.config?.ssid ?: "(Unknown SSID)")
                        },
                        supportingContent = {
                            Text(
                                (stationState.config?.nodeVirtualAddr?.addressToDotNotation() ?: "") +
                                        " - ${stationState.status}"
                            )
                        },
                        leadingContent = {
                            if(stationState.status == WifiStationState.Status.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(8.dp)
                                )
                            }else {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "",
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    onClickDisconnectWifiStation()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Disconnect",
                                )
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // add a Hotspot status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Hotspot Status: ${if (uiState.hotspotStatus) "Online" else "Offline"}")
                Spacer(modifier = Modifier.width(8.dp)) // Adds some space between text and dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (uiState.hotspotStatus) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

// Enable users to copy text by holding down the text for a long press
@Composable
fun LongPressCopyableText(context: Context, text: String, textCopyable: String, textSize: Int){
    val clipboardManager = LocalClipboardManager.current
    BasicText(
        text = text + textCopyable,
        style = TextStyle(fontSize = textSize.sp),
        modifier = Modifier.pointerInput(textCopyable) {
            detectTapGestures(
                onLongPress = {
                    clipboardManager.setText(AnnotatedString(textCopyable))
                    Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                })
        }
    )
}

// display the QR code
@Composable
fun QRCodeView(qrcodeUri: String, barcodeEncoder: BarcodeEncoder, ssid: String?, password: String?,
               mac: String?, port: String?) {
    val qrCodeBitMap = remember(qrcodeUri) {
        barcodeEncoder.encodeBitmap(
            qrcodeUri, BarcodeFormat.QR_CODE, 400, 400
        ).asImageBitmap()
    }
    Row {
        // QR Code left side, Device info on the right side
        Image(
            bitmap = qrCodeBitMap,
            contentDescription = "QR Code"
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "SSID: $ssid")
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Password: $password")
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "MAC Address: $mac")
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Port: $port")
        }
    }
}