package com.greybox.projectmesh.views

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import com.greybox.projectmesh.buttonStyle.GradientButton
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.viewModel.SettingsScreenViewModel
import org.kodein.di.compose.localDI


@Composable
fun SettingsScreen(
    viewModel: SettingsScreenViewModel = viewModel(
        factory = ViewModelFactory(
            di = localDI(),
            owner = LocalSavedStateRegistryOwner.current,
            vmFactory = { SettingsScreenViewModel(it) },
            defaultArgs = null,
        )),
    onThemeChange: (AppTheme) -> Unit,
    onLanguageChange: (String) -> Unit,
    onRestartServer: () -> Unit,
    onDeviceNameChange: (String) -> Unit
) {
    val currTheme = viewModel.theme.collectAsState()
    val currLang = viewModel.lang.collectAsState()
    val currDeviceName = viewModel.deviceName.collectAsState()

    //var deviceName by remember { mutableStateOf(Build.MODEL) }
    var showDialog by remember { mutableStateOf(false) }
    var autoFinish by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()))
    {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text=stringResource(id = R.string.settings), style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(modifier = Modifier.height(18.dp))
        Column(modifier = Modifier.padding(36.dp)) {
            Text(stringResource(id = R.string.general), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 10.dp),
                thickness = 2.dp,
                color = Color.Red
            )
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    stringResource(id = R.string.language), style = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                LanguageSetting(currentLanguage = currLang.value,
                    onLanguageSelected = { selectedLanguageCode ->
                        viewModel.saveLang(selectedLanguageCode)
                        onLanguageChange(selectedLanguageCode)
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    text = stringResource(id = R.string.theme), style = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                ThemeSetting(currentTheme = currTheme.value,
                    onThemeSelected = { selectedTheme ->
                        viewModel.saveTheme(selectedTheme)
                        onThemeChange(selectedTheme)
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(id = R.string.network), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 10.dp),
                thickness = 2.dp,
                color = Color.Red
            )
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    stringResource(id = R.string.server), style = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                GradientButton(
                    text = stringResource(id = R.string.restart),
                    onClick = {
                        // restart the server
                        onRestartServer()
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    text = stringResource(id = R.string.device_name),
                    style = TextStyle(fontSize = 18.sp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.weight(1f))
                GradientButton(
                    text = currDeviceName.value,
                    onClick = {
                        // pop a dialog to change the device name
                        showDialog = true
                    }
                )
                if(showDialog){
                    ChangeDeviceNameDialog(
                        onDismiss = { showDialog = false },
                        onConfirm = { newDeviceName ->
                            viewModel.saveDeviceName(newDeviceName)
                            onDeviceNameChange(newDeviceName)
                            showDialog = false
                        },
                        deviceName = currDeviceName.value
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(id = R.string.receive), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 10.dp),
                thickness = 2.dp,
                color = Color.Red
            )
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    stringResource(id = R.string.auto_finish), style = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .width(130.dp)
                    .height(70.dp))
                {
                    Switch(checked = autoFinish, onCheckedChange = { autoFinish = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            uncheckedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedTrackColor = Color.LightGray,
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .scale(1.3f))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp, 16.dp))
            {
                Text(
                    text = stringResource(id = R.string.save_to_folder), style = TextStyle(fontSize = 18.sp), modifier = Modifier
                        .align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1f))
                GradientButton(text = "Download", onClick = { })
            }
        }
    }
}
@Composable
fun LanguageSetting(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit)
{
    // for Language setting
    var langExpanded by remember { mutableStateOf(false) } // Track menu visibility
    val langMenuItems = listOf("System" to "System",
        "en" to "English", "es" to "Español", "cn" to "简体中文") // Menu items
    val langSelectedOption = langMenuItems.firstOrNull {it.first == currentLanguage}?.second?:"System"
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
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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