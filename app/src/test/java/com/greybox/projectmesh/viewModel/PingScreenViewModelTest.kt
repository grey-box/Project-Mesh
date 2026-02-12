package com.greybox.projectmesh.viewModel

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.user.UserEntity
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.LocalNodeState
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import java.net.InetAddress

class PingScreenViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun new_message_added_duplicate_ignored() = runBlocking {
        val virtualAddress = InetAddress.getByName("192.168.0.42")
        val addrKey = virtualAddress.requireAddressAsInt()

        val userRepo = mockk<UserRepository>()
        val userEntity = mockk<UserEntity>()
        every { userEntity.name } returns "Device42"
        coEvery { userRepo.getUserByIp(virtualAddress.hostAddress) } returns userEntity
        GlobalApp.GlobalUserRepo.userRepository = userRepo

        val initialNodeState = mockk<LocalNodeState>(relaxed = true)
        val stateFlow: MutableStateFlow<LocalNodeState> = MutableStateFlow(initialNodeState)

        val node = mockk<AndroidVirtualNode>()
        every { node.state } returns stateFlow

        val appServer = mockk<AppServer>(relaxed = true)

        val di = DI {
            bind<AndroidVirtualNode>() with singleton { node }
            bind<AppServer>() with singleton { appServer }
        }

        val viewModel = PingScreenViewModel(
            di = di,
            savedStateHandle = SavedStateHandle(),
            virtualAddress = virtualAddress
        )

        val msg1 = mockk<VirtualNode.LastOriginatorMessage>()
        every { msg1.timeReceived } returns 100L

        val msgDup = mockk<VirtualNode.LastOriginatorMessage>()
        every { msgDup.timeReceived } returns 100L

        val stateWithMsg1 = mockk<LocalNodeState>()
        every { stateWithMsg1.originatorMessages } returns mapOf(addrKey to msg1)

        val stateWithDup = mockk<LocalNodeState>()
        every { stateWithDup.originatorMessages } returns mapOf(addrKey to msgDup)

        stateFlow.value = stateWithMsg1
        mainDispatcher.scheduler.advanceUntilIdle()
        val afterFirst = viewModel.uiState.first { it.allOriginatorMessages.size == 1 }

        stateFlow.value = stateWithDup
        mainDispatcher.scheduler.advanceUntilIdle()
        val afterDup = viewModel.uiState.first { it.allOriginatorMessages.size == 1 }

        assertEquals(1, afterFirst.allOriginatorMessages.size)
        assertEquals(1, afterDup.allOriginatorMessages.size)
    }
}
