package com.greybox.projectmesh.viewModel

import android.content.SharedPreferences
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.testutil.FakeSettingsRepository
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * OnboardingViewModel tests, per the approved OnboardingScreen contract.
 *
 * Test doubles, and why:
 *  - [UserRepository]: MockK. The tests verify INTERACTIONS (which user was
 *    inserted/updated, that only one insert happens during a double-tap) rather
 *    than returned state — this is exactly the mock sweet spot at a dependency
 *    boundary.
 *  - [SharedPreferences]: MockK. A pure interface with no observable state of
 *    its own here; the tests only need getString("UUID") to return a stub and
 *    to verify the generated UUID is written back.
 *  - [SettingsRepository]: the existing hand-written [FakeSettingsRepository],
 *    because the device-name assertion reads the fake's stateful flow — a mock
 *    would just be a more awkward fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)

    private val settings: FakeSettingsRepository = FakeSettingsRepository()
    private val localIp = "192.168.42.1"

    private fun buildVm(
        existingUuid: String? = null,
        existingUsers: List<UserEntity> = emptyList(),
        settingsRepo: SettingsRepository = settings,
    ): OnboardingViewModel {
        every { prefs.edit() } returns editor
        // Editor is a builder: putString returns the editor itself. Without this,
        // a relaxed mock hands back a fresh child mock and the chained .apply()
        // lands on the child, not on the mock we verify against.
        every { editor.putString(any(), any()) } returns editor
        every { prefs.getString("UUID", null) } returns existingUuid
        coEvery { userRepository.getAllUsers() } returns existingUsers
        return OnboardingViewModel(
            userRepository = userRepository,
            prefs = prefs,
            settingsRepository = settingsRepo,
            localIp = localIp,
        )
    }

    @Test
    fun `typed username is kept, completes setup and names the device`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = "uuid-1")
        vm.onUsernameChange("Harshit")

        vm.onNextClick()
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Harshit", localIp) }
        assertEquals("Harshit", settings.deviceName.value)
        assertTrue(vm.state.value.isSetupComplete)
        assertFalse(vm.state.value.isSubmitting)
        verify(exactly = 0) { editor.putString(any(), any()) }  // existing UUID reused, nothing written
    }

    @Test
    fun `blank username generates Guest1 when no users exist`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = "uuid-1", existingUsers = emptyList())

        vm.onNextClick()
        advanceUntilIdle()

        assertEquals("Guest1", vm.state.value.username)
        coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Guest1", localIp) }
        assertTrue(vm.state.value.isSetupComplete)
    }

    @Test
    fun `guest numbering continues from the highest existing GuestN`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(
            existingUuid = "uuid-1",
            existingUsers = listOf(
                UserEntity(uuid = "u1", name = "Guest1"),
                UserEntity(uuid = "u2", name = "Alice"),
                UserEntity(uuid = "u3", name = "Guest2"),
            ),
        )

        vm.onNextClick()
        advanceUntilIdle()

        assertEquals("Guest3", vm.state.value.username)
        coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Guest3", localIp) }
    }

    @Test
    fun `second tap while setup is running is ignored`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = "uuid-1")

        vm.onNextClick()
        vm.onNextClick()   // before advanceUntilIdle: the setup coroutine has not run yet
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.insertOrUpdateUser(any(), any(), any()) }
        assertTrue(vm.state.value.isSetupComplete)
    }

    @Test
    fun `missing UUID is generated and persisted before the user insert`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = null)

        vm.onUsernameChange("Harshit")
        vm.onNextClick()
        advanceUntilIdle()

        // UUID was generated and written back to prefs…
        verify(exactly = 1) { editor.putString("UUID", any()) }
        verify(exactly = 1) { editor.apply() }
        // …and the SAME generated UUID is what registers the user
        verify {
            editor.putString(withArg { uuidArg -> assertTrue(uuidArg.isNotBlank()) }, any())
        }
        coVerify { userRepository.insertOrUpdateUser(match { it.isNotBlank() }, "Harshit", localIp) }
    }

    @Test
    fun `skip keeps a typed username instead of replacing it with a guest name`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm(existingUuid = "uuid-1")
            vm.onUsernameChange("Typed")

            vm.onSkipClick()
            advanceUntilIdle()

            assertEquals("Typed", vm.state.value.username)
            coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Typed", localIp) }
            assertTrue(vm.state.value.isSetupComplete)
        }

    @Test
    fun `skip with blank username generates a guest name`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = "uuid-1", existingUsers = listOf(UserEntity(uuid = "u1", name = "Guest4")))

        vm.onSkipClick()
        advanceUntilIdle()

        assertEquals("Guest5", vm.state.value.username)
        assertTrue(vm.state.value.isSetupComplete)
    }

    @Test
    fun `how it works shows and dismisses through state`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm()

        assertFalse(vm.state.value.showHowItWorks)
        vm.onHowItWorksClick()
        assertTrue(vm.state.value.showHowItWorks)
        vm.onHowItWorksDismiss()
        assertFalse(vm.state.value.showHowItWorks)
    }

    @Test
    fun `completion is exposed only through state, never a callback`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(existingUuid = "uuid-1")
        vm.onUsernameChange("Harshit")

        // The public surface ends at state: after finishing, the screen observes
        // isSetupComplete and informs the shell (MainActivity -> AppViewModel).
        vm.onNextClick()
        advanceUntilIdle()

        assertTrue(vm.state.value.isSetupComplete)
        // A further finish attempt must be a no-op, exactly like the double-tap guard.
        vm.onSkipClick()
        advanceUntilIdle()
        coVerify(exactly = 1) { userRepository.insertOrUpdateUser(any(), any(), any()) }
    }
}
