package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.mmcp.MmcpOriginatorMessage
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

class PingScreenViewModelTest {

    //Lets the ViewModel run in a test
    @get:Rule
    val rule = MainDispatcherRule()

    val myIp = InetAddress.getByName("10.0.0.5")

    //Fake phone state with no pings yet
    val emptyPhone = mockk<LocalNodeState>()
    val phoneUpdates = MutableStateFlow(emptyPhone)

    //Makes the ViewModel using fakes
    fun makeViewModel(name: String?): PingScreenViewModel {
        every { emptyPhone.originatorMessages } returns emptyMap()

        val fakeDatabase = mockk<UserRepository>()
        if (name == null) {
            coEvery { fakeDatabase.getUserByIp(any()) } returns null
        } else {
            coEvery { fakeDatabase.getUserByIp(any()) } returns UserEntity(uuid = "1", name = name)
        }

        val fakePhone = mockk<AndroidVirtualNode>()
        every { fakePhone.state } returns phoneUpdates

        val di = DI {
            bind<UserRepository>() with singleton { fakeDatabase }
            bind<AndroidVirtualNode>() with singleton { fakePhone }
        }
        return PingScreenViewModel(di, SavedStateHandle(), myIp, rule.dispatcher)
    }

    @Test
    fun screenStartsEmpty() {
        val viewModel = makeViewModel(null)
        assertEquals(PingUiState(), viewModel.state.value)
    }

    @Test
    fun showsDeviceName() = runTest(rule.dispatcher) {
        val viewModel = makeViewModel("Damien")
        advanceUntilIdle()
        assertEquals("Damien", viewModel.state.value.deviceName)
    }

    @Test
    fun showsUnknownWhenNoName() = runTest(rule.dispatcher) {
        val viewModel = makeViewModel(null)
        advanceUntilIdle()
        assertEquals("Unknown", viewModel.state.value.deviceName)
    }

    @Test
    fun showsOnePing() = runTest(rule.dispatcher) {
        val viewModel = makeViewModel(null)

        // Make a fake ping message
        val message = mockk<MmcpOriginatorMessage>()
        every { message.messageId } returns 7
        every { message.pingTimeSum } returns 42

        val ping = mockk<VirtualNode.LastOriginatorMessage>()
        every { ping.originatorMessage } returns message
        every { ping.hopCount } returns 2
        every { ping.lastHopAddr } returns InetAddress.getByName("10.0.0.1").requireAddressAsInt()
        every { ping.timeReceived } returns 100L

        // Pretend the phone received that ping
        val phoneWithPing = mockk<LocalNodeState>()
        every { phoneWithPing.originatorMessages } returns mapOf(myIp.requireAddressAsInt() to ping)
        phoneUpdates.value = phoneWithPing
        advanceUntilIdle()

        val expected = PingResult(messageId = 7, pingTimeMs = 42, hopCount = 2, lastHopAddress = "10.0.0.1")
        assertEquals(listOf(expected), viewModel.state.value.pingResults)
    }
}