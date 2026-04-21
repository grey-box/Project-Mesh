package com.greybox.projectmesh.messaging.ui.viewmodels

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.DeviceStatusManager
import com.greybox.projectmesh.messaging.repository.ConversationRepository
import com.greybox.projectmesh.messaging.ui.models.ConversationsHomeScreenModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import timber.log.Timber

class ConversationsHomeScreenViewModel(
    di: DI
) : ViewModel() {

    //keep track of UI state
    private val _uiState = MutableStateFlow(ConversationsHomeScreenModel(isLoading = true))
    val uiState: Flow<ConversationsHomeScreenModel> = _uiState.asStateFlow()

    //get repository instances
    private val conversationRepository: ConversationRepository by di.instance()

    // Get access to SharedPreferences for the local UUID
    private val sharedPrefs: SharedPreferences by di.instance(tag = "settings")

    init {
        loadConversations()

        viewModelScope.launch {
            DeviceStatusManager.deviceStatusMap
                .collect { deviceStatusMap ->
                    // Process immediately without debouncing
                    updateConversationStatuses(deviceStatusMap)
                }
        }
    }

    //Update conversations Status based on device status
    private fun updateConversationStatuses(deviceStatusMap: Map<String, Boolean>) {
        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher for database operations
            try {
                // Get current conversations once
                val currentConversations = _uiState.value.conversations

                // Create a list to track conversations needing updates
                val conversationsToUpdate = mutableListOf<Pair<String, Boolean>>()

                // Check which conversations need updates
                currentConversations.forEach { conversation ->
                    conversation.userAddress?.let { ipAddress ->
                        val isOnline = deviceStatusMap[ipAddress] ?: false
                        if (conversation.isOnline != isOnline) {
                            conversationsToUpdate.add(Pair(conversation.userUuid, isOnline))
                        }
                    }
                }

                // Batch update all needed conversations
                conversationsToUpdate.forEach { (userUuid, isOnline) ->
                    // Update conversation status in database
                    conversationRepository.updateUserStatus(
                        userUuid = userUuid,
                        isOnline = isOnline,
                        userAddress = if (isOnline)
                            currentConversations.find { it.userUuid == userUuid }?.userAddress
                        else null
                    )

                    Timber.tag("ConversationsViewModel").d(
                        "Updated conversation status for ${currentConversations.find { it.userUuid == userUuid }?.userName}: online=$isOnline")
                }

                // If we made any updates, force a refresh of the list
                if (conversationsToUpdate.isNotEmpty()) {
                    refreshConversations()
                }
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e,"Error updating conversation statuses")
            }
        }
    }


    private fun loadConversations() {
        viewModelScope.launch {
            try {
                //start with loading state
                _uiState.update { it.copy (isLoading = true, error = null )}

                //get local device id
                val localUuid = sharedPrefs.getString("UUID", null) ?: "local-user"
                Timber.tag("ConversationsViewModel").d("Local UUID: $localUuid")

                //collect conversations from repository
                conversationRepository.getAllConversations().collect { conversations ->
                    Timber.tag("ConversationsViewModel").d("Loaded ${conversations.size} " +
                            "conversations")

                    //filter out conversations with self
                    val filteredConversations = conversations.filter { conversation ->
                        conversation.userUuid != localUuid
                    }

                    conversations.forEach { conversation ->
                        Timber.tag("ConversationsViewModel").d("Conversation: ID=${conversation
                            .id}, UserUUID=${conversation.userUuid}, Name=${conversation.userName}")
                    }

                    Timber.tag("ConversationsViewModel").d("Filtering out conversations with UUID:$localUuid")

                    Timber.tag("ConversationsViewModel").d("After filtering self:${filteredConversations.size} conversations")


                    //update the UI state with the conversations
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            conversations = filteredConversations,
                            error = null
                        )
                    }
                }
            }catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e,"Error loading conversations")

                _uiState.update {
                    it.copy (
                        isLoading = false,
                        error = "Failed to load conversations: ${e.message}"
                    )
                }
            }
        }
    }

    //function to refresh conversations manually
    fun refreshConversations(){
        loadConversations()
    }

    //Function to mark a conversation as read
    fun markConversationAsRead(conversationId: String) {
        viewModelScope.launch {
            try {
                conversationRepository.markAsRead(conversationId)
            } catch (e: Exception) {
                Timber.tag("ConversationsViewModel").e(e, "Error marking conversation as read")
            }
        }
    }
}