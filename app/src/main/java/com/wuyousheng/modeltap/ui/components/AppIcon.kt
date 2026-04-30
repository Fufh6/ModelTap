package com.wuyousheng.modeltap.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

@Composable
fun AppIcon(
    @DrawableRes resId: Int,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(color)
    )
}
