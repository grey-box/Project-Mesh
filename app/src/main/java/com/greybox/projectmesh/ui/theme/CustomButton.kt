package com.greybox.projectmesh.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * A transparent button with white background and black text.
 * Optional rounded corners and full-width by default.
 */
@Composable
fun TransparentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = false
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White, // White background
            contentColor = Color.Black    // Black text
        ),
        border = BorderStroke(1.dp, Color.Black), // Black border
        shape = RoundedCornerShape(8.dp), // Rounded corners
        modifier = modifier.fillMaxWidth(), // Fill max width by default
        enabled = enabled
    ) {
        Text(text = text)
    }
}

/**
 * A gradient button with press animation.
 *
 * @param text The label for the button.
 * @param gradientColors Colors to use for horizontal gradient background.
 * @param textColor Color of the text.
 * @param maxWidth Maximum width of the button.
 * @param onClick Action to perform when the button is clicked.
 */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(Color(0xFF4CAF50), Color(0xFF81C784)), // Default gradient
    textColor: Color = Color.White,
    maxWidth: Dp = 120.dp,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f) // Scale down on press

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100) // Short delay to show pressed effect
            isPressed = false
        }
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(12.dp)) // Shadow effect
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
            .height(50.dp)
            .widthIn(min = 120.dp, max = maxWidth)
            .padding(horizontal = 16.dp)
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center // Center the text
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * A full-width gradient button with press animation.
 *
 * Similar to GradientButton but fills the available width.
 */
@Composable
fun GradientLongButton(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(Color(0xFF4CAF50), Color(0xFF81C784)), // Default gradient
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.85f else 1f) // Scale down on press

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth() // Fill the full width
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
            .height(50.dp)
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
