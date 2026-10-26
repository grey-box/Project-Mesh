package com.greybox.projectmesh.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f) // Scale down when pressed
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
            .height(MeshTheme.size.buttonHeight) // Height of the button
            .widthIn(min = MeshTheme.size.buttonMinWidth, max = maxWidth) // Width of the button
            .padding(horizontal = MeshTheme.spacing.md)
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            style = MeshTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, // Truncate text if it overflows
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
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f) // Scale down when pressed
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
            .height(MeshTheme.size.buttonHeight) // Height of the button
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            style = MeshTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, // Truncate text if it overflows
        )
    }
}
