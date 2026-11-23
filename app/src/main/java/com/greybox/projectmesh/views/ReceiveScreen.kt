package com.greybox.projectmesh.views

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.server.AppServer
import com.greybox.projectmesh.viewModel.ReceiveScreenViewModel
import org.kodein.di.compose.localDI
import androidx.compose.material3.HorizontalDivider
import com.greybox.projectmesh.BuildConfig
import com.greybox.projectmesh.ui.theme.MeshTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle


@Composable
fun ReceiveScreen(
    viewModel: ReceiveScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, _ -> ReceiveScreenViewModel(di) },
            defaultArgs = null,
        )
    ),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(lifecycle, viewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.autoAcceptWhileVisible()
        }
    }

    LaunchedEffect(uiState.userMessage?.id) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message.textRes, Toast.LENGTH_LONG).show()
            viewModel.onUserMessageShown()
        }
    }

    fun openFile(transfer: AppServer.IncomingTransferInfo){
        // get the file from the IncomingTransferInfo object
        val file = transfer.file
        // only when the file transfer completed and the file is not null
        if (file != null && transfer.status == AppServer.Status.COMPLETED){
            // Generate a content URI for the file, it provide secure access to files
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )            // allow the system to find an app capable of handling and viewing the file
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
                    // Was "Error opening file: ${e.message}", which showed the user a raw
                    // exception string in a Toast that vanishes in two seconds and cannot
                    // be re-read. SC 3.3.1.
                    Toast.makeText(
                        context,
                        context.getString(R.string.error_file_open_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }

            }
            else{
                Toast.makeText(context, context.getString(R.string.error_file_open_unsupported), Toast.LENGTH_SHORT).show()
            }
        }
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = uiState.incomingTransfers,
            key = {"${it.fromHost.hostAddress}-${it.id}-${it.requestReceivedTime}".hashCode()}
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
                        Text(stringResource(id = R.string.from) + ":")
                        Text("${transfer.deviceName}(${fromHostAddress})")
                        Text(stringResource(id = R.string.status) + ": ${transfer.status}")
                        Text(autoConvertByte(transfer.transferred) + "/" + autoConvertByte(transfer.size))
                        if(transfer.status == AppServer.Status.COMPLETED){
                            Text(stringResource(id = R.string.elapsed_time) + ": ${autoConvertMS(transfer.transferTime)}")
                        }
                        if(transfer.status == AppServer.Status.PENDING){
                            Row{
                                IconButton(onClick = { viewModel.onAccept(transfer) },
                                    modifier = Modifier.width(MeshTheme.size.buttonMinWidth)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Accept")
                                }
                                Spacer(modifier = Modifier.width(MeshTheme.spacing.sm))
                                IconButton(onClick = { viewModel.onDecline(transfer) },
                                    modifier = Modifier.width(MeshTheme.size.buttonMinWidth)) {
                                    Icon(Icons.Default.Cancel, contentDescription = "Decline")
                                }
                            }
                        }
                    }
                },
                trailingContent = {
                    if(transfer.status == AppServer.Status.COMPLETED){
                        Row (
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MeshTheme.spacing.sm) // Space between buttons
                        ){
                            IconButton(onClick = { viewModel.onDelete(transfer) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                            IconButton(onClick = { viewModel.onDownload(transfer) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                            }
                        }
                    }
                    else if(transfer.status == AppServer.Status.DECLINED || transfer.status == AppServer.Status.FAILED){
                        Row (
                            verticalAlignment = Alignment.CenterVertically
                        ){
                            IconButton(onClick = { viewModel.onDelete(transfer) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}
