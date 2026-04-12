package dev.garnetforge.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Default Garnet accent ─────────────────────────────────────────────
val GarnetRed    = Color(0xFFc0392b)
val GarnetLight  = Color(0xFFe74c3c)
val GarnetDeep   = Color(0xFF96281b)
val GarnetGlow   = Color(0x44e74c3c)
val PurpleAccent = Color(0xFF9b59b6)
val PurpleLight  = Color(0xFFce93d8)
val PurpleGlow   = Color(0x2a9b59b6)

// ── Dark palette ──────────────────────────────────────────────────────
val Bg0          = Color(0xFF090608)
val Bg1          = Color(0xFF0f0b0d)
val CardColor    = Color(0xFF1e1114)
val CardColor2   = Color(0xFF271519)
val BorderCol    = Color(0x2affffff)
val BorderCol2   = Color(0x3cffffff)

// ── Light palette ─────────────────────────────────────────────────────
val LightBg      = Color(0xFFfbf0f1)
val LightSurface = Color(0xFFffffff)
val LightCard    = Color(0xFFfff5f6)
val LightCard2   = Color(0xFFfdeaed)
val LightBorder  = Color(0x26000000)

// ── Status colors ─────────────────────────────────────────────────────
val ColorGreen      = Color(0xFF00e676)
val ColorGreenGlow  = Color(0x3300e676)
val ColorBlue       = Color(0xFF29b6f6)
val ColorBlueGlow   = Color(0x3329b6f6)
val ColorGold       = Color(0xFFffb300)
val ColorGoldGlow   = Color(0x33ffb300)
val ColorCool       = Color(0xFF00e5c3)
val ColorCoolGlow   = Color(0x3300e5c3)
val ColorPurplePill = Color(0xFF9b59b6)

// ── Accent theme palette definitions ─────────────────────────────────
// Each theme: primary, primaryLight, primaryDeep, bg0, bg1, card, card2, lightBg, lightCard, lightCard2

data class AccentPalette(
    val name: String,
    // Dark mode
    val primary: Color,         // main accent (used for sliders, buttons)
    val primaryLight: Color,    // lighter variant
    val primaryDeep: Color,     // dark/deep variant for containers
    val darkBg0: Color,         // darkest background
    val darkBg1: Color,         // surface
    val darkCard: Color,        // card background
    val darkCard2: Color,       // card2 background
    // Light mode
    val lightBg: Color,
    val lightCard: Color,
    val lightCard2: Color,
    val lightPrimary: Color,    // slightly darker for light mode
)

val ACCENT_THEMES = listOf(
    AccentPalette( // 0 — Garnet (default)
        name = "Garnet",
        primary      = Color(0xFFe74c3c), primaryLight = Color(0xFFff6b5b),
        primaryDeep  = Color(0xFF4a1510),
        darkBg0 = Color(0xFF090608), darkBg1 = Color(0xFF0f0b0d),
        darkCard = Color(0xFF1e1114), darkCard2 = Color(0xFF271519),
        lightBg = Color(0xFFfbf0f1), lightCard = Color(0xFFfff5f6), lightCard2 = Color(0xFFfdeaed),
        lightPrimary = Color(0xFFc0392b),
    ),
    AccentPalette( // 1 — Ember (deep amber/orange)
        name = "Ember",
        primary      = Color(0xFFf57c00), primaryLight = Color(0xFFff9e33),
        primaryDeep  = Color(0xFF4a2000),
        darkBg0 = Color(0xFF090600), darkBg1 = Color(0xFF110900),
        darkCard = Color(0xFF1e1400), darkCard2 = Color(0xFF271c00),
        lightBg = Color(0xFFfff8f0), lightCard = Color(0xFFfff3e0), lightCard2 = Color(0xFFffe0b2),
        lightPrimary = Color(0xFFe65100),
    ),
    AccentPalette( // 2 — Sapphire (deep blue)
        name = "Sapphire",
        primary      = Color(0xFF1565c0), primaryLight = Color(0xFF42a5f5),
        primaryDeep  = Color(0xFF0a2a5e),
        darkBg0 = Color(0xFF020509), darkBg1 = Color(0xFF040a14),
        darkCard = Color(0xFF0d1a2e), darkCard2 = Color(0xFF12233d),
        lightBg = Color(0xFFf0f4ff), lightCard = Color(0xFFe8eeff), lightCard2 = Color(0xFFd4e0ff),
        lightPrimary = Color(0xFF0d47a1),
    ),
    AccentPalette( // 3 — Forest (dark green)
        name = "Forest",
        primary      = Color(0xFF2e7d32), primaryLight = Color(0xFF66bb6a),
        primaryDeep  = Color(0xFF0a2e0c),
        darkBg0 = Color(0xFF010900), darkBg1 = Color(0xFF050e03),
        darkCard = Color(0xFF0d1e0a), darkCard2 = Color(0xFF132611),
        lightBg = Color(0xFFf1f8f0), lightCard = Color(0xFFe8f5e9), lightCard2 = Color(0xFFc8e6c9),
        lightPrimary = Color(0xFF1b5e20),
    ),
    AccentPalette( // 4 — Amethyst (deep purple)
        name = "Amethyst",
        primary      = Color(0xFF7b1fa2), primaryLight = Color(0xFFba68c8),
        primaryDeep  = Color(0xFF30084a),
        darkBg0 = Color(0xFF060009), darkBg1 = Color(0xFF0c0214),
        darkCard = Color(0xFF19082a), darkCard2 = Color(0xFF220c38),
        lightBg = Color(0xFFfaf0ff), lightCard = Color(0xFFf3e5ff), lightCard2 = Color(0xFFe1bee7),
        lightPrimary = Color(0xFF4a148c),
    ),
    AccentPalette( // 5 — Copper (bronze/copper metallic)
        name = "Copper",
        primary      = Color(0xFFb85c00), primaryLight = Color(0xFFe07c30),
        primaryDeep  = Color(0xFF3d1e00),
        darkBg0 = Color(0xFF090400), darkBg1 = Color(0xFF100800),
        darkCard = Color(0xFF1e1000), darkCard2 = Color(0xFF2a1600),
        lightBg = Color(0xFFfef8f0), lightCard = Color(0xFFfff0e0), lightCard2 = Color(0xFFffe0bc),
        lightPrimary = Color(0xFF7a3a00),
    ),
    AccentPalette( // 6 — Teal (dark teal/slate)
        name = "Teal",
        primary      = Color(0xFF00897b), primaryLight = Color(0xFF26c6af),
        primaryDeep  = Color(0xFF00352f),
        darkBg0 = Color(0xFF000908), darkBg1 = Color(0xFF001210),
        darkCard = Color(0xFF001e1b), darkCard2 = Color(0xFF002924),
        lightBg = Color(0xFFf0faf9), lightCard = Color(0xFFe0f5f2), lightCard2 = Color(0xFFb2dfdb),
        lightPrimary = Color(0xFF004d40),
    ),
)
