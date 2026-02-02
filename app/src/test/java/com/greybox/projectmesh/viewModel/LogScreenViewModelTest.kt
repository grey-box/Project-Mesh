package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.greybox.projectmesh.MNetLoggerAndroid
import com.ustadmobile.meshrabiya.log.LogLine
import com.ustadmobile.meshrabiya.log.MNetLogger
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.greybox.projectmesh.testutil.MainDispatcherRule


@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LogScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var di: DI
    private lateinit var logger: MNetLoggerAndroid

    // We'll drive emissions into the VM via this flow.
    private lateinit var recentLogsFlow: MutableSharedFlow<List<LogLine>>

    @Before
    fun setUp() {
        recentLogsFlow = MutableSharedFlow(replay = 1)

        // Must be an actual MNetLoggerAndroid at runtime because VM does:
        // di.direct.instance<MNetLogger>() as MNetLoggerAndroid
        logger = mockk(relaxed = true)

        every { logger.recentLogs } returns recentLogsFlow

        di = DI {
            // Bind under MNetLogger (what the VM requests) but return the SAME object
            // whose runtime type is MNetLoggerAndroid, so the cast succeeds.
            bind<MNetLogger>() with singleton { logger as MNetLogger }
        }
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun uiState_startsWithEmptyLogs() = runTest {
        val vm = LogScreenViewModel(di, SavedStateHandle())

        vm.uiState.test {
            val first = awaitItem()
            assertEquals(emptyList<LogLine>(), first.logs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_updatesWhenRecentLogsEmits() = runTest {
        val vm = LogScreenViewModel(di, SavedStateHandle())

        val l1 = mockk<LogLine>(relaxed = true)
        val l2 = mockk<LogLine>(relaxed = true)
        val payload = listOf(l1, l2)

        vm.uiState.test {
            // initial
            val first = awaitItem()
            assertEquals(0, first.logs.size)

            // emit new logs
            recentLogsFlow.emit(payload)
            advanceUntilIdle()

            val second = awaitItem()
            assertEquals(payload, second.logs)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

