package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Modernized "Vantage Gold" palette — keeps the brand's signature amber/gold
// identity but restructures it for a cleaner, more premium fintech look:
// neutral body text (not tinted yellow), a richer near-black dark surface,
// and dedicated green/red for income vs. expense so money direction reads
// at a glance instead of blending into the brand color.
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD60A), // Vantage Gold
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3D3100),
    onPrimaryContainer = Color(0xFFFFE9A8),
    secondary = Color(0xFFFFCA28), // Warm Amber
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF22C55E), // Income Green
    onTertiary = Color(0xFF00210A),
    error = Color(0xFFFF5C5C), // Expense Coral-Red
    onError = Color(0xFF3E0000),
    errorContainer = Color(0xFF7A1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101014), // Rich near-black
    onBackground = Color(0xFFF0F0F0), // Neutral body text
    surface = Color(0xFF1B1B20),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF2A2A30),
    onSurfaceVariant = Color(0xFFC7C7CE)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFFF5B301), // Vantage Gold (deepened for contrast on white)
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFEEB8),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFFFFC94D), // Warm Amber
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF16A34A), // Income Green
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626), // Expense Red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAF7), // Warm off-white
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF2F2ED),
    onSurfaceVariant = Color(0xFF54544D)
)

@Composable
fun FinanceTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
