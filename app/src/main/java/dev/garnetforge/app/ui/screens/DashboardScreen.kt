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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*
import java.time.LocalTime

// ── Welcome messages by time block ────────────────────────────────────
private val MORNING_MSGS = listOf(
    "Rise and grind. Your kernel won't tune itself.",
    "Good morning. Coffee first, overclocking second.",
    "Early bird optimises the clock.",
    "Morning! Let's see if the thermals survived the night.",
    "Still half asleep? Your CPU isn't.",
)
private val AFTERNOON_MSGS = listOf(
    "Good afternoon. Thermals look spicy.",
    "Peak hours, peak clocks. You know the drill.",
    "Afternoon check-in — is the governor behaving?",
    "Midday. Perfect time to rethink that max frequency.",
    "Good afternoon. The big cores say hi.",
)
private val EVENING_MSGS = listOf(
    "Evening. Time to dial it back — or don't.",
    "Good evening. The phone deserves a break. Maybe.",
    "Winding down? Your CPU disagrees.",
    "Evening. Let's check if today's tweaks held up.",
    "Sunset clocks. Poetic, yet technically irrelevant.",
)
private val NIGHT_MSGS = listOf(
    "Burning midnight oil? So is core 4.",
    "It's late. The scheduler is judging you.",
    "Night mode: you're using it. The CPU isn't.",
    "Still here? Respect. The big cluster respects you back.",
    "Post-midnight tweaking — a proud GarnetForge tradition.",
)

private fun getWelcomeMessage(): String {
    val h = LocalTime.now().hour
    val pool = when {
        h in 5..11  -> MORNING_MSGS
        h in 12..16 -> AFTERNOON_MSGS
        h in 17..21 -> EVENING_MSGS
        else         -> NIGHT_MSGS
    }
    // Deterministic but changes each minute
    val idx = (LocalTime.now().minute % pool.size)
    return pool[idx]
}

@Composable
fun DashboardScreen(
    stats: LiveStats,
    config: GarnetConfig,
    sconfig: String,
    onClearRam: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Time + welcome message
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stats.timeStr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp)
                Text("GarnetForge · Garnet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Welcome bubble
            val welcome = remember { getWelcomeMessage() }
            Box(
                Modifier
                    .widthIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(welcome,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp)
            }
        }

        SectionHeader("LIVE CLOCK")
        GarnetCard(glowColor = GarnetGlow) {
            FreqBar("Little Cluster (0–3)", stats.cpu0FreqMhz, 691, 1958, ColorGreen)
            Spacer(Modifier.height(14.dp))
            FreqBar("Big Cluster (4–7)",   stats.cpu4FreqMhz, 691, 2400, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(14.dp))
            FreqBar("GPU · Adreno",        stats.gpuFreqMhz,  295, 940,  PurpleLight)
        }

        SectionHeader("STATUS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // CPU temp — use Developer Board icon (more CPU-like)
            StatChip(Icons.Default.DeveloperBoard, "${stats.cpuTempC}°", "CPU",     ColorGreen,  Modifier.weight(1f))
            StatChip(Icons.Default.FlashOn,        "${stats.gpuTempC}°", "GPU",     ColorCool,   Modifier.weight(1f))
            StatChip(Icons.Default.Memory,         "${stats.ddrTempC}°", "DDR",     ColorGold,   Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Default.BatteryFull,    "${stats.battTempC}°","Battery", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatChip(Icons.Default.Storage,        "${stats.freeRamMb}MB","Free RAM",ColorBlue,  Modifier.weight(1f))
            BroomChip(onClearRam, Modifier.weight(1f))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BroomChip(onClick: () -> Unit, modifier: Modifier) {
    var clicked by remember { mutableStateOf(false) }
    val angle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (clicked) 30f else -10f,
        animationSpec = androidx.compose.animation.core.spring(
            androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            androidx.compose.animation.core.Spring.StiffnessMedium),
        finishedListener = { clicked = false }, label = "broom")
    Box(modifier.clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { clicked = true; onClick() },
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CleaningServices, null,
                Modifier.size(18.dp).graphicsLayer(rotationZ = angle), tint = PurpleLight)
            Spacer(Modifier.height(4.dp))
            Text("Clear", style = MaterialTheme.typography.titleMedium, color = PurpleLight, fontWeight = FontWeight.ExtraBold)
            Text("RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
