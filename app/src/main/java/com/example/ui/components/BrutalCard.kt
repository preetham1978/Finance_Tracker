package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vantage's signature "sticker card": a hard-edged card with a solid offset
 * shadow block instead of Material's soft blurred elevation, and a bold
 * outline instead of no border at all. This is the core visual signature of
 * the "Vantage Neo" look — a deliberate, graphic departure from the
 * soft/minimal card style most finance apps use by default.
 *
 * Drop-in replacement for Card(...) { ... }: same content lambda, just swap
 * the composable name and drop the colors/shape params (this owns its own).
 */
@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    accentColor: Color = MaterialTheme.colorScheme.onBackground,
    cornerRadius: Dp = 12.dp,
    shadowOffset: Dp = 6.dp,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val offsetPx = shadowOffset.toPx()
                val radiusPx = cornerRadius.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(offsetPx, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            }
            .clip(shape)
            .background(backgroundColor)
            .border(width = borderWidth, color = accentColor, shape = shape)
    ) {
        content()
    }
}
