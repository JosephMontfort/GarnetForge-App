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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

private val CPU_L_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,1958400)
private val CPU_B_FB  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,2054400,2112000,2208000,2304000,2400000)
private val GPU_FB    = listOf(295,345,500,600,650,734,816,875,940)
private val GOVS      = listOf("walt","conservative","powersave","performance","schedutil")
private fun <T> List<T>.orFallback(fb: List<T>) = if (isEmpty()) fb else this
private fun ci(l: List<Int>, v: Int?) = v?.let { x -> l.indices.minByOrNull { i -> kotlin.math.abs(l[i] - x) } } ?: 0

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
        val capturedApp = editingApp  // capture for lambda
        ProfileEditorSheet(
            app           = capturedApp,
            availFreqsL   = availFreqsL.orFallback(CPU_L_FB),
            availFreqsB   = availFreqsB.orFallback(CPU_B_FB),
            availFreqsGpu = availFreqsGpu.orFallback(GPU_FB),
            sheetState    = sheetState,
            onDismiss     = { disabledProfile ->
                // If a disabled profile snapshot is provided, persist it
                if (disabledProfile != null) onSaveProfile(capturedApp.pkg, disabledProfile)
                editingPkg = null
            },
            onSave        = { profile -> onSaveProfile(capturedApp.pkg, profile); editingPkg = null },
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
                        "Apply custom CPU/GPU/thermal/core profiles per app. Requires Usage Access.",
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
    val hasSettings = app.cpu0Max != null || app.cpu4Max != null || app.gpuMax != null || app.thermal != null || app.offlinedCores.isNotEmpty()
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(app.label,
                    color = if (app.enabled) GarnetLight else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge)
                if (hasSettings && !app.enabled) {
                    // Show small dot indicating saved-but-disabled profile
                    Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f)))
                }
            }
        },
        supportingContent = {
            if (hasSettings) {
                val parts = buildList {
                    if (!app.enabled) add("off")
                    app.thermal?.let { add(ThermalProfile.fromSconfig(it).label) }
                    app.cpu0Max?.let { add("L:${it/1000}M") }
                    app.cpu4Max?.let { add("B:${it/1000}M") }
                    app.gpuMax?.let  { add("GPU:${it}M") }
                    if (app.offlinedCores.isNotEmpty()) add("cores:−${app.offlinedCores.size}")
                }.joinToString(" · ")
                Text(parts, style = MaterialTheme.typography.labelSmall,
                    color = if (app.enabled) GarnetLight.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = if (app.enabled) GarnetDeep.copy(0.08f) else MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun ProfileEditorSheet(
    app: AppProfile,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    sheetState: SheetState,
    onDismiss: (AppProfile?) -> Unit,
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
    // Core control — which cores to offline. Constraints: keep at least core0 (always), core4 (always)
    // Toggleable: little 1,2,3 and big 5,6,7
    var offlined by remember { mutableStateOf(app.offlinedCores) }

    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tRed    = if (isLight) GarnetRed else GarnetLight
    val tBlue   = if (isLight) androidx.compose.ui.graphics.Color(0xFF0288D1) else ColorBlue
    val tPurple = if (isLight) androidx.compose.ui.graphics.Color(0xFF6A1B9A) else PurpleLight
    val tCool   = if (isLight) androidx.compose.ui.graphics.Color(0xFF00695C) else ColorCool

    fun buildProfile() = AppProfile(
        pkg     = app.pkg, label = app.label, enabled = enabled,
        thermal = thermal, gov0 = gov0, gov4 = gov4,
        cpu0Min = if (cpu0MinI == 0 && app.cpu0Min == null) null else availFreqsL.getOrNull(cpu0MinI),
        cpu0Max = availFreqsL.getOrNull(cpu0MaxI),
        cpu4Min = if (cpu4MinI == 0 && app.cpu4Min == null) null else availFreqsB.getOrNull(cpu4MinI),
        cpu4Max = availFreqsB.getOrNull(cpu4MaxI),
        gpuMin  = if (gpuMinI == 0 && app.gpuMin == null) null else availFreqsGpu.getOrNull(gpuMinI),
        gpuMax  = availFreqsGpu.getOrNull(gpuMaxI),
        offlinedCores = offlined,
    )

    ModalBottomSheet(
        onDismissRequest = {
            // Dismissed without Save: if enabled=false, auto-save disabled state to preserve settings
            onDismiss(if (!enabled) buildProfile() else null)
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {

        // Fixed bottom bar with Save/Clear — always visible
        Column(Modifier.fillMaxWidth()) {
            // Scrollable content
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (enabled) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) GarnetLight else MaterialTheme.colorScheme.onSurfaceVariant)
                        FancyToggle(enabled) { enabled = it }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // Thermal
                Text("Thermal Profile", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", thermal == null) { thermal = null }
                    ThermalProfile.values().forEach { p -> ProfileChip(p.label, thermal == p.sconfig) { thermal = p.sconfig } }
                }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // Little CPU
                Text("Little Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                SheetSliderRow("Min", cpu0MinI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size - 2).coerceAtLeast(0),
                    "${availFreqsL.getOrElse(cpu0MinI){0}/1000} MHz", tRed) { cpu0MinI = it.toInt() }
                SheetSliderRow("Max", cpu0MaxI.toFloat(), 0f, availFreqsL.lastIndex.toFloat(), (availFreqsL.size - 2).coerceAtLeast(0),
                    "${availFreqsL.getOrElse(cpu0MaxI){0}/1000} MHz", tRed) { cpu0MaxI = it.toInt() }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // Big CPU
                Text("Big Cluster", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tRed)
                SheetSliderRow("Min", cpu4MinI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size - 2).coerceAtLeast(0),
                    "${availFreqsB.getOrElse(cpu4MinI){0}/1000} MHz", tRed) { cpu4MinI = it.toInt() }
                SheetSliderRow("Max", cpu4MaxI.toFloat(), 0f, availFreqsB.lastIndex.toFloat(), (availFreqsB.size - 2).coerceAtLeast(0),
                    "${availFreqsB.getOrElse(cpu4MaxI){0}/1000} MHz", tRed) { cpu4MaxI = it.toInt() }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // GPU
                Text("GPU", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tPurple)
                SheetSliderRow("Min", gpuMinI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size - 2).coerceAtLeast(0),
                    "${availFreqsGpu.getOrElse(gpuMinI){0}} MHz", tPurple) { gpuMinI = it.toInt() }
                SheetSliderRow("Max", gpuMaxI.toFloat(), 0f, availFreqsGpu.lastIndex.toFloat(), (availFreqsGpu.size - 2).coerceAtLeast(0),
                    "${availFreqsGpu.getOrElse(gpuMaxI){0}} MHz", tPurple) { gpuMaxI = it.toInt() }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // Governors
                Text("Governor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tBlue)
                Text("Little cluster", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", gov0 == null) { gov0 = null }
                    GOVS.forEach { g -> ProfileChip(g, gov0 == g) { gov0 = g } }
                }
                Text("Big cluster", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileChip("System", gov4 == null) { gov4 = null }
                    GOVS.forEach { g -> ProfileChip(g, gov4 == g) { gov4 = g } }
                }

                HorizontalDivider(thickness = 0.5.dp, color = BorderCol)

                // Core Control
                Text("Core Control", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = tCool)
                Text("Core 0 and Core 4 always stay online.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Little:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                    // Core 0 always on
                    CoreToggleChip(0, true, forced = true) {}
                    (1..3).forEach { c ->
                        CoreToggleChip(c, c !in offlined, forced = false) {
                            offlined = if (c in offlined) offlined - c else offlined + c
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Big:", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).width(44.dp))
                    // Core 4 always on
                    CoreToggleChip(4, true, forced = true) {}
                    (5..7).forEach { c ->
                        CoreToggleChip(c, c !in offlined, forced = false) {
                            offlined = if (c in offlined) offlined - c else offlined + c
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // ── Persistent action bar ──────────────────────────────────────
            HorizontalDivider(thickness = 0.5.dp, color = BorderCol)
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = { onSave(null) }, Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text("Clear")
                }
                // Save Profile only shown when toggle is ON
                AnimatedVisibility(enabled, modifier = Modifier.weight(2f),
                    enter = fadeIn() + expandHorizontally(),
                    exit  = fadeOut() + shrinkHorizontally()) {
                    Button(onClick = { onSave(buildProfile()) }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GarnetRed)) {
                        Text("Save Profile", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreToggleChip(core: Int, online: Boolean, forced: Boolean, onToggle: () -> Unit) {
    val bg = when {
        forced  -> MaterialTheme.colorScheme.surfaceVariant
        online  -> GarnetRed
        else    -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = when {
        forced  -> MaterialTheme.colorScheme.onSurfaceVariant
        online  -> androidx.compose.ui.graphics.Color.White
        else    -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { if (!forced) onToggle() },
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, if (online && !forced) GarnetLight.copy(0.6f) else MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(40.dp),
        enabled = !forced,
    ) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("C$core", style = MaterialTheme.typography.bodySmall, color = textColor, fontWeight = FontWeight.Bold)
            Text(if (forced) "ON" else if (online) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = textColor.copy(0.8f))
        }
    }
}

@Composable
private fun SheetSliderRow(
    label: String, value: Float, min: Float, max: Float, steps: Int,
    display: String, tint: androidx.compose.ui.graphics.Color,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
        Slider(value = value, onValueChange = onChange, valueRange = min..max,
            steps = steps.coerceAtLeast(0), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(0.3f),
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent))
        Text(display, style = MaterialTheme.typography.bodyMedium, color = tint, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(76.dp), textAlign = TextAlign.End)
    }
}
