package com.greybox.projectmesh.viewModel

import android.content.SharedPreferences
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var repo: UserRepository
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        repo = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun onUsernameChange_updatesUiState() = runTest {
        val vm = OnboardingViewModel(repo, prefs, "10.0.0.1")
        vm.onUsernameChange("Jai")
        advanceUntilIdle()
        assertEquals("Jai", vm.uiState.value.username)
    }

    @Test
    fun handleFirstTimeSetup_whenUuidMissing_generatesUuid_savesPrefs_insertsUser_andCallsOnComplete() = runTest {
        every { prefs.getString("UUID", null) } returns null

        val vm = OnboardingViewModel(repo, prefs, "10.0.0.1")
        vm.onUsernameChange("Jai")

        var completed = false
        vm.handleFirstTimeSetup { completed = true }
        advanceUntilIdle()

        assertTrue(completed)

        // UUID should be generated + stored (we don't care what exact value is)
        verify { editor.putString("UUID", match { it.isNotBlank() }) }

        // Repository called with same UUID value that was stored
        val storedUuid = slot<String>()
        verify { editor.putString("UUID", capture(storedUuid)) }

        coVerify {
            repo.insertOrUpdateUser(
                uuid = storedUuid.captured,
                name = "Jai",
                address = "10.0.0.1"
            )
        }

        verify {
            editor.putString("device_name", "Jai")
            editor.putBoolean("hasRunBefore", true)
            editor.apply()
        }
    }

    @Test
    fun handleFirstTimeSetup_whenUuidExists_usesExistingUuid_andDoesNotOverwriteUuid() = runTest {
        val existingUuid = "22222222-2222-2222-2222-222222222222"
        every { prefs.getString("UUID", null) } returns existingUuid

        val vm = OnboardingViewModel(repo, prefs, "10.0.0.9")
        vm.onUsernameChange("Alice")

        var completed = false
        vm.handleFirstTimeSetup { completed = true }
        advanceUntilIdle()

        assertTrue(completed)

        // Should NOT rewrite UUID when already present
        verify(exactly = 0) { editor.putString("UUID", any()) }

        coVerify {
            repo.insertOrUpdateUser(
                uuid = existingUuid,
                name = "Alice",
                address = "10.0.0.9"
            )
        }

        verify {
            editor.putString("device_name", "Alice")
            editor.putBoolean("hasRunBefore", true)
            editor.apply()
        }
    }

    @Test
    fun blankUsernameGenerator_picksNextGuestNumber() = runTest {
        val u1 = mockk<UserEntity>(relaxed = true).also { every { it.name } returns "Guest1" }
        val u2 = mockk<UserEntity>(relaxed = true).also { every { it.name } returns "Guest2" }
        val u3 = mockk<UserEntity>(relaxed = true).also { every { it.name } returns "Bob" }
        val u4 = mockk<UserEntity>(relaxed = true).also { every { it.name } returns "Guest10" }

        coEvery { repo.getAllUsers() } returns listOf(u1, u2, u3, u4)

        val vm = OnboardingViewModel(repo, prefs, "10.0.0.1")

        var result: String? = null
        vm.blankUsernameGenerator { result = it }
        advanceUntilIdle()

        assertEquals("Guest11", result)
    }
}
