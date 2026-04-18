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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import dev.garnetforge.app.ui.theme.LocalAccent
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

private val CPU_L_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,1958400)
private val CPU_B_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,2054400,2112000,2208000,2304000,2400000)
private val GPU_FB    = listOf(295,345,500,600,650,734,816,875,940)
private val GOVS_I    = listOf("walt","conservative","powersave","performance","schedutil")
private fun <T> List<T>.orFallback(fb: List<T>) = if (size < 2) fb else this
private fun ciI(l: List<Int>, v: Int?) = v?.let { x -> l.indices.minByOrNull { i -> kotlin.math.abs(l[i] - x) } } ?: 0

enum class AppFilter { ALL, WITH_PROFILE, WITHOUT_PROFILE }

@Composable
fun IntelligenceScreen(
    config: GarnetConfig,
    apps: List<AppProfile>,
    presets: List<ProfilePreset>,
    appsLoading: Boolean,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    onSet: (String, String) -> Unit,
    onLoadApps: () -> Unit,
    onSaveProfile: (String, AppProfile?) -> Unit,
    onSavePreset: (ProfilePreset) -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    var query        by remember { mutableStateOf("") }
    var filter       by remember { mutableStateOf(AppFilter.ALL) }
    var editingPkg   by remember { mutableStateOf<String?>(null) }
    var showPresetMgr by remember { mutableStateOf(false) }

    val filtered = remember(apps, query, filter) {
        apps.filter { app ->
            val matchQ = query.isBlank() || app.label.contains(query, true) || app.pkg.contains(query, true)
            val matchF = when (filter) {
                AppFilter.ALL -> true
                AppFilter.WITH_PROFILE -> app.enabled || app.presetId != null || app.cpu0Max != null
                AppFilter.WITHOUT_PROFILE -> !app.enabled && app.presetId == null && app.cpu0Max == null
            }
            matchQ && matchF
        }
    }
    val editingApp = remember(editingPkg, apps) { apps.find { it.pkg == editingPkg } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(config.perAppThermal) {
        if (config.perAppThermal && apps.isEmpty()) onLoadApps()
    }

    if (showPresetMgr) {
        PresetManagerSheet(
            presets = presets,
            availFreqsL = availFreqsL.orFallback(CPU_L_FB),
            availFreqsB = availFreqsB.orFallback(CPU_B_FB),
            availFreqsGpu = availFreqsGpu.orFallback(GPU_FB),
            sheetState = sheetState,
            onDismiss = { showPresetMgr = false },
            onSave = onSavePreset,
            onDelete = onDeletePreset,
        )
    }

    if (editingPkg != null && editingApp != null) {
        val cap = editingApp
        AppProfileSheet(
            app = cap,
            presets = presets,
            availFreqsL = availFreqsL.orFallback(CPU_L_FB),
            availFreqsB = availFreqsB.orFallback(CPU_B_FB),
            availFreqsGpu = availFreqsGpu.orFallback(GPU_FB),
            sheetState = sheetState,
            onDismiss = { saved ->
                if (saved != null) onSaveProfile(cap.pkg, saved)
                editingPkg = null
            },
            onSave = { profile -> onSaveProfile(cap.pkg, profile); editingPkg = null },
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                SectionHeader("AUTOMATION")
                GarnetCard(glowColor = if (config.nightMode || config.thermalControl) GarnetGlow else PurpleGlow) {
                    LabeledSwitch("Screen-Off Save",
                        "Offlines extra cores on screen-off to save battery. Restores on wake.",
                        config.nightMode) { onSet("night_mode", if (it) "1" else "0") }
                    AnimatedVisibility(config.nightMode, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        var showCustomise by remember { mutableStateOf(false) }
                        Column(Modifier.padding(top = 6.dp)) {
                            OutlinedButton(
                                onClick = { showCustomise = !showCustomise },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(if (showCustomise) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (showCustomise) "Hide Customisation" else "Customise")
                            }
                            AnimatedVisibility(showCustomise, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                ScreenOffCustomiser(config = config, onSet = onSet)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch("Charging Control",
                        "Switches to Charging thermal profile when charger connected.",
                        config.thermalControl) { onSet("thermal_control", if (it) "1" else "0") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), 0.5.dp, BorderCol)
                    LabeledSwitch("Per-App Profiles",
                        "Apply custom CPU/GPU/thermal/core profiles per app. Requires Usage Access.",
                        config.perAppThermal) { onSet("per_app_thermal", if (it) "1" else "0") }

                }
                Spacer(Modifier.height(14.dp))
            }

            if (config.perAppThermal) {
                item {
                    SectionHeader("APP PROFILES")
                    // Preset manager row
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Preset Profiles", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${presets.size} preset${if (presets.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = { showPresetMgr = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardColor2)) {
                            Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage")
                        }
                    }

                    // Search + clear
                    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        placeholder = { Text("Search apps…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            Row {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Default.Close, "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FilledTonalIconButton(onClick = onLoadApps,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(CardColor2)) {
                                    if (appsLoading) CircularProgressIndicator(Modifier.size(14.dp),
                                        color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Refresh, "Refresh",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor    = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor  = BorderCol,
                            focusedContainerColor = if (isLight) Color.White else CardColor,
                            unfocusedContainerColor = if (isLight) Color.White else CardColor,
                            focusedTextColor      = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor    = MaterialTheme.colorScheme.onSurface,
                            cursorColor           = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    )

                    // Filter chips
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppFilter.values().forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(when(f) {
                                    AppFilter.ALL -> "All"
                                    AppFilter.WITH_PROFILE -> "With Profile"
                                    AppFilter.WITHOUT_PROFILE -> "Without Profile"
                                }, style = MaterialTheme.typography.bodySmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                ),
                            )
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
                        AppRow(app, presets) { editingPkg = app.pkg }
                        HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: AppProfile, presets: List<ProfilePreset>, onClick: () -> Unit) {
    val presetName = app.presetId?.let { id -> presets.find { it.id == id }?.name }
    // Only show "Saved (inactive)" if the app actually has a saved profile (was explicitly saved before)
    val hasSavedProfile = app.presetId != null || app.cpu0Max != null || app.cpu4Max != null
        || app.gpuMax != null || app.thermal != null || app.offlinedCores.isNotEmpty()
    ListItem(
        headlineContent = {
            Text(app.label,
                color = if (app.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            if (app.enabled && hasSavedProfile) {
                val summary = buildList {
                    presetName?.let { add("Preset: $it") } ?: run {
                        app.thermal?.let { add(ThermalProfile.fromSconfig(it).label) }
                        app.cpu0Max?.let { add("L:${it/1000}M") }
                        app.cpu4Max?.let { add("B:${it/1000}M") }
                        app.gpuMax?.let  { add("GPU:${it}M") }
                        if (app.offlinedCores.isNotEmpty()) add("−${app.offlinedCores.size} cores")
                    }
                }.joinToString(" · ")
                Text(summary.ifEmpty { "Custom" }, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(0.8f))
            } else if (!app.enabled && hasSavedProfile) {
                Text("Saved · inactive", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(app.pkg, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (app.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.15f) else MaterialTheme.colorScheme.background),
    )
}

// ── Per-app profile sheet ─────────────────────────────────────────────
@Composable
private fun AppProfileSheet(
    app: AppProfile,
    presets: List<ProfilePreset>,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    sheetState: SheetState,
    onDismiss: (AppProfile?) -> Unit,  // null = don't save; AppProfile = save this
    onSave: (AppProfile?) -> Unit,
) {
    val context = LocalContext.current
    var enabled    by remember { mutableStateOf(app.enabled) }
    var presetSel  by remember { mutableStateOf(app.presetId) }
    // showCustom: true if no preset linked AND the app has custom settings saved
    val hasCustomSaved = app.presetId == null && (app.cpu0Max != null || app.cpu4Max != null ||
        app.gpuMax != null || app.thermal != null || app.gov0 != null || app.offlinedCores.isNotEmpty())
    var showCustom by remember { mutableStateOf(hasCustomSaved) }
    // Start as true if app already has a profile — so toggle-off on dismiss saves properly
    val alreadyHasProfile = app.enabled || app.presetId != null || hasCustomSaved
    var savedOnce  by remember { mutableStateOf(alreadyHasProfile) }

    var thermal   by remember { mutableStateOf(app.thermal) }
    var gov0      by remember { mutableStateOf(app.gov0) }
    var gov4      by remember { mutableStateOf(app.gov4) }
    var cpu0MinI  by remember { mutableIntStateOf(ciI(availFreqsL, app.cpu0Min)) }
    var cpu0MaxI  by remember { mutableIntStateOf(ciI(availFreqsL, app.cpu0Max ?: availFreqsL.lastOrNull())) }
    var cpu4MinI  by remember { mutableIntStateOf(ciI(availFreqsB, app.cpu4Min)) }
    var cpu4MaxI  by remember { mutableIntStateOf(ciI(availFreqsB, app.cpu4Max ?: availFreqsB.lastOrNull())) }
    var gpuMinI   by remember { mutableIntStateOf(ciI(availFreqsGpu, app.gpuMin)) }
    var gpuMaxI   by remember { mutableIntStateOf(ciI(availFreqsGpu, app.gpuMax ?: availFreqsGpu.lastOrNull())) }
    var offlined  by remember { mutableStateOf(app.offlinedCores) }

    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tRed    = if (isLight) GarnetRed    else MaterialTheme.colorScheme.primary
    val tBlue   = if (isLight) Color(0xFF0288D1) else ColorBlue
    val tPurple = if (isLight) Color(0xFF6A1B9A) else PurpleLight
    val tCool   = if (isLight) Color(0xFF00695C) else ColorCool

    fun buildCustom() = AppProfile(
        pkg = app.pkg, label = app.label, enabled = enabled, presetId = null,
        thermal = thermal, gov0 = gov0, gov4 = gov4,
        cpu0Min = if (cpu0MinI == 0 && app.cpu0Min == null) null else availFreqsL.getOrNull(cpu0MinI),
        cpu0Max = availFreqsL.getOrNull(cpu0MaxI),
        cpu4Min = if (cpu4MinI == 0 && app.cpu4Min == null) null else availFreqsB.getOrNull(cpu4MinI),
        cpu4Max = availFreqsB.getOrNull(cpu4MaxI),
        gpuMin  = if (gpuMinI == 0 && app.gpuMin == null) null else availFreqsGpu.getOrNull(gpuMinI),
        gpuMax  = availFreqsGpu.getOrNull(gpuMaxI),
        offlinedCores = offlined,
    )
    fun buildPreset() = AppProfile(pkg = app.pkg, label = app.label, enabled = enabled, presetId = presetSel)

    ModalBottomSheet(
        onDismissRequest = {
            // Only auto-save on dismiss if user explicitly saved before (to handle toggle-off case)
            // If toggled off: always save disabled state so the profile is marked inactive
            val hadProfile = app.enabled || app.presetId != null || hasCustomSaved
            onDismiss(if (!enabled && hadProfile) (if (presetSel != null && !showCustom) buildPreset() else buildCustom()).copy(enabled = false) else null)
        },
        sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // App header + toggle
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(app.pkg, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (enabled) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        FancyToggle(enabled) { enabled = it }
                    }
                }

                // Only show profile options when toggle is ON
                AnimatedVisibility(enabled, enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                        // Preset / Custom selector
                        Text("Profile Source", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // "None" chip — selected by default if nothing chosen yet
                            ProfileChip("None", presetSel == null && !showCustom) {
                                presetSel = null; showCustom = false
                            }
                            presets.forEach { p ->
                                ProfileChip(p.name, presetSel == p.id && !showCustom) {
                                    presetSel = p.id; showCustom = false
                                }
                            }
                            ProfileChip("Custom", showCustom) {
                                presetSel = null; showCustom = true
                                if (presets.isEmpty())
                                    android.widget.Toast.makeText(context, "No presets configured", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }

                        // Custom sliders — only when Custom explicitly selected
                        AnimatedVisibility(showCustom, enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("Thermal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProfileChip("System", thermal == null) { thermal = null }
                                    ThermalProfile.values().forEach { p ->
                                        ProfileChip(p.label, thermal == p.sconfig) { thermal = p.sconfig }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("Little Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                                SheetSlider("Min", cpu0MinI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size-2).coerceAtLeast(0),
                                    "${availFreqsL.getOrElse(cpu0MinI){0}/1000} MHz", tRed) { cpu0MinI = it.toInt() }
                                SheetSlider("Max", cpu0MaxI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size-2).coerceAtLeast(0),
                                    "${availFreqsL.getOrElse(cpu0MaxI){0}/1000} MHz", tRed) { cpu0MaxI = it.toInt() }
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("Big Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                                SheetSlider("Min", cpu4MinI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size-2).coerceAtLeast(0),
                                    "${availFreqsB.getOrElse(cpu4MinI){0}/1000} MHz", tRed) { cpu4MinI = it.toInt() }
                                SheetSlider("Max", cpu4MaxI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size-2).coerceAtLeast(0),
                                    "${availFreqsB.getOrElse(cpu4MaxI){0}/1000} MHz", tRed) { cpu4MaxI = it.toInt() }
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("GPU", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tPurple)
                                SheetSlider("Min", gpuMinI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size-2).coerceAtLeast(0),
                                    "${availFreqsGpu.getOrElse(gpuMinI){0}} MHz", tPurple) { gpuMinI = it.toInt() }
                                SheetSlider("Max", gpuMaxI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size-2).coerceAtLeast(0),
                                    "${availFreqsGpu.getOrElse(gpuMaxI){0}} MHz", tPurple) { gpuMaxI = it.toInt() }
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("Governor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tBlue)
                                Text("Little", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProfileChip("System", gov0 == null) { gov0 = null }
                                    GOVS_I.forEach { g -> ProfileChip(g, gov0 == g) { gov0 = g } }
                                }
                                Text("Big", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProfileChip("System", gov4 == null) { gov4 = null }
                                    GOVS_I.forEach { g -> ProfileChip(g, gov4 == g) { gov4 = g } }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                                Text("Core Control", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tCool)
                                Text("Core 0 and Core 4 always stay online.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Little:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                                    CoreToggle(0, true, true, context) {}
                                    (1..3).forEach { c -> CoreToggle(c, c !in offlined, false, context) { offlined = if (c in offlined) offlined-c else offlined+c } }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Big:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                                    CoreToggle(4, true, true, context) {}
                                    (5..7).forEach { c -> CoreToggle(c, c !in offlined, false, context) { offlined = if (c in offlined) offlined-c else offlined+c } }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Persistent action bar
            HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSave(null) }, Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text("Clear")
                }
                AnimatedVisibility(enabled, modifier = Modifier.weight(2f),
                    enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Button(onClick = {
                        savedOnce = true
                        // None selected (no preset, no custom) = clear profile
                        val isNoneSelected = presetSel == null && !showCustom
                        onSave(if (isNoneSelected) null else if (presetSel != null) buildPreset() else buildCustom())
                    }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("Save Profile", color = Color.White)
                    }
                }
            }
        }
    }
}

// ── Preset Manager ────────────────────────────────────────────────────
@Composable
private fun PresetManagerSheet(
    presets: List<ProfilePreset>,
    availFreqsL: List<Int>, availFreqsB: List<Int>, availFreqsGpu: List<Int>,
    sheetState: SheetState,
    onDismiss: () -> Unit, onSave: (ProfilePreset) -> Unit, onDelete: (String) -> Unit,
) {
    var editingPreset by remember { mutableStateOf<ProfilePreset?>(null) }
    var showEditor    by remember { mutableStateOf(false) }

    if (showEditor) {
        PresetEditorSheet(
            preset = editingPreset,
            availFreqsL = availFreqsL, availFreqsB = availFreqsB, availFreqsGpu = availFreqsGpu,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { showEditor = false; editingPreset = null },
            onSave = { p -> onSave(p); showEditor = false; editingPreset = null },
        )
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Manage Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { editingPreset = null; showEditor = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            if (presets.isEmpty()) {
                Text("No presets yet. Create one to reuse across multiple apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
            }
            presets.forEach { preset ->
                val parts = buildList {
                    preset.thermal?.let { add(ThermalProfile.fromSconfig(it).label) }
                    preset.cpu0Max?.let { add("L:${it/1000}M") }
                    preset.cpu4Max?.let { add("B:${it/1000}M") }
                    preset.gpuMax?.let  { add("GPU:${it}M") }
                }.joinToString(" · ")
                ListItem(
                    headlineContent = { Text(preset.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { if (parts.isNotEmpty()) Text(parts, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editingPreset = preset; showEditor = true }) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDelete(preset.id) }) {
                                Icon(Icons.Default.Delete, null, tint = GarnetLight)
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

// ── Preset Editor ─────────────────────────────────────────────────────
@Composable
private fun PresetEditorSheet(
    preset: ProfilePreset?,
    availFreqsL: List<Int>, availFreqsB: List<Int>, availFreqsGpu: List<Int>,
    sheetState: SheetState,
    onDismiss: () -> Unit, onSave: (ProfilePreset) -> Unit,
) {
    var name      by remember { mutableStateOf(preset?.name ?: "") }
    var thermal   by remember { mutableStateOf(preset?.thermal) }
    var gov0      by remember { mutableStateOf(preset?.gov0) }
    var gov4      by remember { mutableStateOf(preset?.gov4) }
    var cpu0MinI  by remember { mutableIntStateOf(ciI(availFreqsL, preset?.cpu0Min)) }
    var cpu0MaxI  by remember { mutableIntStateOf(ciI(availFreqsL, preset?.cpu0Max ?: availFreqsL.lastOrNull())) }
    var cpu4MinI  by remember { mutableIntStateOf(ciI(availFreqsB, preset?.cpu4Min)) }
    var cpu4MaxI  by remember { mutableIntStateOf(ciI(availFreqsB, preset?.cpu4Max ?: availFreqsB.lastOrNull())) }
    var gpuMinI   by remember { mutableIntStateOf(ciI(availFreqsGpu, preset?.gpuMin)) }
    var gpuMaxI   by remember { mutableIntStateOf(ciI(availFreqsGpu, preset?.gpuMax ?: availFreqsGpu.lastOrNull())) }
    var offlined  by remember { mutableStateOf(preset?.offlinedCores ?: emptySet()) }
    var nameError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tRed    = if (isLight) GarnetRed    else MaterialTheme.colorScheme.primary
    val tPurple = if (isLight) Color(0xFF6A1B9A) else PurpleLight
    val tBlue   = if (isLight) Color(0xFF0288D1) else ColorBlue
    val tCool   = if (isLight) Color(0xFF00695C) else ColorCool

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (preset == null) "New Preset" else "Edit Preset",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name, onValueChange = { name = it; nameError = false },
                    label = { Text("Preset Name") }, isError = nameError,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        errorBorderColor = GarnetLight,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ))
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("Thermal", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", thermal == null) { thermal = null }
                    ThermalProfile.values().forEach { p -> ProfileChip(p.label, thermal == p.sconfig) { thermal = p.sconfig } }
                }
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("Little Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                SheetSlider("Min", cpu0MinI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size-2).coerceAtLeast(0),
                    "${availFreqsL.getOrElse(cpu0MinI){0}/1000} MHz", tRed) { cpu0MinI=it.toInt() }
                SheetSlider("Max", cpu0MaxI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size-2).coerceAtLeast(0),
                    "${availFreqsL.getOrElse(cpu0MaxI){0}/1000} MHz", tRed) { cpu0MaxI=it.toInt() }
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("Big Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                SheetSlider("Min", cpu4MinI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size-2).coerceAtLeast(0),
                    "${availFreqsB.getOrElse(cpu4MinI){0}/1000} MHz", tRed) { cpu4MinI=it.toInt() }
                SheetSlider("Max", cpu4MaxI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size-2).coerceAtLeast(0),
                    "${availFreqsB.getOrElse(cpu4MaxI){0}/1000} MHz", tRed) { cpu4MaxI=it.toInt() }
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("GPU", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tPurple)
                SheetSlider("Min", gpuMinI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size-2).coerceAtLeast(0),
                    "${availFreqsGpu.getOrElse(gpuMinI){0}} MHz", tPurple) { gpuMinI=it.toInt() }
                SheetSlider("Max", gpuMaxI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size-2).coerceAtLeast(0),
                    "${availFreqsGpu.getOrElse(gpuMaxI){0}} MHz", tPurple) { gpuMaxI=it.toInt() }
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("Governor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tBlue)
                Text("Little", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", gov0 == null) { gov0 = null }
                    GOVS_I.forEach { g -> ProfileChip(g, gov0 == g) { gov0 = g } }
                }
                Text("Big", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", gov4 == null) { gov4 = null }
                    GOVS_I.forEach { g -> ProfileChip(g, gov4 == g) { gov4 = g } }
                }
                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
                Text("Core Control", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tCool)
                Text("Core 0 and Core 4 always stay online.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Little:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                    CoreToggle(0, true, true, context) {}
                    (1..3).forEach { c -> CoreToggle(c, c !in offlined, false, context) { offlined = if (c in offlined) offlined-c else offlined+c } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Big:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                    CoreToggle(4, true, true, context) {}
                    (5..7).forEach { c -> CoreToggle(c, c !in offlined, false, context) { offlined = if (c in offlined) offlined-c else offlined+c } }
                }
                Spacer(Modifier.height(8.dp))
            }
            HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = {
                    if (name.isBlank()) { nameError = true; return@Button }
                    onSave(ProfilePreset(
                        id = preset?.id ?: System.currentTimeMillis().toString(),
                        name = name.trim(),
                        cpu0Min = if (cpu0MinI == 0) null else availFreqsL.getOrNull(cpu0MinI),
                        cpu0Max = availFreqsL.getOrNull(cpu0MaxI),
                        cpu4Min = if (cpu4MinI == 0) null else availFreqsB.getOrNull(cpu4MinI),
                        cpu4Max = availFreqsB.getOrNull(cpu4MaxI),
                        gpuMin  = if (gpuMinI == 0) null else availFreqsGpu.getOrNull(gpuMinI),
                        gpuMax  = availFreqsGpu.getOrNull(gpuMaxI),
                        thermal = thermal, gov0 = gov0, gov4 = gov4,
                        offlinedCores = offlined,
                    ))
                }, Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("Save Preset", color = Color.White)
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────
@Composable
private fun CoreToggle(core: Int, online: Boolean, forced: Boolean, context: android.content.Context, onToggle: () -> Unit) {
    // Forced cores use primaryContainer (accent-aware deep color) so it changes with theme
    val bg = when { forced -> MaterialTheme.colorScheme.primaryContainer; online -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.surfaceContainerHigh }
    val tc = when { forced -> MaterialTheme.colorScheme.onPrimaryContainer; online -> Color.White; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Surface(
        onClick = { if (forced) android.widget.Toast.makeText(context, "Core $core must stay online", android.widget.Toast.LENGTH_SHORT).show() else onToggle() },
        shape = RoundedCornerShape(10.dp), color = bg,
        border = BorderStroke(1.dp, if (forced) MaterialTheme.colorScheme.primaryContainer else if (online) MaterialTheme.colorScheme.primary.copy(0.6f) else MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(40.dp), enabled = true
    ) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("C$core", style = MaterialTheme.typography.bodySmall, color = tc, fontWeight = FontWeight.Bold)
            Text(if (forced || online) "ON" else "OFF", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = tc.copy(0.8f))
        }
    }
}

@Composable
private fun SheetSlider(label: String, value: Float, min: Float, max: Float, steps: Int,
    display: String, tint: Color, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max,
            steps = steps.coerceAtLeast(0), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(0.3f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent))
        Text(display, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
    }
}


// ── Screen-Off Save customiser ────────────────────────────────────────
@Composable
private fun ScreenOffCustomiser(config: GarnetConfig, onSet: (String, String) -> Unit) {
    val accent  = LocalAccent.current
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tAccent = if (isLight) accent.lightPrimary else accent.primary
    val borderC = if (isLight) accent.lightPrimary.copy(0.3f) else accent.primary.copy(0.25f)
    val bgC     = if (isLight) accent.lightCard else accent.darkCard

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, borderC, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bgC)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Little cores dropdown
        CoreCountPicker(
            label       = "Little cores to offline",
            sublabel    = "Cores 1-3 eligible (Core 0 always stays on)",
            selected    = config.screenOffLittleCoresOff,
            max         = 3,
            accentColor = tAccent,
            onSelect    = { onSet("screen_off_little_cores_off", it.toString()) }
        )

        HorizontalDivider(thickness = 0.5.dp, color = borderC)

        // Big cores dropdown
        CoreCountPicker(
            label       = "Big cores to offline",
            sublabel    = "Cores 5-7 eligible (Core 4 always stays on)",
            selected    = config.screenOffBigCoresOff,
            max         = 3,
            accentColor = tAccent,
            onSelect    = { onSet("screen_off_big_cores_off", it.toString()) }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RotateRight, null, Modifier.size(14.dp), tint = tAccent.copy(0.7f))
            Text("Rotation enabled — cores rotate to distribute wear evenly",
                style = MaterialTheme.typography.labelSmall,
                color = tAccent.copy(0.7f))
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderC)

        // GPU max
        Text("GPU max freq while screen off",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = tAccent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement   = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "No change", 295 to "295 MHz", 345 to "345 MHz",
                   500 to "500 MHz", 600 to "600 MHz",
                   650 to "650 MHz", 734 to "734 MHz",
                   816 to "816 MHz", 875 to "875 MHz",
                   940 to "940 MHz").forEach { (mhz, lbl) ->
                FilterChip(
                    selected = config.screenOffGpuMaxMhz == mhz,
                    onClick  = { onSet("screen_off_gpu_max_mhz", mhz.toString()) },
                    label    = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = tAccent,
                        selectedLabelColor     = Color.White),
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderC)

        // Governors — side-by-side two-column layout
        Text("Governor while screen off",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = tAccent)
        val govOpts   = listOf("" to "No change", "walt" to "walt", "conservative" to "conservative",
                               "powersave" to "powersave", "schedutil" to "schedutil")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Little
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Little cluster", style = MaterialTheme.typography.labelSmall, color = tAccent.copy(0.8f))
                govOpts.forEach { (v, lbl) ->
                    FilterChip(
                        selected  = config.screenOffGovLittle == v,
                        onClick   = { onSet("screen_off_gov_little", v) },
                        label     = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                        modifier  = Modifier.fillMaxWidth(),
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tAccent, selectedLabelColor = Color.White),
                    )
                }
            }
            // Big
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Big cluster", style = MaterialTheme.typography.labelSmall, color = tAccent.copy(0.8f))
                govOpts.forEach { (v, lbl) ->
                    FilterChip(
                        selected  = config.screenOffGovBig == v,
                        onClick   = { onSet("screen_off_gov_big", v) },
                        label     = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                        modifier  = Modifier.fillMaxWidth(),
                        colors    = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tAccent, selectedLabelColor = Color.White),
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = borderC)

        // Time window
        LabeledSwitch(
            label    = "Active Time Window Only",
            subtitle = "Only apply screen-off save during a specific time period",
            checked  = config.screenOffTimeEnabled,
            onCheckedChange = { onSet("screen_off_time_enabled", if (it) "1" else "0") }
        )
        AnimatedVisibility(config.screenOffTimeEnabled,
            enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.spacedBy(16.dp)) {
                TimePickerField("Start", config.screenOffTimeStart,
                    { onSet("screen_off_time_start", it.toString()) }, Modifier.weight(1f), tAccent)
                TimePickerField("End", config.screenOffTimeEnd,
                    { onSet("screen_off_time_end", it.toString()) }, Modifier.weight(1f), tAccent)
            }
        }
    }
}

@Composable
private fun CoreCountPicker(
    label: String, sublabel: String, selected: Int, max: Int,
    accentColor: Color, onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = accentColor)
        Text(sublabel, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value         = if (selected == 0) "0 (keep all on)" else "$selected core${if (selected>1)"s" else ""} offline",
                onValueChange = {},
                readOnly      = true,
                label         = null,
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = accentColor.copy(0.4f),
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                ),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (0..max).forEach { n ->
                    DropdownMenuItem(
                        text = { Text(if (n==0) "0 — keep all on" else "$n core${if(n>1)"s" else ""} offline") },
                        onClick = { onSelect(n); expanded = false },
                        colors  = MenuDefaults.itemColors(
                            textColor = if (n == selected) accentColor else MaterialTheme.colorScheme.onSurface
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePickerField(
    label: String, hour: Int, onHourChange: (Int) -> Unit,
    modifier: Modifier, tint: Color,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick = { onHourChange((hour-1+24)%24) },
                modifier = Modifier.size(32.dp)) {
                Text("−", color = tint, fontWeight = FontWeight.Bold)
            }
            Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium,
                color = tint, fontWeight = FontWeight.Bold)
            FilledTonalIconButton(onClick = { onHourChange((hour+1)%24) },
                modifier = Modifier.size(32.dp)) {
                Text("+", color = tint, fontWeight = FontWeight.Bold)
            }
        }
    }
}
