package com.greybox.projectmesh.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main with a TestDispatcher for the duration of each test.
 * viewModelScope hardcodes Main.immediate, which does not exist on the JVM —
 * without this rule every StateViewModel constructor throws.
 *
 * Defaults to StandardTestDispatcher (queued, not eager) so the
 * seed tests stay discriminating: the repo mirror cannot run until the test
 * advances the dispatcher, so state correctness before any advance can only
 * come from the synchronous seed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
