package com.greybox.projectmesh.messaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.components.MeshAvatar
import com.greybox.projectmesh.components.MeshAvatarSize
import com.greybox.projectmesh.messaging.data.entities.Conversation
import com.greybox.projectmesh.messaging.ui.models.ConversationsHomeScreenModel
import com.greybox.projectmesh.messaging.ui.viewmodels.ConversationsHomeScreenViewModel
import com.greybox.projectmesh.messaging.utils.MessageUtils
import com.greybox.projectmesh.ui.theme.MeshTheme
import org.kodein.di.compose.localDI

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

@Composable
fun ConversationsList(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit
) {
    // The divider indent is derived, not guessed: row gutter + avatar + the gap after it,
    // which is exactly where the text column starts. The old hardcoded 72dp sat 8dp left
    // of the text it was supposed to align with.
    val dividerIndent = MeshTheme.spacing.md + MeshTheme.size.avatarMd + MeshTheme.spacing.md

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = MeshTheme.spacing.sm)
    ) {
        items(conversations) { conversation ->
            ConversationItem(
                conversation = conversation,
                onClick = { onConversationClick(conversation) }
            )
            Divider(
                modifier = Modifier.padding(start = dividerIndent, end = MeshTheme.spacing.md),
                color = MeshTheme.colors.surfaceVariant
            )
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(MeshTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        //avatar/profile picture with online status indicator
        MeshAvatar(
            name = conversation.userName,
            size = MeshAvatarSize.Md,
            isOnline = conversation.isOnline,
            showStatusDot = conversation.isOnline
        )

        Spacer(modifier = Modifier.width(MeshTheme.spacing.md))

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
                        style = MeshTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer (modifier = Modifier.width(MeshTheme.spacing.sm))

                    //status indicator text
                    if (conversation.isOnline) {
                        Text (
                            text = "Online",
                            style = MeshTheme.typography.labelSmall,
                            color = MeshTheme.mesh.statusOnline
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
                    style = MeshTheme.typography.labelSmall,
                    color = MeshTheme.colors.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(MeshTheme.spacing.xs))

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
                            .size(MeshTheme.size.statusDotInline)
                            .clip(CircleShape)
                            .background(
                                if (conversation.isOnline) MeshTheme.mesh.statusOnline
                                else MeshTheme.mesh.statusOffline
                            )
                    )

                    Spacer(modifier = Modifier.width(MeshTheme.spacing.sm))

                    // Last message content
                    Text(
                        text = conversation.lastMessage ?: "No messages yet",
                        style = MeshTheme.typography.bodyMedium,
                        color = MeshTheme.colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Unread count badge. There used to be a second copy of this in the row
                // above, bound to the same unreadCount, so an unread conversation drew the
                // number twice at two different sizes. Material 3's Badge is used rather
                // than a hand-rolled Box so the size comes from the spec, not a literal.
                if (conversation.unreadCount > 0) {
                    Badge(
                        containerColor = MeshTheme.colors.primary,
                        contentColor = MeshTheme.colors.onPrimary
                    ) {
                        Text(text = conversation.unreadCount.toString())
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyConversationsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MeshTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(MeshTheme.size.iconEmptyState),
            tint = MeshTheme.colors.primary
        )

        Spacer(modifier = Modifier.height(MeshTheme.spacing.md))

        Text(
            text = "No Conversations Yet",
            style = MeshTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(MeshTheme.spacing.sm))

        Text(
            text = "Connect with users on the network to start chatting",
            style = MeshTheme.typography.bodyMedium,
            color = MeshTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MeshTheme.spacing.xl)
        )
    }
}

@Composable
fun ErrorView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MeshTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(MeshTheme.size.iconEmptyState),
            tint = MeshTheme.colors.error
        )

        Spacer(modifier = Modifier.height(MeshTheme.spacing.md))

        Text(
            text = "Error Loading Conversations",
            style = MeshTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(MeshTheme.spacing.sm))

        Text(
            text = errorMessage,
            style = MeshTheme.typography.bodyMedium,
            color = MeshTheme.colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MeshTheme.spacing.xl)
        )

        Spacer(modifier = Modifier.height(MeshTheme.spacing.md))

        Button(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = MeshTheme.spacing.md)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(MeshTheme.spacing.sm))
            Text("Retry")
        }
    }
}
