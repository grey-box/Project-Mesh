package com.greybox.projectmesh.views

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.greybox.projectmesh.extension.NEARBY_WIFI_PERMISSION_NAME
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.extension.hasNearbyWifiDevicesOrLocationPermission
import com.greybox.projectmesh.ui.theme.MeshTheme
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
import com.greybox.projectmesh.extension.hasStaApConcurrency
import com.greybox.projectmesh.ui.theme.TransparentButton
import com.ustadmobile.meshrabiya.vnet.wifi.ConnectBand
import com.ustadmobile.meshrabiya.vnet.wifi.HotspotType
import com.greybox.projectmesh.components.ConnectWifiLauncherResult
import com.greybox.projectmesh.components.ConnectWifiLauncherStatus
import com.greybox.projectmesh.components.MeshStatusDot
//contextual help
import com.greybox.projectmesh.components.HelpDialog
import com.greybox.projectmesh.components.HelpLink
import com.greybox.projectmesh.components.HelpParagraph
//end
import com.greybox.projectmesh.components.meshrabiyaConnectLauncher
import com.ustadmobile.meshrabiya.log.MNetLogger
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greybox.projectmesh.viewModel.HomeUiState

@Composable
// We customize the viewModel since we need to inject dependencies
fun HomeScreen(
    viewModel: HomeScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> HomeScreenViewModel(di, savedStateHandle) },
            defaultArgs = null)),
)
{
    val context = LocalContext.current
    val di = localDI()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val node: VirtualNode by di.instance()
    val logger: MNetLogger by di.instance()

    // Request nearby wifi permission since it is necessary to start a hotspot
    val requestNearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){ granted -> if (granted){
        if(context.hasNearbyWifiDevicesOrLocationPermission()){
            viewModel.setIncomingConnectionsEnabled(true)
        }
    } }

    // if not known and android version >= 11, then use official api to check concurrency
    if(!uiState.concurrencyKnown && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
        viewModel.onConcurrencyDetected(context.hasStaApConcurrency())
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
                        Toast.makeText(context, context.getString(R.string.error_wifi_off), Toast.LENGTH_LONG).show()
                        return@StartHomeScreen
                    }
                }
            }
            viewModel.setIncomingConnectionsEnabled(enabled)
        },
        onClickDisconnectWifiStation = viewModel::disconnectStation,
        onDismissNoConcurrencyWarning = viewModel::dismissNoConcurrencyWarning,
        onDismissConcurrencyWarning = viewModel::dismissConcurrencyWarning,
        context = context,
        onSetBand = viewModel::setBand,
        onSetHotspotTypeToCreate = viewModel::setHotspotType,
        onConnectWifiLauncherResult = { result ->
            if(result.hotspotConfig != null) {
                viewModel.connectWifi(result.hotspotConfig)
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
    uiState: HomeUiState,
    node: AndroidVirtualNode,
    onSetIncomingConnectionsEnabled: (Boolean) -> Unit = { },
    onClickDisconnectWifiStation: () -> Unit = { },
    context: Context,
    onSetBand: (ConnectBand) -> Unit = { },
    onSetHotspotTypeToCreate: (HotspotType) -> Unit = { },
    onConnectWifiLauncherResult: (ConnectWifiLauncherResult) -> Unit,
    logger: MNetLogger,
    onDismissNoConcurrencyWarning: () -> Unit = { },
    onDismissConcurrencyWarning: () -> Unit = { }
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
    var userEnteredConnectUri by rememberSaveable { mutableStateOf("") }
    //contextual help
    var showUriHelp by rememberSaveable { mutableStateOf(false) }
    var showHotspotOptionsHelp by rememberSaveable { mutableStateOf(false) }

    if (showUriHelp) {
        HelpDialog(
            title = stringResource(R.string.help_uri_title),
            onDismiss = { showUriHelp = false },
        ) {
            HelpParagraph(stringResource(R.string.help_uri_body_1))
            HelpParagraph(stringResource(R.string.help_uri_body_2))
            HelpParagraph(stringResource(R.string.help_uri_body_3))
            HelpParagraph(stringResource(R.string.help_uri_body_4))
        }
    }
    if (showHotspotOptionsHelp) {
        HelpDialog(
            title = stringResource(R.string.help_hotspot_options_title),
            onDismiss = { showHotspotOptionsHelp = false },
        ) {
            HelpParagraph(stringResource(R.string.help_hotspot_options_body))
        }
    }//end of contextual help

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
                Toast.makeText(context, context.getString(R.string.error_link_no_config), Toast.LENGTH_LONG).show()
                logger(Log.WARN, "HomeScreen: Link doesn't have a connect config")
            }
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.error_link_unreadable), Toast.LENGTH_LONG).show()
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
            Toast.makeText(context, context.getString(R.string.error_qr_unrelated), Toast.LENGTH_LONG).show()
            logger(Log.INFO,"QR Code scan doesn't return a link")
        }
    }

    // Show warning popup when device does not support STA/AP concurrency
    if (uiState.showNoConcurrencyWarning) {
        NoConcurrencyWarningDialog(onDismiss = onDismissNoConcurrencyWarning)
    }

    // Show warning popup when device does not support STA/AP concurrency
    if (uiState.showConcurrencyWarning) {
        ConcurrencyWarningDialog(onDismiss = onDismissConcurrencyWarning)
    }

    // Function to check if Wi-Fi Direct is supported
    fun isWifiDirectSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.sm),
    ) {
        item (key = "device_info") {
            LongPressCopyableText(
                context,
                text = stringResource(id = R.string.device_name) + ": ",
                textCopyable = uiState.deviceName,
                style = MeshTheme.typography.bodyMedium,
                padding = MeshTheme.spacing.sm,
                copyActionLabel = stringResource(id = R.string.copy_device_name)
            )
            LongPressCopyableText(
                context,
                text = stringResource(id = R.string.ip_address) + ": ",
                textCopyable = uiState.localAddress.addressToDotNotation(),
                style = MeshTheme.typography.bodyMedium,
                padding = MeshTheme.spacing.sm,
                copyActionLabel = stringResource(id = R.string.copy_ip_address)
            )
        }

        item (key = "band_option"){
            if (uiState.connectBandVisible) {
                Text(
                    modifier = Modifier.padding(horizontal = MeshTheme.spacing.sm),
                    text = stringResource(id = R.string.band),
                    style = MeshTheme.typography.bodySmall,
                )
                Row (modifier = Modifier.padding(horizontal = MeshTheme.spacing.sm)){
                    uiState.bandMenu.forEach { band ->
                        FilterChip(
                            selected = uiState.band == band,
                            modifier = Modifier.padding(
                                horizontal = MeshTheme.spacing.sm,
                                vertical = MeshTheme.spacing.sm
                            ),
                            onClick = {
                                onSetBand(band)
                            },
                            label = {
                                Text(band.toString())
                            },
                        )
                    }
                }
            }
        }
        //remade
        item (key = "hotspot_type_option") {
            if(!uiState.wifiConnectionEnabled) {
                val wifiDirectSupported = isWifiDirectSupported(context)
                //contextual help: label and Options link on one line
                Row(
                    modifier = Modifier.padding(horizontal = MeshTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.hotspot_type),
                        style = MeshTheme.typography.bodySmall,
                    )
                    HelpLink(
                        text = stringResource(R.string.help_hotspot_options_link),
                        style = MeshTheme.typography.bodySmall,
                        compact = true,
                        modifier = Modifier.padding(start = MeshTheme.spacing.sm),
                        onClick = { showHotspotOptionsHelp = true },
                    )
                }
                Row(modifier = Modifier.padding(horizontal = MeshTheme.spacing.sm)){
                    uiState.hotspotTypeMenu.forEach { hotspotType ->
                        val isDisabled = (hotspotType == HotspotType.WIFIDIRECT_GROUP && !wifiDirectSupported)
                        FilterChip(
                            enabled = !isDisabled,
                            selected = hotspotType == uiState.hotspotTypeToCreate,
                            modifier = Modifier.padding(
                                horizontal = MeshTheme.spacing.sm,
                                vertical = MeshTheme.spacing.sm
                            ),
                            onClick = { onSetHotspotTypeToCreate(hotspotType) },
                            label = { Text(hotspotType.toString()) }
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
                        modifier = Modifier.padding(MeshTheme.spacing.xs),
                        text = stringResource(id = R.string.start_hotspot),
                        // If not connected to a WiFi, enable the button
                        // Else, check if the device supports WiFi STA/AP Concurrency
                        // If it does, enable the button. Otherwise, disable it
                        enabled = if(stationState == null || stationState.status == WifiStationState.Status.INACTIVE)
                            true
                        else
                            uiState.concurrencySupported
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
                        modifier = Modifier.padding(MeshTheme.spacing.xs),
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
                    uiState.wifiState?.connectConfig?.ssid,
                    uiState.wifiState?.connectConfig?.passphrase,
                    uiState.wifiState?.connectConfig?.bssid,
                    uiState.wifiState?.connectConfig?.port.toString()
                )
                // Display connectUri
                Spacer(modifier = Modifier.height(MeshTheme.spacing.md))
                Text(text = stringResource(id = R.string.instruction_start_hotspot))
                Button(onClick = {
                    val sendIntent: Intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, connectUri)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }, modifier = Modifier.padding(MeshTheme.spacing.xs), enabled = true) {
                    Text(stringResource(id = R.string.share_connect_uri))
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
                    Text(
                        modifier = Modifier.padding(MeshTheme.spacing.sm),
                        text = stringResource(id = R.string.wifi_station_connection),
                        style = MeshTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(MeshTheme.spacing.md))
                    Row{
                        TransparentButton(onClick = {
                            qrScannerLauncher.launch(ScanOptions().setOrientationLocked(false)
                                .setPrompt("Scan another device to join the Mesh")
                                .setBeepEnabled(true)
                            )},
                            modifier = Modifier.padding(MeshTheme.spacing.xs),
                            text = stringResource(id = R.string.connect_via_qr_code_scan),
                            // If the hotspot isn't started, enable the button
                            // Else, check if the device supports WiFi STA/AP Concurrency
                            // If it does, enable the button. Otherwise, disable it
                            enabled = if(!uiState.hotspotStatus)
                                true
                            else
                                uiState.concurrencySupported
                        )
                    }
                    Text(
                        modifier = Modifier.padding(MeshTheme.spacing.sm),
                        text = stringResource(id = R.string.instruction)
                    )
                    //contextual help - HERE
                    Row(
                        modifier = Modifier.padding(horizontal = MeshTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = stringResource(R.string.help_uri_step_prefix))
                        HelpLink(
                            text = stringResource(R.string.help_uri_step_link),
                            style = MeshTheme.typography.bodyLarge,
                            compact = true,
                            onClick = { showUriHelp = true },
                        )
                        Text(text = stringResource(R.string.help_uri_step_suffix))
                    }//

                    TextField(
                        value = userEnteredConnectUri,
                        onValueChange = {
                            userEnteredConnectUri = it
                        },
                        label = { Text(stringResource(id = R.string.prompt_enter_uri)) }
                    )
                    TransparentButton(
                        onClick = {
                            connect(userEnteredConnectUri, logger)
                        },
                        modifier = Modifier.padding(MeshTheme.spacing.xs),
                        text = stringResource(id = R.string.connect_via_entering_connect_uri),
                        // If the hotspot isn't started, enable the button
                        // Else, check if the device supports WiFi STA/AP Concurrency
                        // If it does, enable the button. Otherwise, disable it
                        enabled = if (!uiState.hotspotStatus)
                            true
                        else
                            uiState.concurrencySupported
                    )
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
                                    modifier = Modifier.padding(MeshTheme.spacing.sm)
                                )
                            }else {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    // null rather than "". Both are silent to TalkBack,
                                    // but an empty string reads as an unfinished edit,
                                    // while null is the documented way to declare an icon
                                    // decorative. It is decorative here: the connection
                                    // state is in the supporting text beside it.
                                    contentDescription = null,
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
        }

        // add a Hotspot status indicator
        item(key = "hotspot_status_indicator"){
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modifier = Modifier.padding(MeshTheme.spacing.sm),
                    text = stringResource(id = R.string.hotspot_status) + ": " +
                            if (uiState.hotspotStatus) stringResource(
                                id = R.string.hotspot_status_online)
                            else stringResource(id = R.string.hotspot_status_offline))
                // statusOffline, not error, for the off state. The checklist asked for this
                // to be confirmed: a hotspot being off is a state the user chose, not a
                // fault, and `error` is reserved for things that went wrong. This is a
                // visible change — the dot goes from red to grey when the hotspot is off.
                //
                // Now shared with the other three dot sites via MeshStatusDot, which adds
                // the filled/hollow shape distinction. This site was already the safest of
                // the four, because the text to the left spells out "Online"/"Offline" in
                // words, but it was open-coding the same dot a fourth time.
                MeshStatusDot(
                    isOnline = uiState.hotspotStatus,
                    size = MeshTheme.size.statusDotInline
                )
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

// Enable users to copy text by holding down the text for a long press
//
// `textSize: Int` and `padding: Int` became a TextStyle and a Dp. The Int parameters were
// a literal smuggled across a function boundary: every call site passed a bare number
// that this function turned into `.sp` and `.dp`, so no caller could pass a token and the
// gate could never see the violation.
@OptIn(ExperimentalFoundationApi::class) // combinedClickable
@Composable
fun LongPressCopyableText(context: Context,
                          text: String,
                          textCopyable: String,
                          style: TextStyle = MeshTheme.typography.bodyMedium,
                          padding: Dp = MeshTheme.spacing.none,
                          copyActionLabel: String = "Copy"){
    val clipboardManager = LocalClipboardManager.current
    val copy = {
        clipboardManager.setText(AnnotatedString(textCopyable))
        Toast
            .makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT)
            .show()
    }
    // BasicText draws no colour of its own, so the role has to be applied here or the
    // text renders black on a dark background.
    val styled = style.copy(color = MeshTheme.colors.onBackground)

    // A plain tap copies, as well as a long press. A tap is reachable with a tremor, a
    // prosthetic, a stylus or a switch; a 500ms sustained press is not. The custom action
    // also lists Copy in TalkBack's actions menu. SC 2.1.1 (Keyboard) / SC 4.1.2.
    //
    // Chat bubbles used to share this function with tap left inert; they now have their
    // own tap-for-menu in MessageBubble, so this is only the device name and IP rows.
    //
    // A Box rather than a bare BasicText because the row needs a real 48dp touch target
    // and the text has to stay vertically centred inside it. heightIn alone would
    // top-align the label; heightIn + wrapContentHeight cancels the minimum outright,
    // since wrapContentHeight lets the content ignore incoming minimum constraints and
    // then reports the content's own size.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(copyActionLabel) {
                        copy()
                        true
                    }
                )
            }
            .heightIn(min = MeshTheme.size.touchTargetMin)
            .combinedClickable(
                onClickLabel = copyActionLabel,
                onClick = copy,
                onLongClick = copy
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(
            text = text + textCopyable,
            style = styled,
            modifier = Modifier.padding(padding)
        )
    }
}

