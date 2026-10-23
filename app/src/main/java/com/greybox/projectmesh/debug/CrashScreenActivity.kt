package com.greybox.projectmesh.debug

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.greybox.projectmesh.R
import java.io.PrintWriter
import java.io.StringWriter
import com.greybox.projectmesh.ui.theme.MeshTheme

class CrashScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_crash_screen)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }



        // Load content
        setContent {
            Crash()
        }

    }
    @Composable
    fun Crash()
    {
        Surface(modifier = Modifier.background(MeshTheme.colors.background).fillMaxSize()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text="Crash! Please screenshot and send to george.varvaris@grey-box.ca",Modifier.background(MeshTheme.colors.background), color = MeshTheme.colors.error)
                Button(content = {Text("Exit app")}, onClick = { finish() } )
                CrashHandler.getThrowableFromIntent(intent).let {
                    Text("Message: " + it?.message + "\nStack trace:\n" + it?.stackTraceToString(),Modifier.background(MeshTheme.colors.background), color = MeshTheme.colors.error)
                }
            }
        }
    }
}