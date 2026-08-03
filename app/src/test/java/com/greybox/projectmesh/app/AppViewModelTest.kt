package com.greybox.projectmesh.app

import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.navigation.BottomNavItem
import com.greybox.projectmesh.testutil.FakeSettingsRepository
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.ui.theme.AppTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildVm(
        fake: FakeSettingsRepository = FakeSettingsRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): AppViewModel {
        val di = DI { bind<SettingsRepository>() with singleton { fake } }
        return AppViewModel(di, savedStateHandle)
    }

@Test
fun `state reflects repository values immediately, without advancing the dispatcher`() {
    val vm = buildVm(
        FakeSettingsRepository(
            initialTheme = AppTheme.DARK,
            initialLanguage = "fr",
            initialDeviceName = "Kara's phone",
            initialHasRunBefore = true,
        )
    )
    val s = vm.state.value   // read BEFORE the mirror coroutine can possibly run
    assertEquals(AppTheme.DARK, s.theme)
    assertEquals("fr", s.languageCode)
    assertEquals("Kara's phone", s.deviceName)
    assertTrue(s.hasRunBefore)
}

@Test
fun `hostile conversation id falls back to Home and never reaches saved state`() {
    val handle = SavedStateHandle()
    val vm = buildVm(savedStateHandle = handle)
    vm.applyDeepLink(DeepLink.OpenConversation("a/b"))
    assertEquals(BottomNavItem.Home.route, vm.state.value.startDestination)
    assertEquals(BottomNavItem.Home.route, handle.get<String>("startDestination"))
    assertEquals(true, handle.get<Boolean>("deepLinkHandled"))  // consumed even though invalid
}
@Test
fun `valid ip deep link opens live chat route`() {
    val vm = buildVm()
    vm.applyDeepLink(DeepLink.OpenChat("192.168.0.99"))
    assertEquals("chatScreen/192.168.0.99", vm.state.value.startDestination)
}

@Test
fun `invalid ip deep link falls back to Home`() {
    val vm = buildVm()
    vm.applyDeepLink(DeepLink.OpenChat("not-an-ip"))
    assertEquals(BottomNavItem.Home.route, vm.state.value.startDestination)
}

@Test
fun `valid conversation id opens stored chat route`() {
    val vm = buildVm()
    vm.applyDeepLink(DeepLink.OpenConversation("local-user-offline-test-device-uuid"))
    assertEquals(
        "chatScreen/local-user-offline-test-device-uuid",
        vm.state.value.startDestination,
    )
}

@Test
fun `deep link is consumed once - a later link is ignored`() {
    val vm = buildVm()
    vm.applyDeepLink(DeepLink.OpenChat("192.168.0.99"))
    vm.applyDeepLink(DeepLink.OpenReceive)
    assertEquals("chatScreen/192.168.0.99", vm.state.value.startDestination)
}

@Test
fun `deep link already handled before recreate is ignored`() {
    val handle = SavedStateHandle(mapOf("deepLinkHandled" to true))
    val vm = buildVm(savedStateHandle = handle)
    val before = vm.state.value.startDestination
    vm.applyDeepLink(DeepLink.OpenChat("192.168.0.99"))
    assertEquals(before, vm.state.value.startDestination)
}

@Test
fun `null deep link keeps current destination but still consumes`() {
    val handle = SavedStateHandle()
    val vm = buildVm(savedStateHandle = handle)
    val before = vm.state.value.startDestination
    vm.applyDeepLink(null)
    assertEquals(before, vm.state.value.startDestination)
    assertEquals(true, handle.get<Boolean>("deepLinkHandled"))
}

@Test
fun `completeOnboarding sets hasRunBefore through the repository`() =
    runTest(mainDispatcherRule.dispatcher) {
        val fake = FakeSettingsRepository(initialHasRunBefore = false)
        val vm = buildVm(fake)
        vm.completeOnboarding()
        assertTrue(fake.hasRunBefore.value)   // repo write is synchronous
        advanceUntilIdle()                    // drain the mirror's launchIn
        assertTrue(vm.state.value.hasRunBefore)
    }



}
