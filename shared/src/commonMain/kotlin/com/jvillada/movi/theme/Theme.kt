package com.jvillada.movi.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MinPrimary,
    onPrimary = MinBg,
    primaryContainer = MinPrimaryContainer,
    onPrimaryContainer = MinOnPrimaryContainer,
    background = MinBg,
    onBackground = MinText,
    surface = MinSurface,
    onSurface = MinText,
    surfaceContainer = MinSurfaceContainer,
    surfaceContainerLow = MinSurfaceContainerLow,
    surfaceContainerHigh = MinSurfaceContainerHigh,
    surfaceContainerHighest = MinSurfaceContainerHighest,
    onSurfaceVariant = MinTextMute,
    outline = MinBorderStrong,
    outlineVariant = MinHairline,
    error = MinExpense,
    onError = MinBg,
)

@Composable
fun MoviTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
