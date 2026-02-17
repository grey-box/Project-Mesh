package com.greybox.projectmesh.messaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.ui.models.ConversationsHomeScreenModel
import com.greybox.projectmesh.messaging.ui.viewmodels.ConversationsHomeScreenViewModel
import com.greybox.projectmesh.messaging.utils.MessageUtils
import org.kodein.di.compose.localDI

/**
 * Main Composable for the Conversations Home screen.
 *
 * @param onConversationSelected Callback when a conversation is selected.
 * @param viewModel [ConversationsHomeScreenViewModel] providing the UI state.
 */
@Composable
fun ConversationsHomeScreen(
    onConversationSelected: (String) -> Unit,
    viewModel: ConversationsHomeScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, _ ->
                ConversationsHomeScreenViewModel(di)
            },
            defaultArgs = null
        )
    )
){
    val uiState: ConversationsHomeScreenModel by viewModel.uiState.collectAsState(
        initial = ConversationsHomeScreenModel(isLoading = true)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                ErrorView(
                    errorMessage = uiState.error!!,
                    onRetry = { viewModel.refreshConversations() }
                )
            }
            uiState.conversations.isEmpty() -> {
                EmptyConversationsView()
            }
            else -> {
                ConversationsList(
                    conversations = uiState.conversations,
                    onConversationClick = { conversation ->
                        //mark as read when opening the conversation
                        viewModel.markConversationAsRead(conversation.id)

                        //when user is online (has an IP address), navigate to chat
                        if (conversation.isOnline && conversation.userAddress != null) {
                            onConversationSelected(conversation.userAddress)
                        } else {
                            //when user if offline, still show the chat history ->
                            // handle in MainActivity with a disabled send button
                            onConversationSelected(conversation.id)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Displays a scrollable list of conversations.
 *
 * @param conversations List of [Conversation] objects to display.
 * @param onConversationClick Callback when a conversation item is clicked.
 */
@Composable
fun ConversationsList(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(conversations) { conversation ->
            ConversationItem(
                conversation = conversation,
                onClick = { onConversationClick(conversation) }
            )
            Divider(
                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * Displays an individual conversation item with avatar, status, last message, and unread count.
 *
 * @param conversation The [Conversation] to display.
 * @param onClick Callback for when the conversation item is clicked.
 */
@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //avatar/profile picture with online status indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    color = if (conversation.isOnline)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
                .border(
                    width = 2.dp,
                    color = if (conversation.isOnline) Color.Green else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.userName.firstOrNull()?.toString() ?: "?",
                color = Color.White,
                fontSize = 20.sp
            )

            //online status indicator
            if (conversation.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Green)
                        .border(1.dp, Color.White, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ){
                    Text(
                        text = conversation.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer (modifier = Modifier.width(8.dp))

                    //status indicator text
                    if (conversation.isOnline) {
                        Text (
                            text = "Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Green,
                            fontSize = 10.sp
                        )
                    }
                }

                //show the timestamp of the last message
                Text(
                    text = if (conversation.lastMessageTime > 0) {
                        MessageUtils.formatTimestamp(conversation.lastMessageTime)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Unread count badge
                if (conversation.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                //last message with status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    //online/offline indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (conversation.isOnline) Color.Green else Color.Gray
                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Last message content
                    Text(
                        text = conversation.lastMessage ?: "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                //unread count badge
                if (conversation.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = conversation.unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays a placeholder view when there are no conversations.
 */
@Composable
fun EmptyConversationsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Conversations Yet",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect with users on the network to start chatting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Displays an error view with retry button when conversation loading fails.
 *
 * @param errorMessage The error message to display.
 * @param onRetry Callback triggered when retry button is pressed.
 */
@Composable
fun ErrorView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Error Loading Conversations",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}
