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
import android.net.Uri
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*
import java.time.LocalTime
import kotlinx.coroutines.delay

private val MORNING_MSGS = listOf(
    "Rise and grind. Your kernel won't tune itself.",
    "Good morning. Coffee first, overclocking second.",
    "Early bird optimises the clock.",
    "Still half asleep? Your CPU isn't.",
    "Morning! Hope your thermals are cooler than your attitude.",
    "Another day, another opportunity to blame the scheduler.",
    "Good morning. The governor has been waiting for you.",
    "Fresh boot energy. Let's not waste it.",
    "Morning. Temperatures look stable. Unlike your sleep schedule.",
    "Rise, shine, and check those clock speeds.",
    "Good morning. The walt governor slept well. Did you?",
    "Morning check-in. Everything looks stable. Mostly.",
)
private val AFTERNOON_MSGS = listOf(
    "Peak hours, peak clocks. You know the drill.",
    "Afternoon check-in — is the governor behaving?",
    "Good afternoon. The big cores say hi.",
    "Midday. Perfect time to rethink that max frequency.",
    "Hot outside? Thermals disagree. Or agree. Hard to tell.",
    "Afternoon slump? Tell that to the little cluster.",
    "Still tweaking? Respect. No judgement here.",
    "The SoC is running. You are running. Parallel processing.",
    "Good afternoon. Power levels nominal. Ambitions: excessive.",
    "Half the day gone. Core 4 hasn't even broken a sweat.",
    "Good afternoon. Your thermal profile is judging your choices.",
    "Post-lunch performance window. Make it count.",
)
private val EVENING_MSGS = listOf(
    "Evening. Time to dial it back — or don't.",
    "Good evening. The phone deserves a break. Maybe.",
    "Winding down? Your CPU disagrees.",
    "Evening. Let's check if today's tweaks held up.",
    "Sunset clocks. Poetic, yet technically irrelevant.",
    "One more profile tweak before bed? Just one.",
    "Evening. The scheduler is tired. Are you?",
    "Good evening. ZRAM is still doing its thing silently.",
    "Dusk performance report: surprisingly decent.",
    "Evening. The walt governor has had a long day.",
    "Good evening. Time to audit those frequency caps.",
    "Evening mode. Lower the clocks. Raise the dignity.",
)
private val NIGHT_MSGS = listOf(
    "Burning midnight oil? So is core 4.",
    "It's late. The scheduler is judging you.",
    "Night mode: you're using it. The CPU isn't.",
    "Still here? Respect. The big cluster respects you back.",
    "Post-midnight tweaking — a proud GarnetForge tradition.",
    "Late night debugging. The kernel approves.",
    "Everyone else is asleep. The governor never sleeps.",
    "Night owl mode activated. Thermal profile: spicy.",
    "You and the CPU, both refusing to sleep.",
    "The only light in the room is your phone. And your ambitions.",
    "Deep night. Minimal load. Maximum potential.",
    "3 AM tuning session. No judgement. Only respect.",
)

private fun msgForNow(): String {
    val h = java.time.LocalTime.now().hour
    val pool = when {
        h in 5..11  -> MORNING_MSGS
        h in 12..16 -> AFTERNOON_MSGS
        h in 17..21 -> EVENING_MSGS
        else         -> NIGHT_MSGS
    }
    val min = java.time.LocalTime.now().minute
    return pool[min % pool.size]
}

@Composable
fun DashboardScreen(
    stats: LiveStats,
    config: GarnetConfig,
    sconfig: String,
    coreStates: List<Boolean>,
    onClearRam: () -> Unit,
) {
    // Rotate message every 60 seconds
    var welcome by remember { mutableStateOf(msgForNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            welcome = msgForNow()
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // Time + welcome message — message fills remaining width
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp), Alignment.Top) {
            Column {
                Text(stats.timeStr, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp)
                Text("GarnetForge · Garnet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(welcome, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            }
        }

        SectionHeader("LIVE CLOCK")
        GarnetCard(glowColor = GarnetGlow) {
            val bigCoresAllOff = (4..7).all { !coreStates.getOrElse(it) { true } }
            FreqBar("Little Cluster (0–3)", stats.cpu0FreqMhz, 691, 1958, ColorGreen)
            Spacer(Modifier.height(14.dp))
            if (bigCoresAllOff) {
                FreqBar("Big Cluster (4–7) · offline", 0, 0, 1, MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FreqBar("Big Cluster (4–7)", stats.cpu4FreqMhz, 691, 2400, MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(14.dp))
            FreqBar("GPU · Adreno", stats.gpuFreqMhz, 295, 940, PurpleLight)
        }

        SectionHeader("STATUS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Default.DeveloperBoard, "${stats.cpuTempC}°", "CPU",     ColorGreen,  Modifier.weight(1f))
            StatChip(Icons.Default.FlashOn,        "${stats.gpuTempC}°", "GPU",     ColorCool,   Modifier.weight(1f))
            StatChip(Icons.Default.Memory,         "${stats.ddrTempC}°", "DDR",     ColorGold,   Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(Icons.Default.BatteryFull, "${stats.battTempC}°", "Battery", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatChip(Icons.Default.Storage, "${stats.freeRamMb}MB", "Free RAM", ColorBlue, Modifier.weight(1f))
            BroomChip(onClearRam, Modifier.weight(1f))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.ExtraBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BroomChip(onClick: () -> Unit, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val mp = remember {
        try {
            android.media.MediaPlayer().also { player ->
                val afd = context.assets.openFd("trash_clean.webm")
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                player.isLooping = false
                player.prepare()
            }
        } catch (_: Exception) { null }
    }
    DisposableEffect(Unit) { onDispose { mp?.release() } }

    Box(modifier.clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable {
            mp?.let { player -> if (!player.isPlaying) { player.seekTo(0); player.start() } }
            onClick()
        }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(42.dp)) {
                AndroidView(
                    factory = { ctx ->
                        android.view.SurfaceView(ctx).also { sv ->
                            sv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            sv.setZOrderOnTop(true)
                            sv.holder.setFormat(android.graphics.PixelFormat.TRANSPARENT)
                            sv.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(h: android.view.SurfaceHolder) {
                                    mp?.setSurface(h.surface)
                                }
                                override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, ht: Int) {}
                                override fun surfaceDestroyed(h: android.view.SurfaceHolder) { mp?.setSurface(null) }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text("Clear", style = MaterialTheme.typography.titleSmall, color = PurpleLight, fontWeight = FontWeight.ExtraBold)
            Text("RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
