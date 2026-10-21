// app/src/main/java/com/greybox/projectmesh/messaging/ui/screens/ChatScreen.kt

package com.greybox.projectmesh.messaging.ui.screens

import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.components.MeshAvatar
import com.greybox.projectmesh.components.MeshAvatarSize
import com.greybox.projectmesh.components.MeshScreenScaffold
import com.greybox.projectmesh.messaging.data.entities.Message
import com.greybox.projectmesh.messaging.ui.models.ChatScreenModel
import com.greybox.projectmesh.messaging.ui.viewmodels.ChatScreenViewModel
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.views.LongPressCopyableText
import com.greybox.projectmesh.testing.TestDeviceService
import kotlinx.coroutines.runBlocking
import org.kodein.di.compose.localDI
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import android.provider.OpenableColumns
import org.kodein.di.instance
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import timber.log.Timber

@Composable
fun ChatScreen(
    virtualAddress: InetAddress,
    userName: String? = null,
    isOffline: Boolean = false,
    onClickButton: () -> Unit,
    viewModel: ChatScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle ->
                ChatScreenViewModel(di, savedStateHandle)
            },
            defaultArgs = Bundle().apply {
                putSerializable("virtualAddress", virtualAddress)

            }
        )
    )
) {

    //get user info
    val userInfo = remember {
        if (userName != null) {
            //use provided userName if available
            userName
        } else {
            //else try to look it up from the repository
            runBlocking {
                GlobalApp.GlobalUserRepo.userRepository.getUserByIp(virtualAddress.hostAddress)?.name
                    ?: "Unknown User"
            }
        }
    }

    // Track device status from DeviceStatusManager
    var deviceStatus by remember { mutableStateOf(false) }

    // Only observe for real devices (not placeholders)
    val shouldTrackStatus = remember {
        virtualAddress.hostAddress != "0.0.0.0" &&
                virtualAddress.hostAddress != TestDeviceService.TEST_DEVICE_IP_OFFLINE
    }

    if (shouldTrackStatus) {
        // Collect directly from DeviceStatusManager
        val statusMap by DeviceStatusManager.deviceStatusMap.collectAsState()

        // Use LaunchedEffect with a key derived from the status map entry for this device
        LaunchedEffect(statusMap[virtualAddress.hostAddress]) {
            val newStatus = statusMap[virtualAddress.hostAddress] ?: false
            if (deviceStatus != newStatus) {
                Timber.tag(
                    "ChatScreen")
                    .d("Device status changed: ${virtualAddress.hostAddress} is now ${if
                            (newStatus) "online" else "offline"}"
                )
                deviceStatus = newStatus
            }
        }
    }

    //Grab AppServer fromm local DI
    val di = localDI()
    val appServer: AppServer by di.instance()

    val contextMessageForFile = LocalContext.current

    // set up the system file-picker
    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 1) start the transfer
            appServer.addOutgoingTransfer(selectedUri, virtualAddress)

            //Get name of file
            var displayName = "unknown"
            contextMessageForFile.contentResolver
                .query(selectedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            .takeIf { it >= 0 }
                            ?.let { idx ->
                                displayName = cursor.getString(idx)
                            }
                    }
                }
            //Send a chat message when file is sent via file transfer
            viewModel.sendChatMessage(
                virtualAddress,
                "File $displayName was sent",
                null
            )
        }
    }
    // declare the UI state, we can use the uiState to access the current state of the viewModel
    val uiState: ChatScreenModel by viewModel.uiState.collectAsState(initial = ChatScreenModel())
    var textMessage by rememberSaveable { mutableStateOf("") }

    val scrollState = rememberScrollState()

    // The input row is the Scaffold's bottomBar rather than a Box overlay with a
    // bottom-padding spacer underneath the message list. That 72dp was never
    // nav-bar clearance — it was reserving room for this row, and it was a guess: the
    // TextField grows to five lines, so a tall input used to ride up over the last
    // message. As a bottomBar the Scaffold measures the row and insets the content by
    // its real height, whatever that turns out to be.
    MeshScreenScaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshTheme.spacing.xs),
                verticalAlignment = Alignment.Bottom //Change the Alignment So that it does not
                // increase the height together with the TextField and be floating in the middle of the
                // screen
            ) {

                //File Picker Button
                // OutlinedIconButton instead of a hand-rolled clip + border + size: the
                // outline comes from Material 3, so neither the 2dp width nor the grey
                // needs a literal, and no border token has to be invented for one call site.
                OutlinedIconButton(
                    modifier = Modifier.size(MeshTheme.size.iconXl),
                    onClick = { pickFileLauncher.launch("*/*") }
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Select a file to send"
                    )
                }
                TextField(
                    // min is the accessibility touch-target floor; the old 40dp sat under
                    // it. The previous 120dp max is dropped because `maxLines = 5`
                    // already bounds the growth, and five lines is what 120dp encoded.
                    modifier = Modifier
                        .weight(3f)
                        .heightIn(min = MeshTheme.size.touchTargetMin)
                        .verticalScroll(scrollState),
                    value = textMessage,
                    onValueChange = {
                        textMessage = it
                    },
                    maxLines = 5,
                    enabled = deviceStatus // disable text input when user is offline
                )
                Button(modifier = Modifier.weight(1f), onClick = {
                    val message = textMessage.trimEnd()
                    //val imgpath = "sdcard/padorubastard.jpg"//test image path
                    //future implementation should implement file picker
                    //val filepath = Uri.parse(imgpath)
                    if(message.isNotEmpty()) {
                        viewModel.sendChatMessage(virtualAddress,message, null)
                        // resets the text field
                        textMessage = ""
                    }
                },
                    enabled = deviceStatus //disable button when user is offline
                ) {
                    Text(text = "Send")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            //add a status bar at the top of the chat
            UserStatusBar(
                userName = userInfo,
                isOnline = deviceStatus,
                userAddress = virtualAddress.hostAddress
            )

            // Show device status indicator
            if (!deviceStatus) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeshTheme.spacing.sm),
                    colors = CardDefaults.cardColors(
                        containerColor = MeshTheme.colors.errorContainer,
                        // onErrorContainer, not error: `error` is the colour for content on
                        // a neutral surface, and on errorContainer it is the washed-out
                        // pairing the on* rule exists to stop.
                        contentColor = MeshTheme.colors.onErrorContainer
                    )
                ){
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MeshTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ){
                        Icon(
                            imageVector = Icons.Default.SignalWifiOff,
                            contentDescription = null,
                            tint = MeshTheme.colors.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(MeshTheme.spacing.sm))
                        Text(
                            text = "User is offline. Messages cannot be sent.",
                            color = MeshTheme.colors.onErrorContainer
                        )
                    }
                }
            }

            DisplayAllMessages(uiState, onClickButton)
        }
    }
}

