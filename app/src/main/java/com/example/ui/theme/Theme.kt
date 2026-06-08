package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LeicaDarkColorScheme = darkColorScheme(
    primary = LeicaRed,
    secondary = DarkSurface,
    tertiary = DarkCard,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = LightTextPrimary,
    onSecondary = LightTextPrimary,
    onTertiary = LightTextPrimary,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    error = LeicaRed
)

// Minimalist fallback scheme
private val LeicaLightColorScheme = lightColorScheme(
    primary = LeicaRed,
    secondary = DarkSurface,
    tertiary = DarkCard,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = LightTextPrimary,
    onSecondary = LightTextPrimary,
    onTertiary = LightTextPrimary,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Must remain false to enforce the Leica iconic aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LeicaDarkColorScheme else LeicaDarkColorScheme // Enforce the gorgeous premium dark mode by default

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
