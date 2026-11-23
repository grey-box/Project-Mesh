package com.greybox.projectmesh.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.OutlinedButton
import com.greybox.projectmesh.ui.theme.AppTheme
import com.greybox.projectmesh.ui.theme.MeshTheme
import com.greybox.projectmesh.ui.theme.ProjectMeshTheme
import androidx.compose.ui.text.style.TextOverflow

// This is a pre-defined button with white background and black text
@Composable
fun TransparentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MeshTheme.shapes.medium, // only if the old one wasn't M3's default shape
    ) { Text(text) }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MeshTheme.size.buttonHeight)
            .widthIn(min = MeshTheme.size.buttonMinWidth),
        enabled = enabled,
        shape = MeshTheme.shapes.medium,
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GradientLongButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = MeshTheme.size.buttonHeight)
            .fillMaxWidth(),
        enabled = enabled,
        shape = MeshTheme.shapes.medium,
    ) {
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CustomButtonSamples() {
    Surface {
        Column(
            modifier = Modifier.padding(MeshTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MeshTheme.spacing.sm)
        ) {
            // A short label, and the kind of live value SettingsScreen actually passes.
            // The long one is the case that used to truncate to "Downloa…" at 200%.
            GradientButton(text = "Restart", onClick = {})
            GradientButton(text = "Pixel 7 Pro (Peter)", onClick = {})
            GradientLongButton(text = "Reset STA/AP Concurrency", onClick = {})
            TransparentButton(text = "Send File", onClick = {}, enabled = true)
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun CustomButtonLightPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) { CustomButtonSamples() }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun CustomButtonDarkPreview() {
    ProjectMeshTheme(appTheme = AppTheme.DARK) { CustomButtonSamples() }
}

// The preview that would have caught the bug. These buttons locked a 48dp height around a
// maxLines = 1 label, so at the 200% font scale Android allows, the label was clipped
// outright -- a straight SC 1.4.4 failure that no light/dark preview could reveal.
@Preview(showBackground = true, name = "Large text (200%)", fontScale = 2f)
@Composable
private fun CustomButtonLargeTextPreview() {
    ProjectMeshTheme(appTheme = AppTheme.LIGHT) { CustomButtonSamples() }
}
