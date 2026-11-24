package com.greybox.projectmesh.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.extension.getLocalIpFromDI
import com.greybox.projectmesh.user.UserRepository
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import timber.log.Timber

/**
 * The three phases of the onboarding flow. A single enum instead of parallel
 * booleans, so only the states the flow actually uses are representable:
 * [Editing] is the pager, [Submitting] is the async setup window (second taps
 * are dropped), [Complete] tells the shell setup is done.
 */
enum class OnboardingPhase { Editing, Submitting, Complete }

@Immutable
data class OnboardingUiState(
    val username: String = "",
    val phase: OnboardingPhase = OnboardingPhase.Editing,
    val showHowItWorks: Boolean = false,
)

class OnboardingViewModel(
    di: DI,
) : StateViewModel<OnboardingUiState>(OnboardingUiState()), DIAware {

    override val di = di
    private val userRepository: UserRepository by instance()
    private val settingsRepository: SettingsRepository by instance()
    private val localIp: String by lazy { getLocalIpFromDI(di) }

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
     * a generated guest name.
     */
    fun onSkipClick() = finishOnboarding()

    /**
     * The one completion path. The phase moves to [OnboardingPhase.Submitting]
     * synchronously, so a second tap while the repository writes are still in
     * flight is ignored. The screen observes the state and informs the shell
     * once the phase reaches [OnboardingPhase.Complete].
     */
    private fun finishOnboarding() {
        if (currentState.phase != OnboardingPhase.Editing) return
        setState { copy(phase = OnboardingPhase.Submitting) }
        viewModelScope.launch {
            val uuid = settingsRepository.localUuid
            if (uuid == null) {
                // GlobalApp.onCreate() persists the UUID before any activity can
                // run, so this is unreachable in production. If it ever trips
                // anyway, do not register a user against a missing UUID — fall
                // back to Editing so the user can try again.
                Timber.tag("OnboardingVM").e("No local UUID; setup aborted")
                setState { copy(phase = OnboardingPhase.Editing) }
                return@launch
            }
            // Blank username -> generated guest name. Written into state so the
            // final page would show it too if setup ever failed and the user stayed.
            val name = currentState.username.ifBlank {
                generateGuestUsername().also { generated ->
                    Timber.tag("Username").d("Username = $generated")
                    setState { copy(username = generated) }
                }
            }
            performFirstTimeSetup(uuid, name)
            setState { copy(phase = OnboardingPhase.Complete) }
        }
    }

    /** Register the user and device name under the UUID GlobalApp established at startup. */
    private suspend fun performFirstTimeSetup(uuid: String, name: String) {
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
