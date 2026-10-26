package com.greybox.projectmesh.messaging.ui.screens

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.messaging.ui.viewmodels.ChatScreenViewModel
import com.greybox.projectmesh.testing.TestDeviceService
import com.greybox.projectmesh.ui.theme.MeshTheme
import java.net.InetAddress
import org.kodein.di.compose.localDI
import org.kodein.di.instance
import timber.log.Timber

@Composable
fun ConversationChatScreen (
        conversationId: String,
        onBackClick: () -> Unit
){
    Timber.tag("ConversationChatScreen").d("Starting to load conversation: $conversationId")

    var conversationState = remember { mutableStateOf<Conversation?>(null) }
    var isLoading = remember { mutableStateOf(true) }
    var errorMessage = remember { mutableStateOf<String?>(null) }
    //val conversation = conversationState.value
    val context = LocalContext.current
   val conversationRepository: ConversationRepository by localDI().instance()


    // Load the conversation data
    LaunchedEffect(conversationId) {
        Timber.tag("ConversationChatScreen").d("LaunchedEffect triggered for ID: $conversationId")
            try {
                Timber.tag("ConversationChatScreen").d("Attempting to fetch conversation")
                val result = conversationRepository.getConversationById(conversationId)
                Timber.tag("ConversationChatScreen").d("Fetch result: ${result != null}")
                //conversationState.value = result

                if (result == null) {
                    errorMessage.value = "Conversation not found"
                    isLoading.value = false
                    /*
                    Timber.tag("ConversationChatScreen").e("Conversation not found:
                    $conversationId")
                    Toast.makeText(
                        context,
                        "Conversation not found",
                        Toast.LENGTH_SHORT
                    ).show()
                    onBackClick()
                    */
                }else {
                    conversationState.value = result
                    isLoading.value = false
                    Timber.tag("ConversationChatScreen").d("Loaded conversation: ${result.userName}, online=${result.isOnline}")
                }
            } catch (e: Exception) {
                Timber.tag("ConversationChatScreen").e(e, "Error loading conversation")
                errorMessage.value = e.message
                isLoading.value = false
                /*
                Toast.makeText(
                    context,
                    "Error loading conversation: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                onBackClick()
                 */
            }
        
    }

    // Show loading state while fetching conversation
    if (isLoading.value) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    //show error if loading failed
    if (errorMessage.value != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // This state had no padding at all, so a long error string ran into both
            // screen edges. The gutter and the gap between the message and the button
            // both come from the spacing scale rather than from literals.
            Column(
                modifier = Modifier.padding(MeshTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.md)
            ) {
                Text(
                    text = "Error: ${errorMessage.value}",
                    style = MeshTheme.typography.bodyLarge,
                    color = MeshTheme.colors.error,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBackClick) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    //get the conversation from state
    val conversation = conversationState.value
    if (conversation == null) {
        Toast.makeText(context, "Conversation not found", Toast.LENGTH_SHORT).show()
        onBackClick()
        return
    }

    val shouldCheckDeviceStatus = remember {
        // Only check for devices with real IP addresses, not placeholder or offline devices
        conversation.userAddress != null &&
                conversation.userAddress != "0.0.0.0" &&
                conversation.userAddress != TestDeviceService.TEST_DEVICE_IP_OFFLINE
    }

    // Initialize isUserOnline based on conversation state first
    var isUserOnline by remember { mutableStateOf(conversation.isOnline) }

    // If we should check device status, observe DeviceStatusManager
    if (shouldCheckDeviceStatus) {
        val deviceStatusMap by DeviceStatusManager.deviceStatusMap.collectAsStateWithLifecycle()
        // Update isUserOnline based on DeviceStatusManager
        LaunchedEffect(deviceStatusMap) {
            conversation.userAddress?.let { ipAddress ->
                val statusFromManager = deviceStatusMap[ipAddress] ?: false
                if (isUserOnline != statusFromManager) {
                    Timber.tag("ConversationChatScreen").d("Device status changed for ${conversation
                        .userName}: ${if (statusFromManager) "online" else "offline"}")
                    isUserOnline = statusFromManager
                }
            }
        }
    }

    // Create a virtual address based on the conversation data
    val virtualAddress = if (conversation?.userAddress.isNullOrEmpty()) {
        //use offline test device address if this is the offline test conversation
        if (conversation.userName == TestDeviceService.TEST_DEVICE_NAME_OFFLINE) {
            Timber.tag("ConversationChatScreen").d("Using offline test device address")
            InetAddress.getByName(TestDeviceService.TEST_DEVICE_IP_OFFLINE)
        } else {
            //use a placeholder address for regular offline users
            Timber.tag("ConversationChatScreen").d("Using placeholder address for offline user")
            InetAddress.getByName("0.0.0.0")
        }
    } else {
        //use the actual address if available
        Timber.tag("ConversationChatScreen").d("Using actual address: ${conversation.userAddress}")
        InetAddress.getByName(conversation.userAddress)
    }

    Timber.tag("ConversationChatScreen").d("Showing chat screen for: ${conversation.userName}")

    // Show the chat screen
    ChatScreen(
        virtualAddress = virtualAddress,
        userName = conversation.userName,
        isOffline = !conversation.isOnline,
        onClickButton = {
            // Disable ping for offline users
            Toast.makeText(
                context,
                "Cannot ping offline users",
                Toast.LENGTH_SHORT
            ).show()
        },
        viewModel = viewModel(
            factory = ViewModelFactory(
                di = localDI(),
                owner = LocalSavedStateRegistryOwner.current,
                vmFactory = { di, savedStateHandle ->
                    // Make sure to set the conversation ID in the savedStateHandle
                    savedStateHandle["conversationId"] = conversationId
                    ChatScreenViewModel(di, savedStateHandle)
                },
                defaultArgs = Bundle().apply {
                    putSerializable("virtualAddress", virtualAddress)
                    putString("conversationId", conversationId)
                }
            )
        )
    )
}