@Composable
fun UserStatusBar(
    userName: String,
    isOnline: Boolean,
    userAddress: String
){
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MeshTheme.spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline)
                MeshTheme.colors.primaryContainer
            else
                MeshTheme.colors.surfaceVariant,
            contentColor = if (isOnline)
                MeshTheme.colors.onPrimaryContainer
            else
                MeshTheme.colors.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeshTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            //User avatar/icon
            // MeshAvatar also removes a latent crash: the old code called
            // userName.first(), which throws on an empty name. The component falls back
            // to "?" instead.
            MeshAvatar(
                name = userName,
                size = MeshAvatarSize.Sm,
                isOnline = isOnline,
                showStatusDot = true
            )

            Spacer(modifier = Modifier.width(MeshTheme.spacing.md))

            //User name and status
            Column {
                Text(
                    text = userName,
                    style = MeshTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The separate inline status dot that used to sit here is gone: the
                    // avatar above now carries the status dot, and two dots 20dp apart
                    // driven by one boolean is the same duplicate-indicator bug as the
                    // twice-drawn unread badge in ConversationsHomeScreen. The word
                    // "Online"/"Offline" still carries the state, and carries it to
                    // screen readers too, which a coloured circle never did.
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        style = MeshTheme.typography.bodySmall,
                        color = MeshTheme.colors.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(MeshTheme.spacing.md))

                    // IP address
                    Text(
                        text = userAddress,
                        style = MeshTheme.typography.labelSmall,
                        color = MeshTheme.colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayAllMessages(uiState: ChatScreenModel, onClickButton: () -> Unit) {
    val context = LocalContext.current

    //track if messages are showing:
    val hasMessages = uiState.allChatMessages.isNotEmpty()

    LaunchedEffect(uiState.allChatMessages.size) {
        Timber.tag("ChatScreen").d("DisplayAllMessages with ${uiState.allChatMessages.size} %s",
                "messages")
    }

    LazyColumn{
        if (!hasMessages){
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MeshTheme.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No messages yet. Start a conversation!",
                        modifier = Modifier.padding(MeshTheme.spacing.md),
                        style = MeshTheme.typography.bodyLarge
                    )
                    if (uiState.offlineWarning != null) {
                        Text(
                            text = "You're currently offline. Any messages you send will be delivered when connection is restored.",
                            modifier = Modifier.padding(horizontal = MeshTheme.spacing.md),
                            style = MeshTheme.typography.bodyMedium,
                            color = MeshTheme.colors.error
                        )
                    }
                }
            }
        }

        items(
            items = uiState.allChatMessages
        ){ chatMessage ->
            Timber.tag("ChatDebug").d("Rendering message: ${chatMessage.content}")
            val sender: String = if (chatMessage.sender == "Me") {
                "Me"
            } else {
                val ipStr = uiState.virtualAddress.hostAddress
                val user = runBlocking {
                    GlobalApp.GlobalUserRepo.userRepository.getUserByIp(ipStr)
                }
                user?.name ?: chatMessage.sender
            }

            val sentBySelf = chatMessage.sender == "Me"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (sentBySelf) Arrangement.End else Arrangement.Start
            ) {
                MessageBubble(
                    chatMessage = chatMessage,
                    sentBySelf = sentBySelf,
                    messageContent = {
                        LongPressCopyableText(
                            context = context,
                            text = "",
                            textCopyable = chatMessage.content,
                            // was textSize = 15, which sat between bodyMedium (14) and
                            // bodyLarge (16). Message content is body text at the reading
                            // size, so bodyLarge.
                            style = MeshTheme.typography.bodyLarge
                        )
                    },
                    sender = sender,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    chatMessage: Message,
    sentBySelf: Boolean,
    messageContent: @Composable () -> Unit,
    sender: String,
    modifier: Modifier
){
    ElevatedCard(
        // The old hardcoded cyan, with no content colour, measured about 1.9:1 in dark. Both
        // sides now use a paired container/content token, so the text colour can no
        // longer drift away from whatever the bubble is sitting on.
        colors = CardDefaults.cardColors(
            containerColor = if (sentBySelf) MeshTheme.mesh.bubbleSent
            else MeshTheme.mesh.bubbleReceived,
            contentColor = if (sentBySelf) MeshTheme.mesh.onBubbleSent
            else MeshTheme.mesh.onBubbleReceived
        ),
        shape = MeshTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = MeshTheme.elevation.level1
        ),
        modifier = modifier
            .padding(MeshTheme.spacing.sm)
            .widthIn(max = MeshTheme.size.bubbleMaxWidth)
    ) {
        Column(modifier = Modifier.padding(MeshTheme.spacing.xs)) {
            Text(
                text = sender,
                style = MeshTheme.typography.labelMedium
            )

            messageContent()
            //ONLY SHOW FILE ATTACHMENT IF PRESENT
            if (chatMessage.file != null) {
                Spacer(modifier = Modifier.height(MeshTheme.spacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MeshTheme.colors.surfaceVariant.copy(alpha = 0.3f),
                            MeshTheme.shapes.extraSmall
                        )
                        .padding(MeshTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "File",
                        // iconXs, not iconLg. The checklist says "the 32dp attachment
                        // icon" but the literal in this file was 16dp, and page 05 defines
                        // iconXs as exactly "attachment icon inside a message bubble".
                        modifier = Modifier.size(MeshTheme.size.iconXs)
                    )
                    Spacer(modifier = Modifier.width(MeshTheme.spacing.xs))
                    Text(
                        text = chatMessage.file.toString(),
                        style = MeshTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Adding timestamp to bottom right of message bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm").format(Date(chatMessage.dateReceived)),
                    style = MeshTheme.typography.labelSmall
                )
            }
        }
    }
}
