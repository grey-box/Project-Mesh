package com.greybox.projectmesh.viewModel

import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.testutil.FakeSettingsRepository
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.InetAddress

/**
 * Test doubles, and why:
 *  - [UserRepository]: MockK. The tests verify INTERACTIONS (which user was
 *    inserted/updated, that only one insert happens during a double-tap) rather
 *    than returned state — the mock sweet spot at a dependency boundary.
 *  - [FakeSettingsRepository]: the existing hand-written fake, because the
 *    device-name assertion reads the fake's stateful flow — a mock would just
 *    be a more awkward fake. It also supplies the local UUID the way
 *    GlobalApp.onCreate() does in production, before any screen can run.
 *  - [AndroidVirtualNode]: MockK stub of `address`, so the ViewModel's DI-based
 *    local-IP lookup resolves to a known address without a real virtual node.
 *
 * The ViewModel receives all of these through a real Kodein [DI] container
 * (mirroring the production bindings), so construction paths are exercised
 * the same way the app exercises them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk(relaxed = true)
    private val virtualNode: AndroidVirtualNode = mockk()

    private val settings: FakeSettingsRepository = FakeSettingsRepository()
    private val localIp = "192.168.42.1"
    private val localIpAddress: InetAddress =
        InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 42.toByte(), 1.toByte()))

    private fun buildVm(
        localUuid: String? = "uuid-1",
        existingUsers: List<UserEntity> = emptyList(),
    ): OnboardingViewModel {
        settings.localUuid = localUuid
        coEvery { userRepository.getAllUsers() } returns existingUsers
        every { virtualNode.address } returns localIpAddress

        val di = DI {
            bind<UserRepository>() with singleton { userRepository }
            bind<SettingsRepository>() with singleton { settings }
            bind<AndroidVirtualNode>() with singleton { virtualNode }
        }
        return OnboardingViewModel(di)
    }

    @Test
    fun `initial state is the Editing phase`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")

        assertEquals(OnboardingPhase.Editing, vm.state.value.phase)
        assertFalse(vm.state.value.showHowItWorks)
    }

    @Test
    fun `typed username is kept, completes setup and names the device`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")
        vm.onUsernameChange("Harshit")

        vm.onNextClick()
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Harshit", localIp) }
        assertEquals("Harshit", settings.deviceName.value)
        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
    }

    @Test
    fun `blank username generates Guest1 when no users exist`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1", existingUsers = emptyList())

        vm.onNextClick()
        advanceUntilIdle()

        assertEquals("Guest1", vm.state.value.username)
        coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Guest1", localIp) }
        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
    }

    @Test
    fun `guest numbering continues from the highest existing GuestN`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(
            localUuid = "uuid-1",
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
    fun `phase is Submitting between the finish tap and the setup writes`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")

        vm.onNextClick()
        // No advanceUntilIdle yet: the synchronous guard must already have moved
        // the state to Submitting, which is what disables the screen's buttons.
        assertEquals(OnboardingPhase.Submitting, vm.state.value.phase)

        advanceUntilIdle()
        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
    }

    @Test
    fun `second tap while setup is running is ignored`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")

        vm.onNextClick()
        vm.onNextClick()   // before advanceUntilIdle: the setup coroutine has not run yet
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.insertOrUpdateUser(any(), any(), any()) }
        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
    }

    @Test
    fun `missing local UUID registers nobody and returns to Editing`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = null)

        vm.onNextClick()
        advanceUntilIdle()

        // GlobalApp.onCreate() guarantees a UUID before any activity, so this
        // path is unreachable in production; if it ever trips anyway, onboarding
        // must not register a user against a missing UUID or strand the flow.
        coVerify(exactly = 0) { userRepository.insertOrUpdateUser(any(), any(), any()) }
        assertEquals("", settings.deviceName.value)
        assertEquals(OnboardingPhase.Editing, vm.state.value.phase)
        assertFalse(vm.state.value.showHowItWorks)
    }

    @Test
    fun `skip keeps a typed username instead of replacing it with a guest name`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm(localUuid = "uuid-1")
            vm.onUsernameChange("Typed")

            vm.onSkipClick()
            advanceUntilIdle()

            assertEquals("Typed", vm.state.value.username)
            coVerify(exactly = 1) { userRepository.insertOrUpdateUser("uuid-1", "Typed", localIp) }
            assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
        }

    @Test
    fun `skip with blank username generates a guest name`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1", existingUsers = listOf(UserEntity(uuid = "u1", name = "Guest4")))

        vm.onSkipClick()
        advanceUntilIdle()

        assertEquals("Guest5", vm.state.value.username)
        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
    }

    @Test
    fun `how it works shows and dismisses through state`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")

        assertFalse(vm.state.value.showHowItWorks)
        vm.onHowItWorksClick()
        assertTrue(vm.state.value.showHowItWorks)
        vm.onHowItWorksDismiss()
        assertFalse(vm.state.value.showHowItWorks)
    }

    @Test
    fun `completion is exposed only through state, never a callback`() = runTest(mainDispatcherRule.dispatcher) {
        val vm = buildVm(localUuid = "uuid-1")
        vm.onUsernameChange("Harshit")

        // The public surface ends at state: after finishing, the screen observes
        // the Complete phase and informs the shell (MainActivity -> AppViewModel).
        vm.onNextClick()
        advanceUntilIdle()

        assertEquals(OnboardingPhase.Complete, vm.state.value.phase)
        // A further finish attempt must be a no-op, exactly like the double-tap guard.
        vm.onSkipClick()
        advanceUntilIdle()
        coVerify(exactly = 1) { userRepository.insertOrUpdateUser(any(), any(), any()) }
    }
}
