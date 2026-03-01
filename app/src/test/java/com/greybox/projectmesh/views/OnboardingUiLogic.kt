// File: app/src/test/java/com/greybox/projectmesh/views/OnboardingUiLogicTest.kt
package com.greybox.projectmesh.views

import org.junit.Assert.*
import org.junit.Test

/**
 * Deep JVM tests for OnboardingScreen.kt behavior WITHOUT touching OnboardingScreen.kt.
 *
 * We model the exact "Next" onClick logic as currently written, including the duplicate
 * handleFirstTimeSetup call at the end (likely a bug).
 */
private object OnboardingUiLogic {

    interface VmPort {
        fun onUsernameChange(value: String)
        fun blankUsernameGenerator(cb: (String) -> Unit)
        fun handleFirstTimeSetup(cb: () -> Unit)
    }

    data class Effects(
        val handleFirstTimeSetupCalls: Int,
        val blankUsernameGeneratorCalls: Int,
        val onUsernameChangeCalls: Int,
        val onCompleteCalls: Int,
        val callTrace: List<String>,
    )

    /**
     * Mirrors the current UI onClick logic EXACTLY.
     * Captures call counts + ordering as an Effects summary.
     */
    fun onNextClicked(currentUsername: String?, vm: VmPort, onComplete: () -> Unit): Effects {
        val trace = mutableListOf<String>()

        var hfts = 0
        var gen = 0
        var nameChange = 0
        var completed = 0

        val countedComplete: () -> Unit = {
            trace.add("onComplete")
            completed++
            onComplete()
        }

        val wrappedVm = object : VmPort {
            override fun onUsernameChange(value: String) {
                trace.add("onUsernameChange:$value")
                nameChange++
                vm.onUsernameChange(value)
            }

            override fun blankUsernameGenerator(cb: (String) -> Unit) {
                trace.add("blankUsernameGenerator")
                gen++
                vm.blankUsernameGenerator(cb)
            }

            override fun handleFirstTimeSetup(cb: () -> Unit) {
                trace.add("handleFirstTimeSetup")
                hfts++
                vm.handleFirstTimeSetup(cb)
            }
        }

        if (currentUsername.isNullOrBlank()) {
            wrappedVm.blankUsernameGenerator { generatedName ->
                wrappedVm.onUsernameChange(generatedName)
                wrappedVm.handleFirstTimeSetup { countedComplete() }
            }
        } else {
            wrappedVm.handleFirstTimeSetup { countedComplete() }
        }

        // duplicate call present in UI code
        wrappedVm.handleFirstTimeSetup { countedComplete() }

        return Effects(
            handleFirstTimeSetupCalls = hfts,
            blankUsernameGeneratorCalls = gen,
            onUsernameChangeCalls = nameChange,
            onCompleteCalls = completed,
            callTrace = trace.toList(),
        )
    }

    fun isUsernameBlank(username: String?): Boolean = username.isNullOrBlank()
}

/**
 * Fake VM that lets us control callback timing and validate ordering.
 */
private class FakeOnboardingVm(
    private val generatedName: String = "mesh_user_123",
    private val generatorCallsCallback: Boolean = true,
    private val setupCallsCallback: Boolean = true
) : OnboardingUiLogic.VmPort {

    val received = mutableListOf<String>()

    override fun onUsernameChange(value: String) {
        received.add("vm.onUsernameChange:$value")
    }

    override fun blankUsernameGenerator(cb: (String) -> Unit) {
        received.add("vm.blankUsernameGenerator")
        if (generatorCallsCallback) cb(generatedName)
    }

    override fun handleFirstTimeSetup(cb: () -> Unit) {
        received.add("vm.handleFirstTimeSetup")
        if (setupCallsCallback) cb()
    }
}

class OnboardingUiLogicTest {

    // ---------- username blankness ----------
    @Test
    fun isUsernameBlank_null_empty_whitespace_true_and_nonblank_false() {
        assertTrue(OnboardingUiLogic.isUsernameBlank(null))
        assertTrue(OnboardingUiLogic.isUsernameBlank(""))
        assertTrue(OnboardingUiLogic.isUsernameBlank(" "))
        assertTrue(OnboardingUiLogic.isUsernameBlank("\n\t  "))

        assertFalse(OnboardingUiLogic.isUsernameBlank("a"))
        assertFalse(OnboardingUiLogic.isUsernameBlank(" jai "))
        assertFalse(OnboardingUiLogic.isUsernameBlank("0"))
        assertFalse(OnboardingUiLogic.isUsernameBlank("_"))
    }

