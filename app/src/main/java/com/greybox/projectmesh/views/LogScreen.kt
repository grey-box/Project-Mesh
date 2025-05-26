package com.greybox.projectmesh.views

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.MNetLoggerAndroid
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.viewModel.LogScreenModel
import com.greybox.projectmesh.viewModel.LogScreenViewModel
import com.ustadmobile.meshrabiya.log.MNetLogger
import org.kodein.di.compose.localDI
import org.kodein.di.instance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    viewModel: LogScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> LogScreenViewModel(di, savedStateHandle) },
            defaultArgs = null,
        )
    )
){
    val uiState by viewModel.uiState.collectAsState(initial = LogScreenModel())
    ShowLogScreen(uiState)
}

@Composable
fun ShowLogScreen(
    uiState: LogScreenModel
){
    val context = LocalContext.current
    val localDi = localDI()
    val logger: MNetLogger by localDi.instance()
    val androidLogger = logger as MNetLoggerAndroid
    val clipboardManager = LocalClipboardManager.current
    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss.SS", Locale.getDefault())
    }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedLineIds by remember { mutableStateOf(setOf<Int>()) }

    Column {
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selectedLineIds = uiState.logs.map { it.lineId }.toSet()
                }) {
                    Text("Select All")
                }
                TextButton(onClick = {
                    val selectedLogs = uiState.logs
                        .filter { selectedLineIds.contains(it.lineId) }
                        .joinToString("\n") {
                            "[${timeFormatter.format(Date(it.time))}] ${it.line}"
                        }
                    clipboardManager.setText(AnnotatedString(selectedLogs))
                    Toast.makeText(context, "Logs copied!", Toast.LENGTH_SHORT).show()
                    selectionMode = false
                    selectedLineIds = emptySet()
                }) {
                    Text("Copy")
                }
                TextButton(onClick = {
                    selectionMode = false
                    selectedLineIds = emptySet()
                }) {
                    Text("Cancel")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = uiState.logs,
                key = { it.lineId }
            ) { logLine ->
                val formattedTime = timeFormatter.format(Date(logLine.time))
                val logText = "[${formattedTime}] ${logLine.line}"
                val isSelected = selectedLineIds.contains(logLine.lineId)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (selectionMode) {
                                        selectedLineIds = if (isSelected) {
                                            selectedLineIds - logLine.lineId
                                        } else {
                                            selectedLineIds + logLine.lineId
                                        }
                                    }
                                },
                                onLongPress = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedLineIds = setOf(logLine.lineId)
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedLineIds = if (checked) {
                                    selectedLineIds + logLine.lineId
                                } else {
                                    selectedLineIds - logLine.lineId
                                }
                            }
                        )
                    }
                    ListItem(
                        modifier = Modifier.weight(1f),
                        headlineContent = {
                            Text(logText)
                        }
                    )
                }
            }
        }
    }
}