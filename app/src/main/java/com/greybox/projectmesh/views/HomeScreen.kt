package com.greybox.projectmesh.views

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.greybox.projectmesh.extension.NEARBY_WIFI_PERMISSION_NAME
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.extension.hasNearbyWifiDevicesOrLocationPermission
import com.greybox.projectmesh.viewModel.HomeScreenViewModel
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.MeshrabiyaConnectLink
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import com.ustadmobile.meshrabiya.vnet.wifi.state.WifiStationState
import org.kodein.di.compose.localDI
import org.kodein.di.direct
import org.kodein.di.instance
import androidx.compose.runtime.State
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.greybox.projectmesh.extension.hasStaApConcurrency
import com.greybox.projectmesh.ui.theme.TransparentButton
import com.greybox.projectmesh.viewModel.HomeScreenModel
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.greybox.projectmesh.components.ConnectWifiLauncherResult
import com.greybox.projectmesh.components.ConnectWifiLauncherStatus
import com.greybox.projectmesh.components.meshrabiyaConnectLauncher
import com.ustadmobile.meshrabiya.log.MNetLogger

@Composable
// We customize the viewModel since we need to inject dependencies
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> HomeScreenViewModel(di, savedStateHandle) },
            defaultArgs = null)),
    deviceName: String?
)
{
    val context = LocalContext.current
    val di = localDI()
    val uiState: HomeScreenModel by viewModel.uiState.collectAsState(initial = HomeScreenModel())
    val node: VirtualNode by di.instance()
    val logger: MNetLogger by di.instance()
    val currConcurrencyKnown = viewModel.concurrencyKnown.collectAsState()
    val currConcurrencySupported = viewModel.concurrencySupported.collectAsState()

    // Request nearby wifi permission since it is necessary to start a hotspot
    val requestNearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){ granted -> if (granted){
        if(context.hasNearbyWifiDevicesOrLocationPermission()){
            viewModel.onSetIncomingConnectionsEnabled(true)
        }
    } }

    // if not known and android version >= 11, then use official api to check concurrency
    if(!currConcurrencyKnown.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
        viewModel.saveConcurrencyKnown(true)
        viewModel.saveConcurrencySupported(context.hasStaApConcurrency())
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Launch the home screen
    StartHomeScreen(
        uiState = uiState,
        node = node as AndroidVirtualNode,
        onSetIncomingConnectionsEnabled = { enabled ->
            if(enabled) {
                if (!context.hasNearbyWifiDevicesOrLocationPermission()) {
                    requestNearbyWifiPermissionLauncher.launch(NEARBY_WIFI_PERMISSION_NAME)
                    return@StartHomeScreen
                }
                if (uiState.hotspotTypeToCreate == HotspotType.WIFIDIRECT_GROUP) {
                    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    if (!wifiManager.isWifiEnabled) {
                        Toast.makeText(context, "Please Turn on Wifi", Toast.LENGTH_SHORT).show()
                        return@StartHomeScreen
                    }
                }
            }
            viewModel.onSetIncomingConnectionsEnabled(enabled)
        },
        onClickDisconnectWifiStation = viewModel::onClickDisconnectStation,
        deviceName = deviceName,
        context = context,
        currConcurrencyKnown = currConcurrencyKnown,
        currConcurrencySupported = currConcurrencySupported,
        onSetBand = viewModel::onConnectBandChanged,
        onSetHotspotTypeToCreate = viewModel::onSetHotspotTypeToCreate,
        onConnectWifiLauncherResult = { result ->
            if(result.hotspotConfig != null) {
                viewModel.onConnectWifi(result.hotspotConfig)
            }else {
                errorMessage = result.exception?.message
            }
        },
        logger = logger
    )
    // Show an error dialog when needed
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Connection Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

// Display the home screen
@Composable
fun StartHomeScreen(
    uiState: HomeScreenModel,
    node: AndroidVirtualNode,
    onSetIncomingConnectionsEnabled: (Boolean) -> Unit = { },
    onClickDisconnectWifiStation: () -> Unit = { },
    viewModel: HomeScreenViewModel = viewModel(),
    deviceName: String?,
    context: Context,
    currConcurrencyKnown: State<Boolean>,
    currConcurrencySupported: State<Boolean>,
    onSetBand: (ConnectBand) -> Unit = { },
    onSetHotspotTypeToCreate: (HotspotType) -> Unit = { },
    onConnectWifiLauncherResult: (ConnectWifiLauncherResult) -> Unit,
    logger: MNetLogger
){
    val di = localDI()
    val barcodeEncoder = remember { BarcodeEncoder() }
    var connectLauncherState by remember {
        mutableStateOf(ConnectWifiLauncherStatus.INACTIVE)
    }
    val connectLauncher = meshrabiyaConnectLauncher(
        node = node,
        logger = logger,
        onStatusChange = {
            connectLauncherState = it
        },
        onResult = onConnectWifiLauncherResult,
    )
    val showSettings = remember { mutableStateOf(false) }
    var userEnteredConnectUri by rememberSaveable { mutableStateOf("") }
    var showShareBox by remember { mutableStateOf(false) }
    var showEnterUriBox by remember { mutableStateOf(false) }
    val showNoConcurrencyWarning by viewModel.showNoConcurrencyWarning.collectAsState()
    val showConcurrencyWarning by viewModel.showConcurrencyWarning.collectAsState()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    // connect to other device via connect uri
    fun connect(uri: String, logger: MNetLogger): Unit {
        try {
            logger(Log.INFO, "HomeScreen: Scanned link: $uri")
            // Parse the link, get the wifi connect configuration.
            val hotSpot = MeshrabiyaConnectLink.parseUri(
                uri = uri,
                json = di.direct.instance()
            ).hotspotConfig
            // if the configuration is valid, connect to the device.
            if (hotSpot != null) {
                if(hotSpot.nodeVirtualAddr !in uiState.nodesOnMesh) {
                    // Connect device thru wifi connection
                    connectLauncher.launch(hotSpot)
                }else{
                    Toast.makeText(context, "Already connected to this device", Toast.LENGTH_SHORT).show()
                    logger(Log.INFO,"Already connected to this device")
                }
            } else {
                Toast.makeText(context, "Link doesn't have a connect config", Toast.LENGTH_SHORT).show()
                logger(Log.WARN, "HomeScreen: Link doesn't have a connect config")
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Invalid Link", Toast.LENGTH_SHORT).show()
            logger(Log.WARN, "HomeScreen: Invalid Link", e)
        }
    }

    // initialize the QR code scanner
    val qrScannerLauncher = rememberLauncherForActivityResult(contract = ScanContract()) { result ->
        // Get the contents of the QR code
        val link = result.contents
        if (link != null) {
            connect(link, logger)
        }else{
            Toast.makeText(context, "QR Code scan doesn't return a link", Toast.LENGTH_SHORT).show()
            logger(Log.INFO,"QR Code scan doesn't return a link")
        }
    }

    // Show warning popup when device does not support STA/AP concurrency
    if (showNoConcurrencyWarning) {
        NoConcurrencyWarningDialog(onDismiss = { viewModel.dismissNoConcurrencyWarning() })
    }

    // Show warning popup when device does not support STA/AP concurrency
    if (showConcurrencyWarning) {
        ConcurrencyWarningDialog(onDismiss = { viewModel.dismissConcurrencyWarning() })
    }

    // Function to check if Wi-Fi Direct is supported
    fun isWifiDirectSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item (key = "logo") {
            // Logo & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Image(painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "logo",
                        modifier = Modifier.size(80.dp))
                    Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                        Text("Project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("MESH", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(
                    onClick = { showSettings.value = true },
                    modifier = Modifier
                        .padding(20.dp)
                        .size(60.dp)) {
                    Icon(Icons.Default.AccountCircle,
                        contentDescription = "Settings",
                        modifier = Modifier.fillMaxSize())
                }
            }
        }

        item (key = "band_option"){
            if (uiState.connectBandVisible) {
                Row (modifier = Modifier.padding(horizontal = 6.dp)){
                    uiState.bandMenu.forEach { band ->
                        FilterChip(
                            selected = uiState.band == band,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .size(100.dp, 50.dp),
                            onClick = {
                                onSetBand(band)
                            },
                            label = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(band.toString())
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE5E5E5),
                                selectedLabelColor = Color.Black,
                                containerColor = Color.White,
                                labelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }

        item (key = "hotspot_type_option") {
            if(!uiState.wifiConnectionEnabled) {
                val wifiDirectSupported = isWifiDirectSupported(context)
                Row(modifier = Modifier.padding(horizontal = 6.dp)){
                    uiState.hotspotTypeMenu.forEach { hotspotType ->
                        val isDisabled = (hotspotType == HotspotType.WIFIDIRECT_GROUP && !wifiDirectSupported)
                        FilterChip(
                            enabled = !isDisabled,
                            selected = hotspotType == uiState.hotspotTypeToCreate,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .size(100.dp, 50.dp),
                            onClick = { onSetHotspotTypeToCreate(hotspotType) },
                            label = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(hotspotType.toString())
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEAEAEA),
                                selectedLabelColor = Color.Black,
                                containerColor = Color.White,
                                labelColor = Color.Black
                            )

                        )
                    }
                }
            }
        }

        item (key = "start_stop_hotspot_button"){
            // Display the "Start Hotspot" button
            val stationState = uiState.wifiState?.wifiStationState
            if (!uiState.wifiConnectionEnabled) {
                Row{
                    TransparentButton(
                        onClick = { onSetIncomingConnectionsEnabled(true) },
                        text = stringResource(id = R.string.start_hotspot),
                        // If not connected to a WiFi, enable the button
                        // Else, check if the device supports WiFi STA/AP Concurrency
                        // If it does, enable the button. Otherwise, disable it
                        enabled = if(stationState == null || stationState.status == WifiStationState.Status.INACTIVE)
                            true
                        else
                            currConcurrencySupported.value
                    )
                }
            }
            // Display the "Stop Hotspot" button
            else{
                Row{
                    TransparentButton(
                        onClick = {
                            stopHotspotConfirmationDialog(context) { onConfirm ->
                                if (onConfirm) {
                                    onSetIncomingConnectionsEnabled(false)
                                }
                            }
                        },
                        text = stringResource(id = R.string.stop_hotspot),
                        enabled = true
                    )
                }
            }
        }

        item(key = "qr_code_view"){
            // Generating QR CODE
            val connectUri = uiState.connectUri
            if (connectUri != null && uiState.wifiConnectionEnabled) {
                QRCodeView(
                    connectUri,
                    barcodeEncoder,
                    showShareBox,
                    onToggleShareBox = { showShareBox = !showShareBox },
                    uiState.wifiState?.connectConfig?.ssid,
                    uiState.wifiState?.connectConfig?.passphrase,
                    uiState.wifiState?.connectConfig?.bssid,
                    uiState.wifiState?.connectConfig?.port.toString()
                )

                // Display connectUri
                if(showShareBox){
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = stringResource(id = R.string.instruction_start_hotspot),
                                modifier = Modifier.padding(start = 16.dp))
                            TransparentButton(
                                onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, connectUri)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                },
                                text = stringResource(id = R.string.share_connect_uri),
                                enabled = true,
                            )
                        }
                    }
                }
            }
        }

        item(key = "connect_via_qr_code") {
            val stationState = uiState.wifiState?.wifiStationState
            // Scan the QR CODE
            // If the stationState is not null and its status is INACTIVE,
            // It will display the option to connect via a QR code scan.
            if (stationState != null){
                if (stationState.status == WifiStationState.Status.INACTIVE){
                    Row{
                        TransparentButton(onClick = {
                            qrScannerLauncher.launch(ScanOptions().setOrientationLocked(false)
                                .setPrompt("Scan another device to join the Mesh")
                                .setBeepEnabled(true)
                            )},
                            text = stringResource(id = R.string.connect_via_qr_code_scan),
                            // If the hotspot isn't started, enable the button
                            // Else, check if the device supports WiFi STA/AP Concurrency
                            // If it does, enable the button. Otherwise, disable it
                            enabled = if(!uiState.hotspotStatus)
                                true
                            else
                                currConcurrencySupported.value
                        )
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { showEnterUriBox = !showEnterUriBox }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Manual Entry",
                                    textDecoration = TextDecoration.Underline,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Help,
                                    contentDescription = "QuestionMark",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (showEnterUriBox) {
                            Column{
                                Text(
                                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                                    text = stringResource(id = R.string.instruction)
                                )
                                TextField(
                                    value = userEnteredConnectUri,
                                    onValueChange = { userEnteredConnectUri = it },
                                    label = { Text(stringResource(id = R.string.prompt_enter_uri)) },
                                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
                                )
                                TransparentButton(
                                    onClick = { connect(userEnteredConnectUri, logger) },
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    text = stringResource(id = R.string.connect_via_entering_connect_uri),
                                    enabled = !uiState.hotspotStatus || currConcurrencySupported.value
                                )
                            }
                        }
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
                                    modifier = Modifier.padding(6.dp),
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Wifi Icon",
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    disconnectConfirmationDialog(context) { onConfirm ->
                                        if (onConfirm) {
                                            onClickDisconnectWifiStation()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Disconnect",
                                )
                            }
                        }
                    )
                }
            }
        }

        // add a Mesh status indicator
        item(key = "mesh_status_indicator"){
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mesh Status",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.nodesOnMesh.isNotEmpty()){
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Online",
                                tint = Color(0xFF4CAF50), // Green color
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Online",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "No. of Nodes: " + uiState.nodesOnMesh.size,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                        else{
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Offline",
                                tint = Color.Red, // Green color
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "No. of Nodes: 0",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun stopHotspotConfirmationDialog(context: Context, onConfirm: (Boolean) -> Unit){
    AlertDialog.Builder(context)
        .setTitle("Do you want to turn off the hotspot?")
        .setPositiveButton("Yes"){ _, _ ->
            onConfirm(true)
        }
        .setNegativeButton("No"){ _, _ ->
            onConfirm(false)
        }
        .show()
}

fun disconnectConfirmationDialog(context: Context, onConfirm: (Boolean) -> Unit){
    AlertDialog.Builder(context)
        .setTitle("Do you want to disconnect the network?")
        .setPositiveButton("Yes"){ _, _ ->
            onConfirm(true)
        }
        .setNegativeButton("No"){ _, _ ->
            onConfirm(false)
        }
        .show()
}

// Enable users to copy text by holding down the text for a long press
@Composable
fun LongPressCopyableText(context: Context,
                          text: String,
                          textCopyable: String,
                          textSize: Int,
                          padding: Int = 0){
    val clipboardManager = LocalClipboardManager.current
    BasicText(
        text = text + textCopyable,
        style = TextStyle(
            fontSize = textSize.sp,
            color = MaterialTheme.colorScheme.onBackground),
        modifier = Modifier
            .pointerInput(textCopyable) {
                detectTapGestures(
                    onLongPress = {
                        clipboardManager.setText(AnnotatedString(textCopyable))
                        Toast
                            .makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT)
                            .show()
                    })
            }
            .padding(padding.dp)
    )
}

