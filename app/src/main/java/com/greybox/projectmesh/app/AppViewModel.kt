package com.greybox.projectmesh.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.arch.UserMessage
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.navigation.BottomNavItem
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.util.isIpv4
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

@Immutable
data class AppUiState(
    val hasRunBefore: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val languageCode: String = "en",
    val deviceName: String = "",
    val startDestination: String = BottomNavItem.Home.route,
    val userMessage: UserMessage? = null,
)

/** Parsed from the launch Intent in onCreate — the Android Intent never enters the VM. */
sealed interface DeepLink {
    data class OpenChat(val ip: String) : DeepLink                     // OPEN_CHAT_SCREEN (live, IPv4-validated, fallback Home)
    data class OpenConversation(val conversationId: String) : DeepLink // OPEN_CHAT_CONVERSATION (stored, NOT validated)
    data object OpenReceive : DeepLink
}

/**
 * App-shell state holder. Owns the two shell-only concerns — the current
 * [AppUiState.startDestination] and one-shot deep-link resolution — and mirrors
 * the four user-settable fields (theme/language/deviceName/hasRunBefore) from
 * [SettingsRepository], which is their single source of truth.
 */
class AppViewModel(
    di: DI,
    private val savedStateHandle: SavedStateHandle,
) : StateViewModel<AppUiState>(AppUiState()), DIAware {

    override val di = di
    private val settings: SettingsRepository by instance()

    /** Local carrier for the four mirrored repo fields (combine takes ≤5 flows). */
    private data class ShellSettings(
        val theme: AppTheme, val languageCode: String,
        val deviceName: String, val hasRunBefore: Boolean,
    )

    init {
        combine(
            settings.theme, settings.languageCode, settings.deviceName, settings.hasRunBefore,
        ) { theme, lang, name, hasRun -> ShellSettings(theme, lang, name, hasRun) }
            .onEach { s ->
                // Mirror ONLY repo-owned fields. Do NOT touch startDestination/userMessage —
                // the VM owns those; copying them here would clobber the user's current screen.
                setState {
                    copy(
                        theme = s.theme,
                        languageCode = s.languageCode,
                        deviceName = s.deviceName,
                        hasRunBefore = s.hasRunBefore,
                    )
                }
            }.launchIn(viewModelScope)

        // Restore start destination across process death (replaces rememberSaveable).
        savedStateHandle.get<String>(KEY_START_DEST)?.let { restored ->
            setState { copy(startDestination = restored) }
        }
    }

    /** Mark onboarding complete; the repo flow propagates the new value into state. */
    fun completeOnboarding() { settings.setHasRunBefore(true) }

    /** Record the current screen so it survives recomposition and process death. */
    fun onScreenChanged(route: String) {
        savedStateHandle[KEY_START_DEST] = route
        setState { copy(startDestination = route) }
    }

    /**
     * Resolve the launch deep link to a start destination — exactly once per logical
     * launch. Guarded by a [savedStateHandle] flag so a later recreate (rotation or
     * process death) re-delivering the same Intent does NOT override the restored
     * [AppUiState.startDestination].
     */
    fun applyDeepLink(deepLink: DeepLink?) {
        if (savedStateHandle.get<Boolean>(KEY_DEEPLINK_HANDLED) == true) return
        savedStateHandle[KEY_DEEPLINK_HANDLED] = true
        val dest = when (deepLink) {
            is DeepLink.OpenChat ->
                // Same IPv4 predicate the chat route uses (pure, no DNS). Invalid -> Home.
                if (isIpv4(deepLink.ip)) "chatScreen/${deepLink.ip}"
                else BottomNavItem.Home.route
            is DeepLink.OpenConversation ->
                "chatScreen/${deepLink.conversationId}" // not validated — a conversation ID isn't an IP; the route regex disambiguates
            DeepLink.OpenReceive -> BottomNavItem.Receive.route
            null -> currentState.startDestination
        }
        onScreenChanged(dest)
    }

    /** Clear the one-shot user message after the UI has shown it. */
    fun onUserMessageShown() = setState { copy(userMessage = null) }

    companion object {
        private const val KEY_START_DEST = "startDestination"
        private const val KEY_DEEPLINK_HANDLED = "deepLinkHandled"
    }
}
