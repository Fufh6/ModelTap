package com.wuyousheng.modeltap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4B8BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF17324D),
    secondary = Color(0xFF25C2B5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4FBF7),
    onSecondaryContainer = Color(0xFF153E3A),
    background = Color(0xFFF7FAFE),
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F6FD),
    onSurfaceVariant = Color(0xFF6B778D),
    outline = Color(0xFFD8E4F2),
    error = Color(0xFFFF3B4F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80AFFF),
    secondary = Color(0xFF50D5C9),
    background = Color(0xFF111827),
    surface = Color(0xFF182234),
    surfaceVariant = Color(0xFF243044),
    outline = Color(0xFF42526A)
)

@Composable
fun ModelTapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
