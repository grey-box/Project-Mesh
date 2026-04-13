package com.greybox.projectmesh.viewModel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greybox.projectmesh.server.AppServer
import com.ustadmobile.meshrabiya.ext.addressToByteArray
import com.ustadmobile.meshrabiya.ext.addressToDotNotation
import com.ustadmobile.meshrabiya.vnet.AndroidVirtualNode
import com.ustadmobile.meshrabiya.vnet.VirtualNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import java.net.InetAddress


data class SelectDestNodeScreenModel(
    val allNodes: Map<Int, VirtualNode.LastOriginatorMessage> = emptyMap(),
    val uris: List<Uri> = emptyList(),
    val contactingInProgressDevice: String? = null,
)

class SelectDestNodeScreenViewModel(
    di: DI,
    savedStateHandle: SavedStateHandle,
    private val sendUris: List<Uri>,
    private val popBackWhenDone: () -> Unit
): ViewModel(){

    private val _uiState = MutableStateFlow(SelectDestNodeScreenModel())
    val uiState: Flow<SelectDestNodeScreenModel> = _uiState.asStateFlow()
    private val appServer: AppServer by di.instance()
    private val node: AndroidVirtualNode by di.instance()

    init {
        _uiState.update { prev ->
            prev.copy(
                uris = sendUris,
            )
        }

        viewModelScope.launch {
            node.state.collect{
                _uiState.update { prev ->
                    prev.copy(
                        allNodes = it.originatorMessages
                    )
                }
            }
        }
    }

    fun onClickReceiver(address: Int){
        // convert the ip address to byte array, then convert it to InetAddress object
        // which can be used to perform network operation
        val inetAddress = InetAddress.getByAddress(address.addressToByteArray())
        Log.d("uri_track_onClickReveiver", "inetAddress: $inetAddress")
        // update the ui state to reflect that we are contacting the device
        _uiState.update { prev ->
            prev.copy(
                contactingInProgressDevice = address.addressToDotNotation()
            )
        }
        Log.d("uri_track_onClickReveiver", "sendUris: ${sendUris.toString()}")
        // Launch a coroutine in the ViewModel Scope
        viewModelScope.launch {
            // Switch the coroutine context to Dispatchers.IO for network operations
            val transfer = withContext(Dispatchers.IO){
                // Map over the list of uris and launch an asynchronous task for each uri
                sendUris.map { uri ->
                    async{
                        try{
                            Log.d("uri_track_onClickReveiver_Loop", uri.toString())
                            val response = appServer.addOutgoingTransfer(
                                uri = uri,
                                toNode = inetAddress,
                            )
                            Log.d("uri_track_onClickReveiver_Loop", "response: ${response.toString()}")
                            true
                        }
                        catch (e: Exception){
                            Log.e("AppServer", "Exception attempting to send $uri to destination", e)
                            false
                        }
                    }
                }.awaitAll()    // Wait for all the tasks to complete
            }
            // if any of the transfers were successful, pop back to previous screen
            if(transfer.any{it}){
                Log.d("uri_track_onClickReveiver", "popBackWhenDone")
                popBackWhenDone()
            }
        }
    }
}