package com.greybox.projectmesh.views

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
//contextual help
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.greybox.projectmesh.R
import com.greybox.projectmesh.components.HelpDialog
import com.greybox.projectmesh.components.HelpLink
import com.greybox.projectmesh.components.HelpParagraph
//end
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.MNetLoggerAndroid
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.ui.theme.MeshTheme
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
    //contextual help
    var showLogHelp by remember { mutableStateOf(false) }

    if (showLogHelp) {
        HelpDialog(
            title = stringResource(R.string.help_log_title),
            onDismiss = { showLogHelp = false },
        ) {
            HelpParagraph(stringResource(R.string.help_log_body_intro))
            Text(
                text = stringResource(R.string.help_log_example),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MeshTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = MeshTheme.spacing.sm),
            )
            HelpParagraph(stringResource(R.string.help_log_part_time))
            HelpParagraph(stringResource(R.string.help_log_part_component))
            HelpParagraph(stringResource(R.string.help_log_part_address))
            HelpParagraph(stringResource(R.string.help_log_part_event))
        }
    }//end of contextual help
    Column {
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshTheme.spacing.sm),
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
        } else {
            // A gesture-free way into selection mode.
            //
            // Long-press used to be the ONLY way to start selecting log lines, which made
            // copying logs — the thing you do to report a bug — unreachable for anyone who
            // cannot hold a steady press for half a second: a hand tremor, limited grip
            // strength, a prosthetic, Switch Access, or an external keyboard. Exposing the
            // gesture to TalkBack (below) fixes the screen-reader case but does nothing
            // for those users, because their barrier is the physical press, not the
            // announcement. Two different disabilities, one line of code, two fixes.
            // SC 2.1.1 (Keyboard) and SC 2.5.7 (Dragging Movements).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MeshTheme.spacing.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { selectionMode = true },
                    enabled = uiState.logs.isNotEmpty()
                ) {
                    Text("Select")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = uiState.logs,
                key = { it.lineId }
            ) { logLine ->
                val formattedTime = timeFormatter.format(Date(logLine.time))
                val logText = "[${formattedTime}] ${logLine.line}"
                val isSelected = selectedLineIds.contains(logLine.lineId)

                val enterSelection = {
                    selectionMode = true
                    selectedLineIds = setOf(logLine.lineId)
                }

                // In selection mode the row is a checkbox, and says so. `toggleable` with
                // Role.Checkbox gives TalkBack the checked state and a working toggle
                // action; the old `pointerInput` + `detectTapGestures` registered no
                // semantics action at all, so there was nothing for a screen reader to
                // activate and selection mode could not be used. SC 4.1.2.
                //
                // Out of selection mode the long-press is kept for the people who know
                // it, but published as a real accessibility action rather than a raw
                // pointer handler, so TalkBack can offer it from the actions menu.
                val rowInteraction = if (selectionMode) {
                    Modifier.toggleable(
                        value = isSelected,
                        role = Role.Checkbox,
                        onValueChange = { checked ->
                            selectedLineIds = if (checked) {
                                selectedLineIds + logLine.lineId
                            } else {
                                selectedLineIds - logLine.lineId
                            }
                        }
                    )
                } else {
                    Modifier
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Select log lines") {
                                    enterSelection()
                                    true
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { enterSelection() })
                        }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MeshTheme.spacing.sm, vertical = MeshTheme.spacing.xs)
                        .background(
                            // surfaceContainer, not surfaceVariant. The hairline below is
                            // drawn in `outline`, and outline-on-surfaceVariant measures
                            // 2.94:1 in the dark scheme — just under the 3:1 that SC
                            // 1.4.11 requires of a border that delimits a component. On
                            // surfaceContainer the same border measures 4.89:1 dark and
                            // 3.95:1 light.
                            //
                            // The selected tint is primaryContainer rather than the old
                            // primary.copy(alpha = 0.2f). Both measure about 1.0:1
                            // against the unselected row, so NEITHER is a usable state
                            // indicator on its own — the Checkbox is what actually carries
                            // selection, and this tint is redundant reinforcement only.
                            // Using a real container token at least drops the alpha hack,
                            // whose composited result nobody had measured.
                            color = if (isSelected) MeshTheme.colors.primaryContainer
                            else MeshTheme.colors.surfaceContainer,
                            shape = MeshTheme.shapes.small
                        )
                        .border(
                            // No border-width token exists: 05 defines spacing, size,
                            // shape and elevation only. DividerDefaults.Thickness is the
                            // M3 hairline that pairs with the outline role, so the width
                            // comes from the library rather than from a literal here.
                            width = DividerDefaults.Thickness,
                            color = MeshTheme.colors.outline,
                            shape = MeshTheme.shapes.small
                        )
                        .clip(MeshTheme.shapes.small)
                        .then(rowInteraction),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            // null, so the checkbox is a visual indicator and the row owns
                            // the interaction. Left clickable it became a second focus
                            // target inside a row that is itself toggleable, which makes
                            // TalkBack announce the same control twice.
                            onCheckedChange = null
                        )
                    }
                    ListItem(
                        modifier = Modifier.weight(1f),
                        // ListItem paints its own container, defaulting to `surface`,
                        // straight over the row background set above. The selected tint
                        // and the row colour were therefore being hidden behind it for the
                        // whole width of the text.
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(logText)
                        }
                    )
                }
            }
        }
        //contextual help
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            HelpLink(
                text = stringResource(R.string.help_log_link),
                onClick = { showLogHelp = true },
            )
        }//end of contextual help
    }
}