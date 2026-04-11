#!/usr/bin/env python3
"""
GarnetForge theme patch — run from project root (GarnetForge-App/).
Improves color saturation/warmth for both dark and light mode.
Does NOT touch animation code.
"""
import os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))

def write(rel, content):
    path = os.path.join(ROOT, rel)
    with open(path, "w") as f:
        f.write(content)
    print(f"  wrote {rel}")

def patch(rel, replacements):
    path = os.path.join(ROOT, rel)
    with open(path) as f:
        src = f.read()
    for old, new in replacements:
        if old not in src:
            print(f"  WARNING: pattern not found in {rel}: {old[:60]!r}")
        src = src.replace(old, new)
    with open(path, "w") as f:
        f.write(src)
    print(f"  patched {rel}")

# ── Color.kt ──────────────────────────────────────────────────────────
COLOR_KT = """\
package dev.garnetforge.app.ui.theme

import androidx.compose.ui.graphics.Color

// Garnet accent — vivid, works on both dark and light
val GarnetRed    = Color(0xFFc0392b)
val GarnetLight  = Color(0xFFe74c3c)
val GarnetDeep   = Color(0xFF96281b)
val GarnetGlow   = Color(0x44e74c3c)
val PurpleAccent = Color(0xFF9b59b6)
val PurpleLight  = Color(0xFFba8fc9)
val PurpleGlow   = Color(0x2a9b59b6)

// ── Dark palette ──────────────────────────────────────────────────────
val Bg0          = Color(0xFF090608)   // warm-dark near-black (shifted from cold navy)
val Bg1          = Color(0xFF0f0b0d)   // warm dark surface
val CardColor    = Color(0xFF1e1114)   // dark garnet-tinted card
val CardColor2   = Color(0xFF271519)   // slightly lighter warm card
val BorderCol    = Color(0x2affffff)
val BorderCol2   = Color(0x3cffffff)

// ── Light palette ─────────────────────────────────────────────────────
val LightBg      = Color(0xFFfbf0f1)   // very pale warm rose background
val LightSurface = Color(0xFFffffff)
val LightCard    = Color(0xFFfff5f6)   // subtle rose-tinted card
val LightCard2   = Color(0xFFfdeaed)   // more tinted card2
val LightBorder  = Color(0x26000000)

// Status colors — vivid, same in both modes
val ColorGreen      = Color(0xFF22c55e)
val ColorGreenGlow  = Color(0x3322c55e)
val ColorBlue       = Color(0xFF3b9ad9)
val ColorBlueGlow   = Color(0x333b9ad9)
val ColorGold       = Color(0xFFe09b20)
val ColorGoldGlow   = Color(0x33e09b20)
val ColorCool       = Color(0xFF1cb99a)
val ColorCoolGlow   = Color(0x331cb99a)
val ColorPurplePill = Color(0xFF9b59b6)
"""

# ── Theme.kt ──────────────────────────────────────────────────────────
THEME_KT = """\
package dev.garnetforge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary              = GarnetLight,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF4a1510),   // rich deep garnet container
    onPrimaryContainer   = Color(0xFFffc4bb),
    secondary            = PurpleAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF3a1545),   // deep purple container
    onSecondaryContainer = PurpleLight,
    background           = Bg0,
    surface              = Bg1,
    surfaceVariant       = CardColor,
    surfaceContainerHigh = CardColor2,
    onBackground         = Color.White,
    onSurface            = Color(0xFFf0e8e9),   // slightly warm white
    onSurfaceVariant     = Color(0xFFb09499),   // warm rosy-grey (was cold #9999bb)
    outline              = BorderCol,
    outlineVariant       = BorderCol2,
    error                = GarnetLight,
    tertiary             = ColorCool,
    tertiaryContainer    = Color(0xFF0d3530),
    onTertiary           = Color.White,
)

private val LightColors = lightColorScheme(
    primary              = GarnetRed,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFffe0e3),   // warm rose container
    onPrimaryContainer   = Color(0xFF7a0012),
    secondary            = PurpleAccent,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFecd5f5),   // pale purple container
    onSecondaryContainer = Color(0xFF5c1a7a),
    background           = LightBg,
    surface              = LightSurface,
    surfaceVariant       = LightCard,
    surfaceContainerHigh = LightCard2,
    onBackground         = Color(0xFF1a0c0e),   // warm near-black
    onSurface            = Color(0xFF1a0c0e),
    onSurfaceVariant     = Color(0xFF8b6770),   // warm dusty rose (was cold #55557a)
    outline              = LightBorder,
    outlineVariant       = Color(0x46000000),
    error                = GarnetRed,
    tertiary             = ColorCool,
    tertiaryContainer    = Color(0xFFc8f5ec),
    onTertiary           = Color.White,
)

@Composable
fun GarnetForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography  = GarnetTypography,
        content     = content,
    )
}
"""

write("app/src/main/java/dev/garnetforge/app/ui/theme/Color.kt", COLOR_KT)
write("app/src/main/java/dev/garnetforge/app/ui/theme/Theme.kt", THEME_KT)

# ── TuningScreen.kt — patch hardcoded gradients only ─────────────────
patch(
    "app/src/main/java/dev/garnetforge/app/ui/screens/TuningScreen.kt",
    [
        (
            "Brush.verticalGradient(listOf(Color(0xFF232326), Color(0xFF18181A)))",
            "Brush.verticalGradient(listOf(Color(0xFF231318), Color(0xFF160c0f)))",
        ),
        (
            "Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F6)))",
            "Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFfdeaed)))",
        ),
    ]
)

print("\nTheme patch applied successfully.")
