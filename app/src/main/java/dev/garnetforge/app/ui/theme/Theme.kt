package dev.garnetforge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// CompositionLocal — provides the active accent palette throughout the app
val LocalAccent = compositionLocalOf { ACCENT_THEMES[0] }

private fun buildDarkColors(a: AccentPalette) = darkColorScheme(
    primary              = a.primary,
    onPrimary            = Color.White,
    primaryContainer     = a.primaryDeep,
    onPrimaryContainer   = a.primaryLight,
    secondary            = PurpleAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF3a1545),
    onSecondaryContainer = PurpleLight,
    background           = a.darkBg0,
    surface              = a.darkBg1,
    surfaceVariant       = a.darkCard,
    surfaceContainerHigh = a.darkCard2,
    onBackground         = Color.White,
    onSurface            = Color(0xFFf0e8e9),
    onSurfaceVariant     = Color(0xFFb09499),
    outline              = BorderCol,
    outlineVariant       = BorderCol2,
    error                = a.primaryLight,
    tertiary             = ColorCool,
    tertiaryContainer    = Color(0xFF0d3530),
    onTertiary           = Color.White,
)

private fun buildLightColors(a: AccentPalette) = lightColorScheme(
    primary              = a.lightPrimary,
    onPrimary            = Color.White,
    primaryContainer     = a.lightCard2,
    onPrimaryContainer   = a.primaryDeep,
    secondary            = PurpleAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFecd5f5),
    onSecondaryContainer = Color(0xFF5c1a7a),
    background           = a.lightBg,
    surface              = LightSurface,
    surfaceVariant       = a.lightCard,
    surfaceContainerHigh = a.lightCard2,
    onBackground         = Color(0xFF1a0c0e),
    onSurface            = Color(0xFF1a0c0e),
    onSurfaceVariant     = Color(0xFF8b6770),
    outline              = LightBorder,
    outlineVariant       = Color(0x46000000),
    error                = a.lightPrimary,
    tertiary             = ColorCool,
    tertiaryContainer    = Color(0xFFc8f5ec),
    onTertiary           = Color.White,
)

@Composable
fun GarnetForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val accent = ACCENT_THEMES.getOrElse(accentIndex) { ACCENT_THEMES[0] }
    val colorScheme = if (darkTheme) buildDarkColors(accent) else buildLightColors(accent)

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = GarnetTypography,
            content     = content,
        )
    }
}
