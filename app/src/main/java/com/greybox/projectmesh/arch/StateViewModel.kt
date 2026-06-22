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
 *  - UI collects with collectAsStateWithLifecycle().
 *  - Derived hot flows use stateIn(viewModelScope, WhileSubscribed(5_000), initial).
 */

abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()
    protected val currentState: S get() = _state.value
    protected fun setState(reducer: S.() -> S) = _state.update(reducer)
}