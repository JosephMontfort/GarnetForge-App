@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
package dev.garnetforge.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

private val CPU_L_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,1958400)
private val CPU_B_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,2054400,2112000,2208000,2304000,2400000)
private val GPU_FB    = listOf(295,345,500,600,650,734,816,875,940)
private val GOVS      = listOf("walt","conservative","powersave","performance","schedutil")
private fun <T> List<T>.orFallback(fb: List<T>) = if (isEmpty()) fb else this
private fun ci(l: List<Int>, v: Int?) = v?.let { l.indices.minByOrNull { i -> kotlin.math.abs(l[i] - v) } } ?: 0

@Composable
fun IntelligenceScreen(
    config: GarnetConfig,
    apps: List<AppProfile>,
    appsLoading: Boolean,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    onSet: (String, String) -> Unit,
    onLoadApps: () -> Unit,
    onSaveProfile: (String, AppProfile?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    val editingApp = remember(editingPkg, apps) { apps.find { it.pkg == editingPkg } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(config.perAppThermal) {
        if (config.perAppThermal && apps.isEmpty()) onLoadApps()
    }

    if (editingPkg != null && editingApp != null) {
        ProfileEditorSheet(
            app           = editingApp,
            availFreqsL   = availFreqsL.orFallback(CPU_L_FB),
            availFreqsB   = availFreqsB.orFallback(CPU_B_FB),
            availFreqsGpu = availFreqsGpu.orFallback(GPU_FB),
            sheetState    = sheetState,
            onDismiss     = { editingPkg = null },
            onSave        = { profile -> onSaveProfile(editingApp.pkg, profile); editingPkg = null },
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                SectionHeader("AUTOMATION")
                GarnetCard(glowColor = if (config.nightMode || config.thermalControl) GarnetGlow else PurpleGlow) {
                    LabeledSwitch("Screen-Off Save",
                        "Offlines extra cores on screen-off to save battery. Restores instantly on wake.",
                        config.nightMode) { onSet("night_mode", if (it) "1" else "0") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch("Charging Control",
                        "Switches to Charging thermal profile (sconfig 32) when charger connected.",
                        config.thermalControl) { onSet("thermal_control", if (it) "1" else "0") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch("Per-App Profiles",
                        "Apply custom CPU/GPU/thermal profiles per app. Requires Usage Access permission.",
                        config.perAppThermal) { onSet("per_app_thermal", if (it) "1" else "0") }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (config.perAppThermal) {
                item {
                    SectionHeader("APP PROFILES")
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            Modifier.weight(1f),
                            placeholder = { Text("Search apps…") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GarnetLight, unfocusedBorderColor = BorderCol,
                                focusedContainerColor = CardColor, unfocusedContainerColor = CardColor,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        )
                        FilledTonalIconButton(onClick = onLoadApps,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(CardColor2)) {
                            if (appsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = GarnetLight, strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, "Refresh", tint = GarnetLight)
                        }
                    }
                }

                if (apps.isEmpty() && !appsLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                            Text("Tap ↻ to load installed apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filtered, key = { it.pkg }) { app ->
                        AppRow(app) { editingPkg = app.pkg }
                        HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: AppProfile, onClick: () -> Unit) {
    val hasProfile = app.enabled
    ListItem(
        headlineContent = {
            Text(app.label, color = if (hasProfile) GarnetLight else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            if (hasProfile) {
                val parts = buildList {
                    app.thermal?.let { add(ThermalProfile.fromSconfig(it).label) }
                    app.cpu0Max?.let { add("L:${it/1000}MHz") }
                    app.cpu4Max?.let { add("B:${it/1000}MHz") }
                    app.gpuMax?.let  { add("GPU:${it}MHz") }
                }.joinToString(" · ")
                Text(parts.ifEmpty { "Custom profile" }, style = MaterialTheme.typography.labelSmall, color = GarnetLight.copy(0.8f))
            } else {
                Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = if (hasProfile) GarnetDeep.copy(0.08f) else MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ProfileEditorSheet(
    app: AppProfile,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSave: (AppProfile?) -> Unit,
) {
    var enabled  by remember { mutableStateOf(app.enabled) }
    var thermal  by remember { mutableStateOf(app.thermal) }
    var gov0     by remember { mutableStateOf(app.gov0) }
    var gov4     by remember { mutableStateOf(app.gov4) }
    var cpu0MinI by remember { mutableIntStateOf(ci(availFreqsL, app.cpu0Min)) }
    var cpu0MaxI by remember { mutableIntStateOf(ci(availFreqsL, app.cpu0Max ?: availFreqsL.lastOrNull())) }
    var cpu4MinI by remember { mutableIntStateOf(ci(availFreqsB, app.cpu4Min)) }
    var cpu4MaxI by remember { mutableIntStateOf(ci(availFreqsB, app.cpu4Max ?: availFreqsB.lastOrNull())) }
    var gpuMinI  by remember { mutableIntStateOf(ci(availFreqsGpu, app.gpuMin)) }
    var gpuMaxI  by remember { mutableIntStateOf(ci(availFreqsGpu, app.gpuMax ?: availFreqsGpu.lastOrNull())) }

    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tRed    = if (isLight) GarnetRed else GarnetLight
    val tBlue   = if (isLight) androidx.compose.ui.graphics.Color(0xFF0288D1) else ColorBlue
    val tPurple = if (isLight) androidx.compose.ui.graphics.Color(0xFF6A1B9A) else PurpleLight

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Header
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FancyToggle(enabled) { enabled = it }
            }

            AnimatedVisibility(enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Thermal
                    Text("Thermal Profile", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileChip("System", thermal == null) { thermal = null }
                        ThermalProfile.values().forEach { p -> ProfileChip(p.label, thermal == p.sconfig) { thermal = p.sconfig } }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                    // Little CPU
                    Text("Little Cluster Freq", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                    SliderRow("Min", cpu0MinI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), availFreqsL.lastIndex - 1,
                        "${availFreqsL.getOrElse(cpu0MinI){0}/1000} MHz", tRed) { cpu0MinI = it.toInt() }
                    SliderRow("Max", cpu0MaxI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), availFreqsL.lastIndex - 1,
                        "${availFreqsL.getOrElse(cpu0MaxI){0}/1000} MHz", tRed) { cpu0MaxI = it.toInt() }

                    // Big CPU
                    Text("Big Cluster Freq", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                    SliderRow("Min", cpu4MinI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), availFreqsB.lastIndex - 1,
                        "${availFreqsB.getOrElse(cpu4MinI){0}/1000} MHz", tRed) { cpu4MinI = it.toInt() }
                    SliderRow("Max", cpu4MaxI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), availFreqsB.lastIndex - 1,
                        "${availFreqsB.getOrElse(cpu4MaxI){0}/1000} MHz", tRed) { cpu4MaxI = it.toInt() }

                    // GPU
                    Text("GPU Freq", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tPurple)
                    SliderRow("Min", gpuMinI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), availFreqsGpu.lastIndex - 1,
                        "${availFreqsGpu.getOrElse(gpuMinI){0}} MHz", tPurple) { gpuMinI = it.toInt() }
                    SliderRow("Max", gpuMaxI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), availFreqsGpu.lastIndex - 1,
                        "${availFreqsGpu.getOrElse(gpuMaxI){0}} MHz", tPurple) { gpuMaxI = it.toInt() }

                    // Governors
                    Text("Governor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tBlue)
                    Text("Little", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileChip("System", gov0 == null) { gov0 = null }
                        GOVS.forEach { g -> ProfileChip(g, gov0 == g) { gov0 = g } }
                    }
                    Text("Big", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProfileChip("System", gov4 == null) { gov4 = null }
                        GOVS.forEach { g -> ProfileChip(g, gov4 == g) { gov4 = g } }
                    }
                }
            }

            // Save / Clear
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSave(null) }, Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text("Clear")
                }
                Button(onClick = {
                    val profile = if (!enabled) AppProfile(pkg = app.pkg, label = app.label)
                    else AppProfile(
                        pkg     = app.pkg, label = app.label, enabled = true,
                        thermal = thermal,
                        gov0    = gov0, gov4 = gov4,
                        cpu0Min = if (cpu0MinI == 0 && app.cpu0Min == null) null else availFreqsL.getOrNull(cpu0MinI),
                        cpu0Max = availFreqsL.getOrNull(cpu0MaxI),
                        cpu4Min = if (cpu4MinI == 0 && app.cpu4Min == null) null else availFreqsB.getOrNull(cpu4MinI),
                        cpu4Max = availFreqsB.getOrNull(cpu4MaxI),
                        gpuMin  = if (gpuMinI == 0 && app.gpuMin == null) null else availFreqsGpu.getOrNull(gpuMinI),
                        gpuMax  = availFreqsGpu.getOrNull(gpuMaxI),
                    )
                    onSave(profile)
                }, Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = GarnetRed)) {
                    Text("Save Profile", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String, value: Float, min: Float, max: Float, steps: Int,
    display: String, tint: androidx.compose.ui.graphics.Color,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max,
            steps = steps.coerceAtLeast(0), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(0.3f),
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent))
        Text(display, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
