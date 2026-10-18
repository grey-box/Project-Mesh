package com.greybox.projectmesh.arch

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Minimal base for a screen state holder (modern MVVM / UDF).
 *
 * Contract every screen ViewModel follows:
 *  - ONE @Immutable data class per screen, exposed as a single [state] StateFlow.
 *    No loose parallel StateFlows.
 *  - Public methods (setX / onY / connectZ) are the ONLY entry points for state
 *    changes. Each calls [setState] to produce the next immutable state.
 *  - One-off signals (toasts, "show this once") are modeled AS STATE: add a
 *    nullable field to the state (e.g. userMessage: UserMessage?) and expose an
 *    onXShown() method the UI calls to clear it. Do NOT use Channel/SharedFlow
 *    for one-off events (delivery is not guaranteed across lifecycle changes).
 *  - The single userMessage slot is LAST-WRITER-WINS, deliberately: a second
 *    message may replace one the UI has not shown yet, and onXShown() clears
 *    whatever is there. Acceptable for toasts. If a screen ever needs every
 *    message delivered, upgrade THAT screen's field to a List<UserMessage>
 *    queue — never to a Channel/SharedFlow (see previous bullet).
 *  - When state mirrors a repository, the FIRST statement of init must seed
 *    the state synchronously from the repo flows' current .value s, through
 *    the same carrier->state mapping the mirror uses. The mirror's combine(...).launchIn is not
 *    guaranteed to emit before the first frame reads state.value, so an
 *    unseeded default state can flash for a frame.
 *  - UI collects with collectAsStateWithLifecycle().
 *  - Derived hot flows use stateIn(viewModelScope, WhileSubscribed(5_000), initial).
 */
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()
    protected val currentState: S get() = _state.value
    protected fun setState(reducer: S.() -> S) = _state.update(reducer)
}