package dev.garnetforge.app.ui.theme

import androidx.compose.ui.graphics.Color

val GarnetRed    = Color(0xFFc0392b)
val GarnetLight  = Color(0xFFe74c3c)
val GarnetDeep   = Color(0xFF96281b)
val GarnetGlow   = Color(0x44e74c3c)
val PurpleAccent = Color(0xFF9b59b6)
val PurpleLight  = Color(0xFFce93d8)
val PurpleGlow   = Color(0x2a9b59b6)

val Bg0       = Color(0xFF090608)
val Bg1       = Color(0xFF0f0b0d)
val CardColor = Color(0xFF1e1114)
val CardColor2= Color(0xFF271519)
val BorderCol = Color(0x2affffff)
val BorderCol2= Color(0x3cffffff)

val LightBg     = Color(0xFFfbf0f1)
val LightSurface= Color(0xFFffffff)
val LightCard   = Color(0xFFfff5f6)
val LightCard2  = Color(0xFFfdeaed)
val LightBorder = Color(0x26000000)

val ColorGreen     = Color(0xFF00e676); val ColorGreenGlow = Color(0x3300e676)
val ColorBlue      = Color(0xFF29b6f6); val ColorBlueGlow  = Color(0x3329b6f6)
val ColorGold      = Color(0xFFffb300); val ColorGoldGlow  = Color(0x33ffb300)
val ColorCool      = Color(0xFF00e5c3); val ColorCoolGlow  = Color(0x3300e5c3)
val ColorPurplePill= Color(0xFF9b59b6)

data class AccentPalette(
    val name: String,
    val primary: Color, val primaryLight: Color, val primaryDeep: Color,
    val darkBg0: Color, val darkBg1: Color, val darkCard: Color, val darkCard2: Color,
    val lightBg: Color, val lightCard: Color, val lightCard2: Color,
    val lightPrimary: Color,
)

val ACCENT_THEMES = listOf(
    AccentPalette( // 0 — Garnet
        name = "Garnet",
        primary = Color(0xFFe74c3c), primaryLight = Color(0xFFff6b5b), primaryDeep = Color(0xFF5a1810),
        darkBg0  = Color(0xFF120a0b), darkBg1  = Color(0xFF1a0e10),
        darkCard = Color(0xFF2c1518), darkCard2= Color(0xFF38191d),
        lightBg = Color(0xFFfbf0f1), lightCard = Color(0xFFfff5f6), lightCard2 = Color(0xFFfdeaed),
        lightPrimary = Color(0xFFc0392b),
    ),
    AccentPalette( // 1 — Ember
        name = "Ember",
        primary = Color(0xFFf57c00), primaryLight = Color(0xFFff9e33), primaryDeep = Color(0xFF5a2800),
        darkBg0  = Color(0xFF120900), darkBg1  = Color(0xFF1a1000),
        darkCard = Color(0xFF2c1c00), darkCard2= Color(0xFF3a2400),
        lightBg = Color(0xFFfff8f0), lightCard = Color(0xFFfff3e0), lightCard2 = Color(0xFFffe0b2),
        lightPrimary = Color(0xFFe65100),
    ),
    AccentPalette( // 2 — Sapphire
        name = "Sapphire",
        primary = Color(0xFF1e88e5), primaryLight = Color(0xFF64b5f6), primaryDeep = Color(0xFF0d3575),
        darkBg0  = Color(0xFF07090f), darkBg1  = Color(0xFF0c1220),
        darkCard = Color(0xFF132038), darkCard2= Color(0xFF1a2d4e),
        lightBg = Color(0xFFf0f4ff), lightCard = Color(0xFFe8eeff), lightCard2 = Color(0xFFd4e0ff),
        lightPrimary = Color(0xFF0d47a1),
    ),
    AccentPalette( // 3 — Forest
        name = "Forest",
        primary = Color(0xFF43a047), primaryLight = Color(0xFF76d275), primaryDeep = Color(0xFF133a15),
        darkBg0  = Color(0xFF070f07), darkBg1  = Color(0xFF0c160c),
        darkCard = Color(0xFF142414), darkCard2= Color(0xFF1c301c),
        lightBg = Color(0xFFf1f8f0), lightCard = Color(0xFFe8f5e9), lightCard2 = Color(0xFFc8e6c9),
        lightPrimary = Color(0xFF1b5e20),
    ),
    AccentPalette( // 4 — Amethyst
        name = "Amethyst",
        primary = Color(0xFF9c27b0), primaryLight = Color(0xFFce93d8), primaryDeep = Color(0xFF3e0a55),
        darkBg0  = Color(0xFF0b0610), darkBg1  = Color(0xFF130c1c),
        darkCard = Color(0xFF221230), darkCard2= Color(0xFF2e1840),
        lightBg = Color(0xFFfaf0ff), lightCard = Color(0xFFf3e5ff), lightCard2 = Color(0xFFe1bee7),
        lightPrimary = Color(0xFF4a148c),
    ),
    AccentPalette( // 5 — Copper
        name = "Copper",
        primary = Color(0xFFd4692a), primaryLight = Color(0xFFe8924a), primaryDeep = Color(0xFF4d2000),
        darkBg0  = Color(0xFF120800), darkBg1  = Color(0xFF1a0f00),
        darkCard = Color(0xFF2e1a00), darkCard2= Color(0xFF3d2400),
        lightBg = Color(0xFFfef8f0), lightCard = Color(0xFFfff0e0), lightCard2 = Color(0xFFffe0bc),
        lightPrimary = Color(0xFF7a3a00),
    ),
    AccentPalette( // 6 — Teal
        name = "Teal",
        primary = Color(0xFF00acc1), primaryLight = Color(0xFF4dd0e1), primaryDeep = Color(0xFF00404a),
        darkBg0  = Color(0xFF060d0e), darkBg1  = Color(0xFF0a1617),
        darkCard = Color(0xFF112627), darkCard2= Color(0xFF183435),
        lightBg = Color(0xFFf0faf9), lightCard = Color(0xFFe0f5f2), lightCard2 = Color(0xFFb2dfdb),
        lightPrimary = Color(0xFF004d40),
    ),
)
