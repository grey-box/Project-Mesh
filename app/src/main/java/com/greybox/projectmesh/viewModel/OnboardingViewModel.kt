package com.greybox.projectmesh.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import com.greybox.projectmesh.user.UserRepository
import android.content.SharedPreferences
import timber.log.Timber


data class OnboardingUiState(
    val username: String
)

class OnboardingViewModel(
    private val userRepository: UserRepository,
    private val prefs: SharedPreferences,
    private val localIp: String // Use the local IP here
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState(username = ""))
    val uiState = _uiState.asStateFlow()

    fun onUsernameChange(newUsername: String) {
        _uiState.value = _uiState.value.copy(username = newUsername)
    }

    fun handleFirstTimeSetup(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Generate or retrieve the user's UUID
            var uuid = prefs.getString("UUID", null)
            if (uuid.isNullOrEmpty()) {
                uuid = UUID.randomUUID().toString()
                prefs.edit().putString("UUID", uuid).apply()
            }
            // Insert or update the user with the local IP
            userRepository.insertOrUpdateUser(
                uuid = uuid,
                name = _uiState.value.username,
                address = localIp
            )
            prefs.edit().putString("device_name", _uiState.value.username).apply()
            prefs.edit().putBoolean("hasRunBefore", true).apply()
            onComplete()
        }
    }

    // In order to Generate Guest Username
    fun blankUsernameGenerator(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val allUsers = userRepository.getAllUsers()
            val guestUsernames = allUsers.mapNotNull { it.name }
                .filter { it.startsWith("Guest") }

            val guestNumbers = guestUsernames.mapNotNull { username ->
                username.removePrefix("Guest").toIntOrNull()
            }

            val nextGuestNumber = if (guestNumbers.isEmpty()) {
                1
            } else {
                guestNumbers.maxOrNull()!! + 1
            }

            val generatedUsername = "Guest$nextGuestNumber"
            Timber.tag("Username").d("Username = $generatedUsername")
            onResult(generatedUsername)
        }
    }
}
