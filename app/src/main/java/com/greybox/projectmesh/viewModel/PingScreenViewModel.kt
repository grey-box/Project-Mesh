package com.greybox.projectmesh.viewModel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import org.kodein.di.DI
import com.ustadmobile.meshrabiya.ext.requireAddressAsInt
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import kotlinx.coroutines.launch
import org.kodein.di.instance
import java.net.InetAddress
import androidx.compose.runtime.Immutable
import com.greybox.projectmesh.arch.StateViewModel
import com.greybox.projectmesh.user.UserRepository
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.kodein.di.DIAware
//screen now reads ip from the virtualAddress parameter
@Immutable
data class PingUiState(
    val deviceName: String = "",
    val pingResults: List<PingResult> = emptyList(),
)


@Immutable
data class PingResult(
    val messageId: Int,
    val pingTimeMs: Int,
    val hopCount: Int,
    val lastHopAddress: String,
)

class PingScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle,
    virtualAddress: InetAddress,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO, //swapped for a test dispatcher in unit test
): StateViewModel<PingUiState>(PingUiState()), DIAware {

    override val di = di
    private val node: AndroidVirtualNode by instance()
    private val userRepository: UserRepository by instance()
    private var lastTimeReceived: Long = 0L

    init {
        viewModelScope.launch(ioDispatcher) {
            val name = userRepository.getUserByIp(virtualAddress.hostAddress)?.name ?: "Unknown"
            setState { copy(deviceName = name) }
        }

        viewModelScope.launch {
            node.state.collect { nodeState ->
                val message = nodeState.originatorMessages[virtualAddress.requireAddressAsInt()]
                if (message != null && message.timeReceived != lastTimeReceived) {
                    lastTimeReceived = message.timeReceived
                    //TODO: list grows forever while the screen is open
                    setState { copy(pingResults = pingResults + message.toPingResult()) }
                }
            }
        }
    }

    private fun VirtualNode.LastOriginatorMessage.toPingResult() = PingResult(
        messageId = originatorMessage.messageId,
        pingTimeMs = originatorMessage.pingTimeSum.toInt(),
        hopCount = hopCount.toInt(),
        lastHopAddress = lastHopAddr.addressToDotNotation(),
    )
}