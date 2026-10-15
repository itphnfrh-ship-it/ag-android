package com.ag.port.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0E3A5D)
private val Sea = Color(0xFF1C6E8C)
private val Foam = Color(0xFFF3F6F8)
private val Ink = Color(0xFF12202B)

private val PortColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    secondary = Sea,
    onSecondary = Color.White,
    background = Foam,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

@Composable
fun AgPortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PortColors,
        content = content,
    )
}
