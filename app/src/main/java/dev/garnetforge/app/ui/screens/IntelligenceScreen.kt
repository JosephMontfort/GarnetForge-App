package dev.garnetforge.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

private val PROFILES = listOf("" to "None") +
    ThermalProfile.values().map { it.sconfig to it.label }

@Composable
fun IntelligenceScreen(
    config: GarnetConfig,
    apps: List<ThermalApp>,
    thermalMap: Map<String, String>,
    appsLoading: Boolean,
    onSet: (String, String) -> Unit,
    onLoadApps: () -> Unit,
    onSetProfile: (String, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    // Auto-load on first open when per_app_thermal is on
    LaunchedEffect(config.perAppThermal) {
        if (config.perAppThermal && apps.isEmpty()) onLoadApps()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            // ── Automation toggles ─────────────────────────────────
            item {
                SectionHeader("AUTOMATION")
                GarnetCard(glowColor = if (config.nightMode || config.thermalControl) GarnetGlow else PurpleGlow) {
                    LabeledSwitch(
                        "Screen-Off Save",
                        "Halves clocks, offlines extra cores on screen-off. Restores in <10ms via BroadcastReceiver.",
                        config.nightMode,
                    ) { onSet("night_mode", if (it) "1" else "0") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch(
                        "Charging Control",
                        "Automatically switches to Charging profile (sconfig 32) when charger is connected. Per-App profiles are paused while charging.",
                        config.thermalControl,
                    ) { onSet("thermal_control", if (it) "1" else "0") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch(
                        "Per-App Thermal Profiles",
                        "Custom profile when app comes to foreground. Requires Usage Access permission.",
                        config.perAppThermal,
                    ) { onSet("per_app_thermal", if (it) "1" else "0") }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ── Per-app list (only when enabled) ─────────────────
            if (config.perAppThermal) {
                item {
                    SectionHeader("APP PROFILES")
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        Arrangement.spacedBy(8.dp), Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            Modifier.weight(1f),
                            placeholder = { Text("Search apps…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor    = GarnetLight,
                                unfocusedBorderColor  = BorderCol,
                                focusedContainerColor = CardColor,
                                unfocusedContainerColor = CardColor,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )
                        FilledTonalIconButton(
                            onClick = onLoadApps,
                            colors  = IconButtonDefaults.filledTonalIconButtonColors(CardColor2),
                        ) {
                            if (appsLoading)
                                CircularProgressIndicator(Modifier.size(18.dp), color = GarnetLight, strokeWidth = 2.dp)
                            else
                                Icon(Icons.Default.Refresh, "Refresh", tint = GarnetLight)
                        }
                    }
                }

                if (apps.isEmpty() && !appsLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text("Tap ↻ to load installed apps",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filtered, key = { it.pkg }) { app ->
                        AppProfileRow(app, thermalMap[app.pkg] ?: "", onSetProfile)
                        HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AppProfileRow(app: ThermalApp, assigned: String, onSelect: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = PROFILES.find { it.first == assigned }?.second ?: "None"
    val hasProfile = assigned.isNotEmpty()

    ListItem(
        headlineContent = { Text(app.label, color = if (hasProfile) GarnetLight else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Box {
                AssistChip(
                    onClick = { expanded = true },
                    label   = { Text(label) },
                    border  = AssistChipDefaults.assistChipBorder(true,
                        borderColor = if (hasProfile) GarnetRed.copy(0.5f) else BorderCol),
                    colors  = AssistChipDefaults.assistChipColors(
                        containerColor = if (hasProfile) GarnetDeep.copy(0.3f) else CardColor2,
                        labelColor = if (hasProfile) GarnetLight else MaterialTheme.colorScheme.onSurfaceVariant),
                )
                DropdownMenu(expanded, { expanded = false }) {
                    PROFILES.forEach { (v, l) ->
                        DropdownMenuItem({ Text(l) }, { onSelect(app.pkg, v); expanded = false })
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (hasProfile) GarnetDeep.copy(0.08f) else MaterialTheme.colorScheme.background),
    )
}
