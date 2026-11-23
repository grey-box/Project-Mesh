package com.greybox.projectmesh.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.R
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.arch.UserMessage
import com.greybox.projectmesh.data.FileSaveResult
import com.greybox.projectmesh.data.ReceivedFileSaver
import com.greybox.projectmesh.data.SettingsRepository
import com.greybox.projectmesh.server.AppServer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.io.File

@Immutable
data class ReceiveUiState(
    val incomingTransfers: List<AppServer.IncomingTransferInfo> = emptyList(),
    val userMessage: UserMessage? = null,
)

class ReceiveScreenViewModel(
    di: DI,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateViewModel<ReceiveUiState>(ReceiveUiState()) {
    private val appServer: AppServer by di.instance()
    private val receiveDir: File by di.instance(tag = GlobalApp.TAG_RECEIVE_DIR)
    private val settings: SettingsRepository by di.instance()
    private val fileSaver: ReceivedFileSaver by di.instance()

    // Receiver-side IDs can be reused by different senders.
    private val acceptingTransfers = mutableSetOf<Triple<String?, Int, Long>>()

    init {
        setState { withTransfers(appServer.incomingTransfers.value) }
        viewModelScope.launch {
            appServer.incomingTransfers.collect { transfers ->
                setState { withTransfers(transfers) }
            }
        }
    }

    private fun ReceiveUiState.withTransfers(transfers: List<AppServer.IncomingTransferInfo>) =
        copy(incomingTransfers = transfers.toList())

    internal suspend fun autoAcceptWhileVisible() {
        combine(settings.autoFinish, appServer.incomingTransfers) { enabled, transfers ->
            if (enabled) transfers.filter { it.status == AppServer.Status.PENDING } else emptyList()
        }.collect { pending ->
            pending.forEach(::onAccept)
        }
    }

    fun onAccept(transfer: AppServer.IncomingTransferInfo) {
        viewModelScope.launch {
            val key = transfer.key()
            val pending = appServer.incomingTransfers.value.any {
                it.key() == key && it.status == AppServer.Status.PENDING
            }
            if (!pending || !acceptingTransfers.add(key)) return@launch

            try {
                withContext(ioDispatcher) {
                    if (!receiveDir.exists()) {
                        receiveDir.mkdirs()
                    }
                    appServer.acceptIncomingTransfer(transfer, File(receiveDir, transfer.name))
                }
            } finally {
                acceptingTransfers.remove(key)
            }
        }
    }

    fun onDecline(transfer: AppServer.IncomingTransferInfo) {
        viewModelScope.launch {
            appServer.onDeclineIncomingTransfer(transfer)
        }
    }

    fun onDelete(transfer: AppServer.IncomingTransferInfo) {
        viewModelScope.launch {
            appServer.onDeleteIncomingTransfer(transfer)
        }
    }

    fun onDownload(transfer: AppServer.IncomingTransferInfo) {
        viewModelScope.launch {
            val message = when (fileSaver.save(transfer, settings.saveToFolder.value)) {
                FileSaveResult.SUCCESS -> R.string.receive_file_saved
                FileSaveResult.FILE_UNAVAILABLE -> R.string.error_file_unavailable
                FileSaveResult.FOLDER_CREATE_FAILED -> R.string.error_folder_create_failed
                FileSaveResult.SELECTED_FOLDER_FAILED -> R.string.error_save_folder_failed
                FileSaveResult.SAVE_FAILED -> R.string.error_file_save_failed
            }
            setState { copy(userMessage = UserMessage(message)) }
        }
    }

    fun onUserMessageShown() {
        setState { copy(userMessage = null) }
    }

    private fun AppServer.IncomingTransferInfo.key() =
        Triple(fromHost.hostAddress, id, requestReceivedTime)
}