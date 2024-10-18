package com.greybox.projectmesh.views

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.model.ReceiveScreenModel
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.viewModel.ReceiveScreenViewModel
import org.kodein.di.compose.localDI

@Composable
fun ReceiveScreen(
    viewModel: ReceiveScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { ReceiveScreenViewModel(it) },
            defaultArgs = null,
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState(ReceiveScreenModel())
    HandleIncomingTransfers(
        uiState = uiState,
        onAccept = viewModel::onAccept,
        onDecline = viewModel::onDecline,
        onDelete = viewModel::onDelete,
    )
}

@Composable
fun HandleIncomingTransfers(
    uiState: ReceiveScreenModel,
    onAccept: (AppServer.IncomingTransferInfo) -> Unit = {},
    onDecline: (AppServer.IncomingTransferInfo) -> Unit = {},
    onDelete: (AppServer.IncomingTransferInfo) -> Unit = {},
){
    val context = LocalContext.current
    fun openFile(transfer: AppServer.IncomingTransferInfo){
        // get the file from the IncomingTransferInfo object
        val file = transfer.file
        // only when the file transfer completed and the file is not null
        if (file != null && transfer.status == AppServer.Status.COMPLETED){
            // Generate a content URI for the file, it provide secure access to files
            val uri = FileProvider.getUriForFile(
                context, "com.greybox.projectmesh.fileprovider", file
            )
            // allow the system to find an app capable of handling and viewing the file
            val intent = Intent(Intent.ACTION_VIEW).apply {
                // The flag is set to ensure the receiving app can temporarily access the file uri with read permission
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                // determine the MIME type based on the file's extension using MimeTypeMap
                // If the MIME type cannot be determined, a default (*/*) fallback is used
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)?: "*/*"
                // Sets the URI and the MIME type on the intent so that the system can know
                // what kind of file it is dealing with and suggest the appropriate apps.
                setDataAndType(uri, mimeType)
            }

            // If an app is found, it launches the intent, allowing the user to view the file.
            // Otherwise, it shows a Toast message informing the user that File cannot be opened.
            if(intent.resolveActivity(context.packageManager) != null){
                try{
                    context.startActivity(intent)
                }
                catch (e: Exception){
                    Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
                }

            }
            else{
                Toast.makeText(context, "File cannot be opened", Toast.LENGTH_SHORT).show()
            }
        }

    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = uiState.incomingTransfers,
            key = {Triple(it.fromHost, it.id, it.requestReceivedTime)}
        ){ transfer ->
            ListItem(
                modifier = Modifier
                    .clickable {
                        openFile(transfer)
                    }
                    .fillMaxWidth(),

                headlineContent = {
                    Text(transfer.name)
                },
                supportingContent = {
                    Column{
                        val fromHostAddress = transfer.fromHost.hostAddress
                        Text("From ${transfer.deviceName}(${fromHostAddress})")
                        Text("Status: ${transfer.status}")
                        Text("${autoConvertByte(transfer.transferred)} / ${autoConvertByte(transfer.size)}")
                        if(transfer.status == AppServer.Status.PENDING){
                            Row{
                                IconButton(onClick = {onAccept(transfer)},
                                    modifier = Modifier.width(100.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Accept")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = {onDecline(transfer)},
                                    modifier = Modifier.width(100.dp)) {
                                    Icon(Icons.Default.Cancel, contentDescription = "Decline")
                                }
                            }
                        }
                    }
                },
                trailingContent = {
                    if(transfer.status == AppServer.Status.COMPLETED){
                        IconButton(onClick = {onDelete(transfer)}) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    }
}