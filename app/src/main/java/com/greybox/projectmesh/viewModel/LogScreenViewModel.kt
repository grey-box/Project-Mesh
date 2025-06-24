package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.MNetLoggerAndroid
import com.ustadmobile.meshrabiya.log.LogLine
import com.ustadmobile.meshrabiya.log.MNetLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.direct
import org.kodein.di.instance

data class LogScreenModel(
    val logs: List<LogLine> = emptyList()
)

class LogScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(LogScreenModel())
    val uiState: Flow<LogScreenModel> = _uiState.asStateFlow()
    private val logger: MNetLoggerAndroid = di.direct.instance<MNetLogger>() as MNetLoggerAndroid

    init {
        viewModelScope.launch {
            logger.recentLogs.collect {
                _uiState.update { prev ->
                    prev.copy(
                        logs = it
                    )
                }
            }
        }
    }
}