// display the QR code
@Composable
fun QRCodeView(qrcodeUri: String, barcodeEncoder: BarcodeEncoder,
               showShareBox: Boolean, onToggleShareBox: () -> Unit,
               ssid: String?, password: String?,
               mac: String?, port: String?) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    // Convert dp to int once and remember the value
    val qrCodeSize = remember(density, screenWidthDp) {
        with(density) { screenWidthDp.times(0.5f).roundToPx() } // Converts to Int
    }
    val qrCodeBitMap = remember(qrcodeUri) {
        barcodeEncoder.encodeBitmap(
            qrcodeUri, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize
        ).asImageBitmap()
    }
    Box (modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Scan QR Code To Connect",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Image(
                bitmap = qrCodeBitMap,
                contentDescription = "QR Code"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row (
                modifier = Modifier.clickable{ onToggleShareBox() }
            ){
                Text(text = "Share Connect URI ", textDecoration = TextDecoration.Underline)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = "QuestionMark",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun NoConcurrencyWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("STA/AP Concurrency Not Supported") },
        text = {
            Text(
                "Based on our test, we detected that your device does not support simultaneous Wi-Fi and hotspot usage (STA/AP concurrency)."
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("OK")
            }
        }
    )
}

@Composable
fun ConcurrencyWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("STA/AP Concurrency Supported") },
        text = {
            Text(
                "Based on our test, we detected that your device support simultaneous Wi-Fi and hotspot usage (STA/AP concurrency)."
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("OK")
            }
        }
    )
}