package com.greybox.projectmesh.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.MNetLoggerAndroid
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.arch.UserMessage
import com.greybox.projectmesh.R
import com.ustadmobile.meshrabiya.log.LogLine
import com.ustadmobile.meshrabiya.log.MNetLogger
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class LogUiState(
    val logs: List<LogLine> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedLineIds: Set<Int> = emptySet(),
    val pendingCopyText: String? = null,
    val userMessage: UserMessage? = null,
)

class LogScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle
) : StateViewModel<LogUiState>(LogUiState()) {
    private val logger: MNetLoggerAndroid = di.direct.instance<MNetLogger>() as MNetLoggerAndroid

    private var latestLogs: List<LogLine> = emptyList()
    init {
        viewModelScope.launch {
            logger.recentLogs.collect { updatedLogs ->

                // Always remember the newest logs
                latestLogs = updatedLogs

                // Only update the displayed logs outside selection mode
                if (!currentState.isSelectionMode) {
                    setState {
                        copy(logs = updatedLogs)
                    }
                }
            }
        }
    }

    // start selection mode using the Select button
    fun startSelection() {
        setState {
            if (logs.isEmpty()) this
            else copy(isSelectionMode = true)
        }
    }

    // start selecting when a user long presses a log
    fun onLogLongPressed(lineId: Int){
        setState {
            if (!isSelectionMode) {
                copy(
                    isSelectionMode = true,
                    selectedLineIds = setOf(lineId)
                )
            } else {
                this
            }
        }
    }

    // select or deselect one log
    fun toggleLogSelection(lineId: Int){
        setState {
            if (!isSelectionMode){
                this
            } else {
                copy(
                    selectedLineIds = if (lineId in selectedLineIds) {
                        selectedLineIds - lineId
                    } else {
                        selectedLineIds + lineId
                    }
                )
            }
        }
    }

    // select every displayed log
    fun selectAllLogs(){
        setState {
            if (isSelectionMode) {
                copy(selectedLineIds = logs.map { it.lineId }.toSet())
            } else {
                this
            }
        }
    }

    // prepares selected logs to be copied
    fun copySelectedLogs() {
        val state = currentState
        
        if (!currentState.isSelectionMode) return

        val formatter = SimpleDateFormat(
            "HH:mm:ss.SS",
            Locale.getDefault()
        )

        val selectedLogs = currentState.logs
            .filter { it.lineId in currentState.selectedLineIds }
            .joinToString("\n") { log ->
                "[${formatter.format(Date(log.time))}] ${log.line}"
            }
        setState {
            copy(
                logs = latestLogs,
                pendingCopyText = selectedLogs,
                isSelectionMode = false,
                selectedLineIds = emptySet()
            )
        }
    }

    // clear the copy request and shows a success message
    fun onCopyCompleted() {
        setState {
            if (pendingCopyText == null) {
                this
            } else {
                copy(
                    pendingCopyText = null,
                    userMessage = UserMessage(R.string.logs_copied)
                )
            }
        }
    }

    // leave selection mode and clear the selected logs
    fun cancelSelection() {
        setState {
            copy(
                logs = latestLogs,
                isSelectionMode = false,
                selectedLineIds = emptySet()
            )
        }
    }

    // clear a temporary message after the UI displays it
    fun onUserMessageShown() {
        setState { copy(userMessage = null)}
    }

}