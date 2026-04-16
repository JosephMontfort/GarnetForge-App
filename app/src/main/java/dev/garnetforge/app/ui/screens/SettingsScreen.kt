@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.garnetforge.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import dev.garnetforge.app.data.model.DeviceInfo
import dev.garnetforge.app.data.model.DiagnosticState
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

@Composable
fun SettingsScreen(
    deviceInfo: DeviceInfo,
    themeMode: Int,
    accentTheme: Int,
    blurEnabled: Boolean,
    diagnosticState: dev.garnetforge.app.DiagnosticState,
    onTheme: (Int) -> Unit,
    onAccent: (Int) -> Unit,
    onBlurToggle: (Boolean) -> Unit,
    onRunDiagnostic: () -> Unit,
    onTelegram: () -> Unit,
) {
    var aboutExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionHeader("APPEARANCE")
        GarnetCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(when(themeMode) { 1 -> "Dark"; 2 -> "Light"; else -> "System Default" },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileChip("Auto", themeMode == 0) { onTheme(0) }
                        ProfileChip("Dark", themeMode == 1) { onTheme(1) }
                        ProfileChip("Light", themeMode == 2) { onTheme(2) }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text("Accent Colour", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(ACCENT_THEMES.getOrElse(accentTheme) { ACCENT_THEMES[0] }.name,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ACCENT_THEMES.forEachIndexed { idx, palette ->
                            AccentSwatch(palette = palette, selected = accentTheme == idx) { onAccent(idx) }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                LabeledSwitch("Background Blur", "Glass effect behind expanded tuning cards", blurEnabled, onBlurToggle)
            }
        }

        SectionHeader("ABOUT")
        GarnetCard {
            Row(Modifier.fillMaxWidth().clickable { aboutExpanded = !aboutExpanded }.padding(vertical = 4.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("GarnetForge", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Kernel Manager · v1.0.0", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (aboutExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(aboutExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), 0.5.dp, BorderCol)
                    SubHeader("DEVICE")
                    InfoRow("Device",   deviceInfo.deviceName.ifEmpty { "—" })
                    InfoRow("Codename", deviceInfo.codename.ifEmpty { "—" })
                    InfoRow("SoC",      deviceInfo.socModel.ifEmpty { "—" })
                    InfoRow("Android",  deviceInfo.androidVersion.ifEmpty { "—" })
                    InfoRow("Kernel",   deviceInfo.kernelVersion.ifEmpty { "—" })
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), 0.5.dp, BorderCol)
                    SubHeader("DEVELOPER")
                    Text("montfort_1607", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    // Diagnostic Report button
                    val context = LocalContext.current
                    DiagnosticButton(diagnosticState, onRunDiagnostic) { filePath ->
                        // Share the report file via Intent
                        try {
                            val file = java.io.File(filePath)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, context.packageName + ".provider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "GarnetForge Diagnostic Report")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            // Open Telegram directly if installed, else chooser
                            val telegramPkg = "org.telegram.messenger"
                            val pm = context.packageManager
                            val telegramInstalled = try { pm.getPackageInfo(telegramPkg, 0); true } catch(e: Exception) { false }
                            if (telegramInstalled) {
                                shareIntent.setPackage(telegramPkg)
                                context.startActivity(shareIntent)
                            } else {
                                context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Report"))
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("GarnetForge", "Share failed: ${e.message}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onTelegram).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("@montfort_1607 on Telegram", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AccentSwatch(palette: AccentPalette, selected: Boolean, onClick: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val swatch  = if (isLight) palette.lightPrimary else palette.primary
    Box(contentAlignment = Alignment.Center,
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(palette.primaryLight, swatch)))
            .border(if (selected) 3.dp else 1.5.dp,
                    if (selected) Color.White else Color.White.copy(0.3f), CircleShape)
            .clickable { onClick() }) {
        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable private fun SubHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.2.sp, modifier = Modifier.padding(bottom = 6.dp, top = 4.dp))
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun DiagnosticButton(
    state: dev.garnetforge.app.DiagnosticState,
    onRun: () -> Unit,
    onShare: (String) -> Unit,
) {
    val isRunning = state is dev.garnetforge.app.DiagnosticState.Running
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !isRunning) { if (state is dev.garnetforge.app.DiagnosticState.Done) onShare(state.filePath) else onRun() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.BugReport, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text(
                        when (state) {
                            is dev.garnetforge.app.DiagnosticState.Idle    -> "Generate Diagnostic Report"
                            is dev.garnetforge.app.DiagnosticState.Running -> "Collecting diagnostics…"
                            is dev.garnetforge.app.DiagnosticState.Done    -> "Report ready — tap to share"
                            is dev.garnetforge.app.DiagnosticState.Error   -> "Report failed — retry"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Generates full system log and shares to Telegram",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state is dev.garnetforge.app.DiagnosticState.Done) {
                Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        if (state is dev.garnetforge.app.DiagnosticState.Error) {
            Text("Error: ${state.msg}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
