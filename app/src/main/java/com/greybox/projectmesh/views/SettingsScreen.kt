package com.greybox.projectmesh.views

import android.content.Intent

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greybox.projectmesh.R
import com.greybox.projectmesh.ViewModelFactory
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.GradientButton
import com.greybox.projectmesh.ui.theme.GradientLongButton
import com.greybox.projectmesh.viewModel.SettingsScreenViewModel
import org.kodein.di.compose.localDI

import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme


@Composable
fun SettingsScreen(
    viewModel: SettingsScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { di, savedStateHandle -> SettingsScreenViewModel(di, savedStateHandle)},
            defaultArgs = null,
        )),
) {

    val context = LocalContext.current
    val ui by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(ui.userMessage?.id){
        ui.userMessage?.let {
            Toast.makeText(context, it.textRes, Toast.LENGTH_SHORT).show()
            viewModel.onUserMessageShown()
        }
    }



    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
    {
        Spacer(modifier = Modifier.height(MeshTheme.spacing.xl))
        // Title "Settings"
        Text(
            text = stringResource(id = R.string.settings),
            style = TextStyle(fontSize = MeshTheme.typography.headlineSmall.fontSize, fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Column(modifier = Modifier.padding(MeshTheme.spacing.xl)) {
            // General Setting Section (Language, Theme)
            SectionHeader(title = R.string.general)
            SettingItem(
                label = stringResource(id = R.string.language),
                trailingContent = {
                    LanguageSetting(
                        currentLanguage = ui.languageCode,
                        onLanguageSelected = {
                            viewModel.setLanguage(it)
                        }
                    )
                }
            )
            SettingItem(
                label = stringResource(id = R.string.theme),
                trailingContent = {
                    ThemeSetting(
                        currentTheme = ui.theme,
                        onThemeSelected = {
                            viewModel.setTheme(it)
                        }
                    )
                }
            )
            // Network Setting Section (Server Restart, Device Name Change)
            SectionHeader(title = R.string.network)
            SettingItem(
                label = stringResource(id = R.string.server),
                trailingContent = {
                    GradientButton(
                        text = stringResource(id = R.string.restart),
                        onClick = { viewModel.restartServer() }
                    )
                }
            )
            SettingItem(
                label = stringResource(id = R.string.device_name),
                trailingContent = {
                    GradientButton(text = ui.deviceName, onClick = { viewModel.showDeviceNameDialog() })
                }
            )
            if (ui.showDeviceNameDialog) {
                ChangeDeviceNameDialog(
                    onDismiss = { viewModel.dismissDeviceNameDialog()},
                    onConfirm = { 
                        viewModel.setDeviceName(it)
                        viewModel.dismissDeviceNameDialog()
                    },
                    deviceName = ui.deviceName
                )
            }
            // Receive Setting Section (Auto Finish, Save to folder)
            SectionHeader(title = R.string.receive)
            SettingItem(label = stringResource(id = R.string.auto_finish),
                trailingContent = {
                    Box(modifier = Modifier.width(130.dp).height(70.dp))
                    {
                        Switch(
                            checked = ui.autoFinish,
                            onCheckedChange = {
                                viewModel.setAutoFinish(it)
                            },
                            colors = SwitchDefaults.colors(),
                            modifier = Modifier.scale(1.3f).align(Alignment.Center)
                        )
                    }
                }
            )
            val directoryLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) {
                    // Persist the directory permission
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    Toast.makeText(context, "Directory selected: $uri", Toast.LENGTH_LONG).show()
                    viewModel.setSaveToFolder(uri.toString())
                } else {
                    Toast.makeText(context, "No directory selected", Toast.LENGTH_LONG).show()
                }
            }
            val folderNameToShow = if (ui.saveToFolder.startsWith("content://")) {
                // Decode Uri and extract the folder name for content URIs
                Uri.decode(ui.saveToFolder).split(":").lastOrNull() ?: "Unknown"
            } else {
                // Extract the last directory from the file path for plain file paths
                ui.saveToFolder.split("/").lastOrNull() ?: "Unknown"
            }
            SettingItem(label = stringResource(id = R.string.save_to_folder),
                trailingContent = {
                    GradientButton(text = folderNameToShow,
                        onClick = { directoryLauncher.launch(null) }
                    )
                }
            )
            // STA/AP Concurrency Setting Section (Only for Android 10 and below)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                SectionHeader(title = R.string.concurrency)
                SettingItem(label = "",
                    trailingContent = {
                        GradientLongButton(
                            text = stringResource(id = R.string.reset),
                            onClick = {
                                viewModel.resetConcurrency()
                            }
                        )
                    }
                )
            }
        }
    }
}
@Composable
fun SectionHeader(title: Int) {
    Spacer(modifier = Modifier.height(MeshTheme.spacing.lg))
    Text(
        text = stringResource(id = title),
        style = TextStyle(fontSize = MeshTheme.typography.titleLarge.fontSize, fontWeight = FontWeight.Bold)
    )
//    HorizontalDivider(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(0.dp, 10.dp),
//        thickness = 2.dp,
//        color = Color.Red
//    )
}

@Composable
fun SettingItem(label: String, trailingContent: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MeshTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = TextStyle(fontSize = MeshTheme.typography.bodyLarge.fontSize))
        Spacer(modifier = Modifier.weight(1f))
        trailingContent()
    }
}

@Composable
fun LanguageSetting(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit)
{
    // for Language setting
    var langExpanded by remember { mutableStateOf(false) } // Track menu visibility
    val langMenuItems = listOf("en" to "English", "es" to "Español", "cn" to "简体中文", "fr" to
            "Français") //
    // Menu
    // items
    val langSelectedOption = langMenuItems.firstOrNull {it.first == currentLanguage}?.second?:"English"
    Box()
    {
        GradientButton(text = langSelectedOption,
            onClick = { langExpanded = true })
        DropdownMenu(expanded = langExpanded,
            onDismissRequest = { langExpanded = false },
            properties = PopupProperties(true))
        {
            langMenuItems.forEach{ item ->
                DropdownMenuItem(
                    text = { Text(item.second) },
                    onClick = {
                        onLanguageSelected(item.first)
                        langExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ThemeSetting(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit)
{
    // for Theme setting
    var expanded by remember { mutableStateOf(false) } // Track menu visibility
    val themes = listOf("System", "Light", "Dark") // Menu items
    Box()
    {
        GradientButton(text = themes[currentTheme.ordinal],
            onClick = { expanded = true })
        DropdownMenu(expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(true))
        {
            AppTheme.entries.forEach{ theme ->
                DropdownMenuItem(
                    text = { Text(themes[theme.ordinal]) },
                    onClick = {
                        onThemeSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ChangeDeviceNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    deviceName: String,
){
    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .padding(MeshTheme.spacing.md)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.md)
            ) {
                Text("Enter New Device Name", style = MaterialTheme.typography.titleMedium)
                var inputText by remember { mutableStateOf("") }
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text(deviceName) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = {
                        if (inputText.isNotBlank()) {
                            onConfirm(inputText)
                        }
                    }) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}