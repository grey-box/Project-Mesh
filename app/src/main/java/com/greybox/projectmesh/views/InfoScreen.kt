package com.greybox.projectmesh.views

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InfoScreen() {
    Column (modifier = Modifier.fillMaxSize().padding(20.dp)){
        Text("Project Mesh -- Version 1.0", style = TextStyle(fontSize = 18.sp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Device Name: ${Build.BRAND.substring(0, 1).uppercase() + Build.BRAND.substring(1).lowercase()} ${Build.MODEL}",
            style = TextStyle(fontSize = 18.sp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Android Version: ${Build.VERSION.RELEASE}", style = TextStyle(fontSize = 18.sp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("SDK Version: ${Build.VERSION.SDK_INT}", style = TextStyle(fontSize = 18.sp))
        Spacer(modifier = Modifier.height(12.dp))
    }

}