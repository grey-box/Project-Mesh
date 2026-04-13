package com.greybox.projectmesh.messaging.ui.models

import com.greybox.projectmesh.messaging.data.entities.Conversation

/**
 * Data model representing the state of the home screen showing all conversations.
 *
 * @property isLoading Indicates whether conversation data is currently being loaded.
 * @property conversations List of conversations to display on the home screen; defaults to empty list.
 * @property error Optional error message to display if loading or retrieving conversations fails.
 */
data class ConversationsHomeScreenModel (
    val isLoading: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val error: String? = null
)
