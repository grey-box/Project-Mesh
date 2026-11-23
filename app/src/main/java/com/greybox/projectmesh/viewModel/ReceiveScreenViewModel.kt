package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.server.AppServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.io.File
import androidx.compose.runtime.Immutable
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.arch.UserMessage

@Immutable
data class ReceiveUiState(
    val incomingTransfers: List<AppServer.IncomingTransferInfo> = emptyList(),
    val userMessage: UserMessage? = null,
)

class ReceiveScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle
) : StateViewModel<ReceiveUiState>(ReceiveUiState()) {
    private val appServer: AppServer by di.instance()
    private val receiveDir: File by di.instance(tag = GlobalApp.TAG_RECEIVE_DIR)
    init {
        viewModelScope.launch {
            appServer.incomingTransfers.collect { transfers ->
                setState {
                    copy(incomingTransfers = transfers)
                }
            }
        }
    }

    /*
    If user accept the file, then the file will be downloaded to specific directory
    1. Make sure the destination directory exist, if not, then create one.
    2. Process the file transfer by using "acceptIncomingTransfer" function from the Server side
     */
    fun onAccept(transfer: AppServer.IncomingTransferInfo){
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                if (!receiveDir.exists()){
                    receiveDir.mkdirs()
                }
                val file = File(receiveDir, transfer.name)
                appServer.acceptIncomingTransfer(transfer, file)
            }
        }
    }

    /*
    If user decline the file, then call "onDeclineIncomingTransfer" function from the Server side
     */
    fun onDecline(transfer: AppServer.IncomingTransferInfo){
        viewModelScope.launch{
            appServer.onDeclineIncomingTransfer(transfer)
        }
    }

    /*
    If user delete the file, then call "onDeleteIncomingTransfer" function from the Server side
     */
    fun onDelete(transfer: AppServer.IncomingTransferInfo){
        viewModelScope.launch {
            appServer.onDeleteIncomingTransfer(transfer)
        }
    }

    fun onUserMessageShown() {
        setState {
            copy(userMessage = null)
        }
    }
}