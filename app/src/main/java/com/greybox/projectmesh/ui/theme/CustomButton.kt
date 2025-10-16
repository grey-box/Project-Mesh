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

// This is a pre-defined button with white background and black text
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
            containerColor = Color.White, // Background color
            contentColor = Color.Black    // Text color
        ),
        border = BorderStroke(1.dp, Color.Black), // Black border
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 8.dp).size(50.dp),
        enabled = enabled
    ) {
        Text(text = text)
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(Color(0xFF4CAF50), Color(0xFF81C784)), // Default gradient colors
    textColor: Color = Color.White,
    maxWidth: Dp = 120.dp,
    onClick: () -> Unit
) {
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
            .shadow(8.dp, RoundedCornerShape(12.dp)) // Shadow effect
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
            .height(50.dp) // Height of the button
            .widthIn(min = 120.dp, max = maxWidth) // Width of the button
            .padding(horizontal = 16.dp)
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
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
    gradientColors: List<Color> = listOf(Color(0xFF4CAF50), Color(0xFF81C784)), // Default gradient colors
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
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
            .shadow(8.dp, RoundedCornerShape(12.dp)) // Shadow effect
            .background(
                brush = Brush.horizontalGradient(gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
            .height(50.dp) // Height of the button
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center // Center content in the box
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, // Truncate text if it overflows
        )
    }
}
