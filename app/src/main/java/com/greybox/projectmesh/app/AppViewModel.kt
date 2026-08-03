package com.greybox.projectmesh.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.arch.StateViewModel
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
)

/** Parsed from the launch Intent in onCreate — the Android Intent never enters the VM. */
sealed interface DeepLink {
    data class OpenChat(val ip: String) : DeepLink
    data class OpenConversation(val conversationId: String) : DeepLink 
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
        // seed synchronously from the repo's CURRENT values before the
        // mirror launches — the first frame reads state.value ahead of the collector
        // coroutine's first dispatch, so unseeded defaults (hasRunBefore=false,
        // theme=SYSTEM) would flash for one frame on an existing install.
        setState { withShellSettings(currentShellSettings()) }

        // Mirror: every LATER repo change flows into state.
        combine(
            settings.theme, settings.languageCode, settings.deviceName, settings.hasRunBefore,
        ) { theme, lang, name, hasRun -> ShellSettings(theme, lang, name, hasRun) }
            .onEach { s -> setState { withShellSettings(s) } }
            .launchIn(viewModelScope)

        // Restore start destination across process death (replaces rememberSaveable).
        savedStateHandle.get<String>(KEY_START_DEST)?.let { restored ->
            setState { copy(startDestination = restored) }
        }
    }

    /** Snapshot of the repo's current values — synchronous, because they're StateFlows. */
    private fun currentShellSettings() = ShellSettings(
        theme = settings.theme.value,
        languageCode = settings.languageCode.value,
        deviceName = settings.deviceName.value,
        hasRunBefore = settings.hasRunBefore.value,
    )

    /** The ONE carrier→state mapping, shared by seed and mirror. Repo-owned fields
     *  only — never startDestination. the VM owns this and copying
     *  it here would clobber the user's current screen. */
    private fun AppUiState.withShellSettings(s: ShellSettings) = copy(
        theme = s.theme,
        languageCode = s.languageCode,
        deviceName = s.deviceName,
        hasRunBefore = s.hasRunBefore,
    )
    /** Mark onboarding complete; the repo flow propagates the new value into state. */
    fun completeOnboarding() { settings.setHasRunBefore(true) }

    /** Record the current screen so it survives recomposition and process death. */
    fun onScreenChanged(route: String) {
        savedStateHandle[KEY_START_DEST] = route
        setState { copy(startDestination = route) }
    }

    /**
     * Resolve the launch deep link to a start destination — exactly once per logical
     * launch. Guarded by a [savedStateHandle] flag so a later recreate
     * (rotation or process death) re-delivering the same Intent does NOT override the
     * restored [AppUiState.startDestination].
     *
     * This is also the deep-link trust boundary: the extras arrive through an
     * exported=true Activity, so both payload variants are validated before route
     * interpolation — [DeepLink.OpenChat] via [isIpv4],
     * [DeepLink.OpenConversation] via [CONVERSATION_ID_REGEX].
     * Invalid values fall back to Home; the link is still consumed either way.
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
                // SAME trust boundary as OpenChat (exported=true Activity).
                // Unvalidated, an embedded '/' fails single-segment {ip} matching and
                // onScreenChanged would persist the malformed route into KEY_START_DEST,
                // so the restore path replays it. Invalid -> Home, symmetric with OpenChat.
                if (CONVERSATION_ID_REGEX.matches(deepLink.conversationId))
                    "chatScreen/${deepLink.conversationId}"
                else BottomNavItem.Home.route
            DeepLink.OpenReceive -> BottomNavItem.Receive.route
            null -> currentState.startDestination
        }
        onScreenChanged(dest)
    }
    
    companion object {
        /** accepted conversation-ID shape: sorted UUIDs joined by
        *  '-' (sender: AppServer.kt:651). Test-device IDs such as
        *  "local-user-test-device-uuid" also match. Regex.matches() requires a
        *  FULL match, so '/', whitespace, and empty strings are all rejected. */
        private val CONVERSATION_ID_REGEX = Regex("[A-Za-z0-9-]+")
        private const val KEY_START_DEST = "startDestination"
        private const val KEY_DEEPLINK_HANDLED = "deepLinkHandled"
    }
}
