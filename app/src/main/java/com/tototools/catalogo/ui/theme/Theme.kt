package com.tototools.catalogo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores sacados de la web original
val Brand = Color(0xFF0A7C86)
val BrandDark = Color(0xFF0F2A33)
val Accent = Color(0xFFFFB020)
val Bg = Color(0xFFF2F6F6)
val Card = Color(0xFFFFFFFF)
val Ink = Color(0xFF14212B)
val Muted = Color(0xFF62727D)
val Line = Color(0xFFE1E8EB)
val Soft = Color(0xFFE6F3F4)
val Ok = Color(0xFF15803D)
val Bad = Color(0xFFB91C1C)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color(0xFF3B2300),
    background = Bg,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Soft,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Bad
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Accent,
    background = BrandDark,
    onBackground = Color.White,
    surface = Color(0xFF1A333C),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF243F48),
    onSurfaceVariant = Color(0xFFA8B8C0),
    outline = Color(0xFF3A5560),
    error = Bad
)

@Composable
fun CatalogoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
