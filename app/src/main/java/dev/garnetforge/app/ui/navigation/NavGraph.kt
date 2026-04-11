package dev.garnetforge.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard   : Screen("dashboard",    "Dashboard",    Icons.Default.Dashboard)
    object Tuning      : Screen("tuning",       "Tuning",       Icons.Default.Tune)
    object Intelligence: Screen("intelligence", "Automation",   Icons.Default.Psychology)
    object Settings    : Screen("settings",     "Settings",     Icons.Default.Settings)

    companion object { val all = listOf(Dashboard, Tuning, Intelligence, Settings) }
}
