package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFBC02D), // Sunflower Yellow
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF5D4037),
    onPrimaryContainer = Color(0xFFFFF9C4),
    secondary = Color(0xFFFDD835), // Lemon Yellow
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFFFFF176), // Light Yellow (Income)
    onTertiary = Color(0xFF000000),
    error = Color(0xFFFF8A80), // Soft Coral (Expense)
    onError = Color(0xFF3E0A06),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212), // Dark background
    onBackground = Color(0xFFFFF9C4),
    surface = Color(0xFF212121), // Dark surface
    onSurface = Color(0xFFFFF9C4),
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFFFF9C4)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFBC02D), // Sunflower Yellow
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFFFFF9C4),
    onPrimaryContainer = Color(0xFF212121),
    secondary = Color(0xFFFDD835), // Lemon Yellow
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFFFFF176), // Light Yellow (Income)
    onTertiary = Color(0xFF000000),
    error = Color(0xFFC62828), // Crimson Red (Expense)
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFDF7), // Light yellow-ish background
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF), // Pure white cards
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242)
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
