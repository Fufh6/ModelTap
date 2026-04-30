package com.wuyousheng.modeltap.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val MinAppFontScale = 0.95f
private const val MaxAppFontScale = 1.12f

@Composable
fun AdaptiveDensity(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val appDensity = remember(density.density, density.fontScale) {
        Density(
            density = density.density,
            fontScale = density.fontScale.coerceIn(MinAppFontScale, MaxAppFontScale)
        )
    }

    CompositionLocalProvider(LocalDensity provides appDensity) {
        content()
    }
}