// display the QR code
@Composable
fun QRCodeView(qrcodeUri: String, barcodeEncoder: BarcodeEncoder, ssid: String?, password: String?,
               mac: String?, port: String?) {
    val configuration = LocalConfiguration.current
    // Not a design value: this converts a system-reported screen width, so it stays a
    // raw .dp conversion rather than becoming a token.
    val screenWidthDp = configuration.screenWidthDp.dp
    val density = LocalDensity.current
    // Convert dp to int once and remember the value
    val qrCodeSize = remember(density, screenWidthDp) {
        with(density) { screenWidthDp.times(0.35f).roundToPx() } // Converts to Int
    }
    val qrCodeBitMap = remember(qrcodeUri) {
        barcodeEncoder.encodeBitmap(
            qrcodeUri, BarcodeFormat.QR_CODE, qrCodeSize, qrCodeSize
        ).asImageBitmap()
    }
    Row (modifier = Modifier.fillMaxWidth()) {
        // QR Code left side, Device info on the right side
        Image(
            bitmap = qrCodeBitMap,
            contentDescription = "QR Code"
        )
        Spacer(modifier = Modifier.width(MeshTheme.spacing.sm))
        Column(
            modifier = Modifier.weight(1f),
            // Four identical Spacers became one spacedBy, per page 05's rule to prefer
            // Arrangement.spacedBy over Spacer chains.
            verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.sm)
        ) {
            Text(text = "SSID: $ssid")
            Text(text = "Password: $password")
            Text(text = "MAC: $mac")
            Text(text = "Port: $port")
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