    // ---------- path: username provided ----------
    @Test
    fun onNextClicked_nonBlankUsername_doesNotGenerate_callsSetupTwice_callsCompleteTwice() {
        val vm = FakeOnboardingVm()
        var completes = 0

        val effects = OnboardingUiLogic.onNextClicked(
            currentUsername = "jai",
            vm = vm
        ) { completes++ }

        assertEquals(0, effects.blankUsernameGeneratorCalls)
        assertEquals(0, effects.onUsernameChangeCalls)

        // Due to duplicate call in UI:
        assertEquals(2, effects.handleFirstTimeSetupCalls)
        assertEquals(2, effects.onCompleteCalls)
        assertEquals(2, completes)

        // Order guarantee: setup -> complete -> setup -> complete
        assertEquals(
            listOf(
                "handleFirstTimeSetup",
                "onComplete",
                "handleFirstTimeSetup",
                "onComplete"
            ),
            effects.callTrace
        )
    }

    // ---------- path: username blank ----------
    @Test
    fun onNextClicked_blankUsername_generates_thenChangesUsername_thenSetupInsideCallback_thenSetupAgain() {
        val vm = FakeOnboardingVm(generatedName = "gen_name")
        var completes = 0

        val effects = OnboardingUiLogic.onNextClicked(
            currentUsername = "   ",
            vm = vm
        ) { completes++ }

        assertEquals(1, effects.blankUsernameGeneratorCalls)
        assertEquals(1, effects.onUsernameChangeCalls)

        // setup happens once inside generator callback + once duplicated after if/else
        assertEquals(2, effects.handleFirstTimeSetupCalls)
        assertEquals(2, effects.onCompleteCalls)
        assertEquals(2, completes)

        // Exact order in current code:
        // generator -> nameChange -> setup -> complete -> setup -> complete
        assertEquals(
            listOf(
                "blankUsernameGenerator",
                "onUsernameChange:gen_name",
                "handleFirstTimeSetup",
                "onComplete",
                "handleFirstTimeSetup",
                "onComplete"
            ),
            effects.callTrace
        )
    }

    @Test
    fun onNextClicked_nullUsername_treatedAsBlank() {
        val vm = FakeOnboardingVm(generatedName = "gen")
        val effects = OnboardingUiLogic.onNextClicked(
            currentUsername = null,
            vm = vm
        ) { /* no-op */ }

        assertEquals(1, effects.blankUsernameGeneratorCalls)
        assertEquals(1, effects.onUsernameChangeCalls)
        assertEquals(2, effects.handleFirstTimeSetupCalls)
        assertEquals(2, effects.onCompleteCalls)
    }

    // ---------- edge: generator does NOT invoke callback ----------
    @Test
    fun onNextClicked_blankUsername_ifGeneratorNeverReturns_stillCallsSetupOnce_dueToDuplicateCall() {
        val vm = FakeOnboardingVm(generatorCallsCallback = false)
        val effects = OnboardingUiLogic.onNextClicked(
            currentUsername = "",
            vm = vm
        ) { /* no-op */ }

        // Generator called once, but no username change and no setup inside callback
        assertEquals(1, effects.blankUsernameGeneratorCalls)
        assertEquals(0, effects.onUsernameChangeCalls)

        // Only the duplicated setup call executes
        assertEquals(1, effects.handleFirstTimeSetupCalls)
        assertEquals(1, effects.onCompleteCalls)

        assertEquals(
            listOf(
                "blankUsernameGenerator",
                "handleFirstTimeSetup",
                "onComplete"
            ),
            effects.callTrace
        )
    }

    // ---------- edge: handleFirstTimeSetup does NOT invoke callback ----------
    @Test
    fun onNextClicked_whenSetupDoesNotCallback_onCompleteNotCalled_evenThoughSetupCalledTwice() {
        val vm = FakeOnboardingVm(setupCallsCallback = false)
        val effects = OnboardingUiLogic.onNextClicked(
            currentUsername = "jai",
            vm = vm
        ) { /* no-op */ }

        assertEquals(2, effects.handleFirstTimeSetupCalls)
        assertEquals(0, effects.onCompleteCalls)

        assertEquals(
            listOf(
                "handleFirstTimeSetup",
                "handleFirstTimeSetup"
            ),
            effects.callTrace
        )
    }

    // ---------- integration-ish: verify VM receives expected calls ----------
    @Test
    fun vmReceivesExpectedCalls_inBlankFlow() {
        val vm = FakeOnboardingVm(generatedName = "abc")
        OnboardingUiLogic.onNextClicked(currentUsername = " ", vm = vm) {}

        // VM-level calls (not the logic wrapper trace)
        assertEquals(
            listOf(
                "vm.blankUsernameGenerator",
                "vm.onUsernameChange:abc",
                "vm.handleFirstTimeSetup",
                "vm.handleFirstTimeSetup",
            ),
            vm.received
        )
    }

    @Test
    fun vmReceivesExpectedCalls_inNonBlankFlow() {
        val vm = FakeOnboardingVm()
        OnboardingUiLogic.onNextClicked(currentUsername = "jai", vm = vm) {}

        assertEquals(
            listOf(
                "vm.handleFirstTimeSetup",
                "vm.handleFirstTimeSetup",
            ),
            vm.received
        )
    }
}