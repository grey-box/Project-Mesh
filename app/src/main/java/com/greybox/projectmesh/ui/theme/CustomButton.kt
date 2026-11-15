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

// This is a pre-defined button with white background and black text
@Composable
fun TransparentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MeshTheme.colors.primary
        ),
        // Was a hardcoded Color.Black, which ignored the theme and sat wrong in dark.
        // outline is the M3 role for exactly this.
        border = BorderStroke(ButtonDefaults.outlinedButtonBorder.width, MeshTheme.colors.outline),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Text(text = text)
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null, // null = solid primary; pass two or more colors for a gradient
    textColor: Color = MeshTheme.colors.onPrimary,
    maxWidth: Dp = MeshTheme.size.buttonMinWidth,
    onClick: () -> Unit
) {
    val brushColors = gradientColors ?: List(2) { MeshTheme.colors.primary }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    var isPressed by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    // The press animation punches the whole button down to 85% and back. For someone with
    // a vestibular disorder that is six lurches per visit to Settings, where these
    // buttons live. If they have switched animations off system-wide they have already
    // said motion is a problem, so the button stays still. WCAG 2.1 SC 2.3.3.
    val scale by animateFloatAsState(if (isPressed && !reduceMotion) 0.85f else 1f)
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100) // Wait for 100 ms
            isPressed = false
        }
    }
    Box(
        modifier = modifier
            .scale(scale)
            .shadow(MeshTheme.elevation.level2, MeshTheme.shapes.medium) // Shadow effect
            .background(
                brush = Brush.horizontalGradient(brushColors),
                shape = MeshTheme.shapes.medium
            )
            // heightIn, not height: a fixed 48dp with a single-line label clipped the text
            // outright at the 200% font scale Android allows, which is a straight SC 1.4.4
            // failure. The minimum still holds the 48dp touch target.
            .heightIn(min = MeshTheme.size.buttonHeight)
            // The cap grows with the system font scale. With min and max both at 120dp the
            // button was a fixed width, so at 200% text even "System" split mid-word
            // ("Syste / m"). Scaling the cap keeps the button's proportions to its text.
            .widthIn(min = MeshTheme.size.buttonMinWidth, max = maxWidth * fontScale)
            // clickable sits above padding so the padded area is tappable too. Underneath
            // it, the touch target was the text box alone rather than the button you see.
            .clickable(
                // Without an explicit Role this is a Box that happens to react to taps:
                // TalkBack read the label as static text, with no "button" and no
                // "double-tap to activate". SC 4.1.2 Name, Role, Value. Six of these
                // render in SettingsScreen, so most of that screen was unannounced.
                role = Role.Button,
                onClickLabel = text
            ) {
                isPressed = true
                onClick()
            }
            .padding(horizontal = MeshTheme.spacing.md, vertical = MeshTheme.spacing.xs),
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            style = MeshTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // maxLines = 1 + Ellipsis is deliberately gone. These labels are live values —
            // a device name, a folder name, a language — so truncating them loses content
            // rather than trimming decoration. The width cap above still bounds the shape.
        )
    }
}

@Composable
fun GradientLongButton(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color>? = null, // null = solid primary; pass two or more colors for a gradient
    textColor: Color = MeshTheme.colors.onPrimary,
    onClick: () -> Unit
) {
    val brushColors = gradientColors ?: List(2) { MeshTheme.colors.primary }
    var isPressed by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    // See GradientButton: honours the system "Remove animations" setting. SC 2.3.3.
    val scale by animateFloatAsState(if (isPressed && !reduceMotion) 0.85f else 1f)
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100) // Wait for 100 ms
            isPressed = false
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(MeshTheme.elevation.level2, MeshTheme.shapes.medium) // Shadow effect
            .background(
                brush = Brush.horizontalGradient(brushColors),
                shape = MeshTheme.shapes.medium
            )
            // heightIn, not height: see GradientButton. SC 1.4.4 at 200% font scale.
            .heightIn(min = MeshTheme.size.buttonHeight)
            .clickable(
                // SC 4.1.2: announce as a button, not as text that happens to be tappable.
                role = Role.Button,
                onClickLabel = text
            ) {
                isPressed = true
                onClick()
            }
            .padding(horizontal = MeshTheme.spacing.md, vertical = MeshTheme.spacing.xs),
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            style = MeshTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // maxLines = 1 + Ellipsis removed: see GradientButton.
        )
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
