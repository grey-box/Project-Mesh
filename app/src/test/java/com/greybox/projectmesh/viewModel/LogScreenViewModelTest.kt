package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import com.greybox.projectmesh.MNetLoggerAndroid
import com.greybox.projectmesh.testutil.MainDispatcherRule
import com.ustadmobile.meshrabiya.log.LogLine
import com.ustadmobile.meshrabiya.log.MNetLogger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.advanceUntilIdle
import com.greybox.projectmesh.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LogScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // this creates a ViewModel using a fake logger
    private fun buildVm(
        logs: MutableStateFlow<List<LogLine>> =
            MutableStateFlow(emptyList())
    ): LogScreenViewModel {

        val fakeLogger = mockk<MNetLoggerAndroid>()
        every { fakeLogger.recentLogs } returns logs
        val di = DI { bind<MNetLogger>() with singleton { fakeLogger } }
        return LogScreenViewModel(di, SavedStateHandle())
    }

    @Test
    fun `long press starts selection mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            vm.onLogLongPressed(5)
            assertTrue(vm.state.value.isSelectionMode)
            assertEquals(
                setOf(5),
                vm.state.value.selectedLineIds
            )
        }

    @Test
    fun `toggle selection adds and removes a log`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            // start selection mode with log 5
            vm.onLogLongPressed(5)
            // select another log
            vm.toggleLogSelection(8)
            assertEquals(
                setOf(5, 8),
                vm.state.value.selectedLineIds
            )
            // deselect log 5
            vm.toggleLogSelection(5)
            assertEquals(
                setOf(8),
                vm.state.value.selectedLineIds
            )
        }

    @Test
    fun `cancel clears selected logs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            // start selecting log 5
            vm.onLogLongPressed(5)
            // cancel selection
            vm.cancelSelection()
            assertFalse(vm.state.value.isSelectionMode)
            assertTrue(vm.state.value.selectedLineIds.isEmpty())
        }

    @Test
    fun `start selection enters selection mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val log = mockk<LogLine>()
            val logs = MutableStateFlow(listOf(log))
            val vm = buildVm(logs)
            // allow the ViewModel to collect the logs
            advanceUntilIdle()
            vm.startSelection()
            assertTrue(vm.state.value.isSelectionMode)
            // no log should be selected yet
            assertTrue(vm.state.value.selectedLineIds.isEmpty())
        }

    @Test
    fun `select all selects every log`() =
        runTest(mainDispatcherRule.dispatcher) {
            val log1 = mockk<LogLine>()
            val log2 = mockk<LogLine>()
            every { log1.lineId } returns 5
            every { log2.lineId } returns 8
            val logs = MutableStateFlow(listOf(log1, log2))
            val vm = buildVm(logs)
            advanceUntilIdle()
            // enter selection mode
            vm.startSelection()
            // select every log
            vm.selectAllLogs()
            assertEquals(
                setOf(5, 8),
                vm.state.value.selectedLineIds
            )
        }

    @Test
    fun `copy prepares only selected logs`() =
        runTest(mainDispatcherRule.dispatcher) {
            val log1 = mockk<LogLine>()
            val log2 = mockk<LogLine>()
            every { log1.lineId } returns 5
            every { log1.time } returns 0L
            every { log1.line } returns "First log"
            every { log2.lineId } returns 8
            every { log2.time } returns 0L
            every { log2.line } returns "Second log"
            val logs = MutableStateFlow(listOf(log1, log2))
            val vm = buildVm(logs)
            advanceUntilIdle()
            // select only log 5
            vm.onLogLongPressed(5)
            // prepare the selected log for copying
            vm.copySelectedLogs()
            val copiedText = vm.state.value.pendingCopyText
            // only the selected log should be included
            assertTrue(copiedText?.contains("First log") == true)
            assertFalse(copiedText?.contains("Second log") == true)
            // selection mode should be cleared
            assertFalse(vm.state.value.isSelectionMode)
            assertTrue(vm.state.value.selectedLineIds.isEmpty())
        }

    @Test
    fun `copy completed clears request and creates message`() =
        runTest(mainDispatcherRule.dispatcher) {
            val log = mockk<LogLine>()
            every { log.lineId } returns 5
            every { log.time } returns 0L
            every { log.line } returns "Test log"
            val logs = MutableStateFlow(listOf(log))
            val vm = buildVm(logs)
            advanceUntilIdle()
            vm.onLogLongPressed(5)
            vm.copySelectedLogs()
            // there should be text waiting to be copied
            assertNotNull(vm.state.value.pendingCopyText)
            // simulate the UI finishing the clipboard operation
            vm.onCopyCompleted()
            // the pending copy request should be cleared
            assertNull(vm.state.value.pendingCopyText)
            // a success message should now exist
            assertEquals(
                R.string.logs_copied,
                vm.state.value.userMessage?.textRes
            )
        }

    @Test
    fun `user message is cleared after being shown`() =
        runTest(mainDispatcherRule.dispatcher) {
            val log = mockk<LogLine>()
            every { log.lineId } returns 5
            every { log.time } returns 0L
            every { log.line } returns "Test log"
            val logs = MutableStateFlow(listOf(log))
            val vm = buildVm(logs)
            advanceUntilIdle()
            vm.onLogLongPressed(5)
            vm.copySelectedLogs()
            vm.onCopyCompleted()
            // verify the message exists
            assertNotNull(vm.state.value.userMessage)
            // simulate the UI displaying the message
            vm.onUserMessageShown()
            // the message should now be cleared
            assertNull(vm.state.value.userMessage)
        }

    @Test
    fun `ViewModel receives log updates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val logs = MutableStateFlow<List<LogLine>>(emptyList())
            val vm = buildVm(logs)
            val log = mockk<LogLine>()
            // simulate the logger receiving a new log
            logs.value = listOf(log)
            // allow the ViewModel to process the update
            advanceUntilIdle()
            // check that the ViewModel received the log
            assertEquals(
                listOf(log),
                vm.state.value.logs
            )
        }

    //edge cases
    @Test
    fun `start selection does nothing when logs are empty`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            vm.startSelection()
            assertFalse(vm.state.value.isSelectionMode)
            assertTrue(vm.state.value.selectedLineIds.isEmpty())
        }

    @Test
    fun `toggle does nothing outside selection mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            vm.toggleLogSelection(5)
            assertFalse(vm.state.value.isSelectionMode)
            assertTrue(vm.state.value.selectedLineIds.isEmpty())
        }

    @Test
    fun `copy completed does nothing without pending text`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = buildVm()
            vm.onCopyCompleted()
            assertNull(vm.state.value.pendingCopyText)
            assertNull(vm.state.value.userMessage)
        }
}
