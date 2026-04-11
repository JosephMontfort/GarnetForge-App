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
