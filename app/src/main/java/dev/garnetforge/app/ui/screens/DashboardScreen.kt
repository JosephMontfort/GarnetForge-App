package dev.garnetforge.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*

@Composable
fun DashboardScreen(
    stats: LiveStats,
    config: GarnetConfig,
    sconfig: String,
    onClearRam: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Time — use onBackground (black in light, white in dark)
        Column {
            Text(stats.timeStr,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp)
            Text("GarnetForge · Garnet SM7435",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionHeader("LIVE CLOCK")
        GarnetCard(glowColor = GarnetGlow) {
            FreqBar("Little Cluster (0–3) · A510", stats.cpu0FreqMhz, 691, 1958, ColorGreen)
            Spacer(Modifier.height(14.dp))
            FreqBar("Big Cluster (4–7) · A730",   stats.cpu4FreqMhz, 691, 2400, GarnetLight)
            Spacer(Modifier.height(14.dp))
            FreqBar("GPU · Adreno 710",           stats.gpuFreqMhz,  295, 940,  PurpleLight)
        }

        SectionHeader("STATUS")
        // All 6 chips same size using weight — no breathing animations, Material icons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Default.Thermostat,   "${stats.cpuTempC}°", "CPU",     ColorGreen,  Modifier.weight(1f))
            StatChip(Icons.Default.FlashOn,      "${stats.gpuTempC}°", "GPU",     ColorCool,   Modifier.weight(1f))
            StatChip(Icons.Default.Memory,       "${stats.ddrTempC}°", "DDR",     ColorGold,   Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Default.BatteryFull, "${stats.battTempC}°", "Battery", GarnetLight, Modifier.weight(1f))
            StatChip(Icons.Default.Storage,   "${stats.freeRamMb}MB",  "Free RAM",ColorBlue,   Modifier.weight(1f))
            BroomChip(onClearRam, Modifier.weight(1f))
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ── Simple stat chip — no breathing animation, same size as broom chip ─
@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Broom chip — same size as StatChip, spring rotate on tap ───────────
@Composable
private fun BroomChip(onClick: () -> Unit, modifier: Modifier) {
    var clicked by remember { mutableStateOf(false) }
    val angle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (clicked) 30f else -10f,
        animationSpec = androidx.compose.animation.core.spring(
            androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        finishedListener = { clicked = false },
        label = "broom",
    )

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { clicked = true; onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.CleaningServices, null,
                Modifier.size(18.dp).graphicsLayer(rotationZ = angle), tint = PurpleLight)
            Spacer(Modifier.height(4.dp))
            Text("Clear", style = MaterialTheme.typography.titleMedium, color = PurpleLight, fontWeight = FontWeight.ExtraBold)
            Text("RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
