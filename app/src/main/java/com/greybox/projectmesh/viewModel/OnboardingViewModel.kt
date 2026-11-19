package com.greybox.projectmesh.viewModel

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.user.UserRepository
import java.util.UUID
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Screen state per the approved OnboardingScreen contract:
 *  - completion is modelled AS STATE ([isSetupComplete]) instead of an `onComplete`
 *    callback handed INTO the ViewModel — the screen observes it and informs the shell.
 *  - `showHowItWorks` moved out of the composable's rememberSaveable into screen state.
 *  - [isSubmitting] is retained from the review pass: the repository writes are async,
 *    so a second tap in that window must be dropped, and the buttons use it to disable
 *    themselves.
 */
@Immutable
data class OnboardingUiState(
    val username: String = "",
    val isSetupComplete: Boolean = false,
    val showHowItWorks: Boolean = false,
    val isSubmitting: Boolean = false,
)

class OnboardingViewModel(
    private val userRepository: UserRepository,
    private val prefs: SharedPreferences,
    private val settingsRepository: SettingsRepository,
    private val localIp: String // Use the local IP here
) : StateViewModel<OnboardingUiState>(OnboardingUiState()) {

    fun onUsernameChange(newUsername: String) = setState { copy(username = newUsername) }

    /** User education page opened from page 1 of the pager. */
    fun onHowItWorksClick() = setState { copy(showHowItWorks = true) }

    /** Back from the user education page (returns to the same pager page). */
    fun onHowItWorksDismiss() = setState { copy(showHowItWorks = false) }

    /**
     * Get started (final page). Blank username gets a generated guest name;
     * a typed one is kept. Same single path as [onSkipClick].
     */
    fun onNextClick() = finishOnboarding()

    /**
     * Skip (pages 1–2). Deliberately the same single path as [onNextClick]:
     * a username typed on the final page is preserved, a blank one becomes
     * a generated guest name (review fix).
     */
    fun onSkipClick() = finishOnboarding()

    /**
     * The one completion path. `isSubmitting` is set synchronously so a
     * second tap is ignored while the setup writes are still in flight.
     * Only the state lives here: the shell is notified via [OnboardingUiState.isSetupComplete].
     */
    private fun finishOnboarding() {
        if (currentState.isSubmitting || currentState.isSetupComplete) return
        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            // Blank username -> generated guest name. Written into state so the
            // final page would show it too if setup ever failed and the user stayed.
            val name = currentState.username.ifBlank {
                generateGuestUsername().also { generated ->
                    Timber.tag("Username").d("Username = $generated")
                    setState { copy(username = generated) }
                }
            }
            performFirstTimeSetup(name)
            // Terminal state: the submitting flag comes back down so the final
            // state is a clean "done" (isSetupComplete alone also blocks re-entry).
            setState { copy(isSetupComplete = true, isSubmitting = false) }
        }
    }

    /** Generate or retrieve the user's UUID, then register the user and device name. */
    private suspend fun performFirstTimeSetup(name: String) {
        var uuid = prefs.getString("UUID", null)
        if (uuid.isNullOrEmpty()) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("UUID", uuid).apply()
        }
        // Insert or update the user with the local IP
        userRepository.insertOrUpdateUser(
            uuid = uuid,
            name = name,
            address = localIp
        )
        settingsRepository.setDeviceName(name)
    }

    /** Guest numbering continues from the highest existing GuestN, so Guest2 -> Guest3. */
    private suspend fun generateGuestUsername(): String {
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

        return "Guest$nextGuestNumber"
    }
}
