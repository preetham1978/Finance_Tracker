package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Vantage Neo" — a deliberately bold, high-contrast identity built around
// pure black/white bases, the brand's signature gold as the one loud accent,
// and fully-saturated (not muted) income/expense colors. Paired with the
// hard-bordered, offset-shadow "sticker card" treatment in BrutalCard.kt,
// this is a structural departure from the soft, low-contrast card style
// most finance apps default to.
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD60A), // Vantage Gold
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF241D00),
    onPrimaryContainer = Color(0xFFFFE9A8),
    secondary = Color(0xFFFFCA28), // Warm Amber
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF00E676), // Income — vivid Spring Green
    onTertiary = Color(0xFF00210A),
    error = Color(0xFFFF3B3B), // Expense — hot saturated red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000), // True black
    onBackground = Color(0xFFFFFFFF), // Pure white body text
    surface = Color(0xFF141414),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFB5B5B5)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFFF5B301), // Vantage Gold
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFEEB8),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFFFFC94D), // Warm Amber
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF00C853), // Income — vivid Spring Green
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFFF3B30), // Expense — hot saturated red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFFFF), // Crisp paper white
    onBackground = Color(0xFF000000), // Pure black body text
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF4A4A4A)
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
