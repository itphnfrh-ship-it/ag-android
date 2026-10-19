package com.cruisepass.verify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1C2748)
private val Wave = Color(0xFF00A8B4)
private val Foam = Color(0xFFF4F7F8)
private val Ink = Color(0xFF1C2748)

private val PortColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Wave,
    onSecondary = Color.White,
    background = Foam,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

@Composable
fun CruisePassVerifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PortColors,
        content = content,
    )
}
