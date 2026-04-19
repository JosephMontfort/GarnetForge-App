package dev.garnetforge.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import dev.garnetforge.app.ui.theme.*

// ── Card ─────────────────────────────────────────────────────────────
@Composable
fun GarnetCard(modifier: Modifier = Modifier, glowColor: Color = Color.Transparent, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier.fillMaxWidth()
            .graphicsLayer()
            .border(1.dp, Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.outlineVariant, Color.Transparent, MaterialTheme.colorScheme.outline),
                Offset.Zero, Offset(400f, 400f),
            ), shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(18.dp),
        content = content,
    )
}

// ── Section header ───────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String) {
    Row(Modifier.padding(start=4.dp, top=6.dp, bottom=8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).background(Brush.verticalGradient(listOf(GarnetLight, GarnetRed)), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp)
    }
}

// ── Freq bar ─────────────────────────────────────────────────────────
@Composable
fun FreqBar(label: String, currentMhz: Int, minMhz: Int, maxMhz: Int, color: Color, modifier: Modifier = Modifier) {
    val frac = if (maxMhz > minMhz) ((currentMhz - minMhz).toFloat() / (maxMhz - minMhz)).coerceIn(0f,1f) else 0f
    val animFrac by animateFloatAsState(frac, spring(0.8f, 120f), label="bar")
    val animMhz by animateIntAsState(currentMhz, tween(300, easing=FastOutSlowInEasing), label="mhz")
    Column(modifier.graphicsLayer()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$animMhz", style=MaterialTheme.typography.bodyMedium, color=color, fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline)) {
            Box(Modifier.fillMaxWidth(animFrac).fillMaxHeight().graphicsLayer().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(.6f), color))))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("$minMhz", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$maxMhz MHz", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Status icon enum ─────────────────────────────────────────────────
enum class StatIcon { Cpu, Gpu, Ram, Battery, Memory }

// ── Animated stat card with pulsing icon ────────────────────────────
@Composable
fun AnimatedStatCard(label: String, value: String, color: Color, icon: StatIcon, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label="statpulse")
    val pulse by inf.animateFloat(.7f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label="pulse")
    Box(modifier.graphicsLayer().clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical=12.dp, horizontal=6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated icon glyph (Unicode emoji with pulse)
            Text(
                text = when(icon) { StatIcon.Cpu->"🔥"; StatIcon.Gpu->"⚡"; StatIcon.Ram->"💾"; StatIcon.Battery->"🔋"; StatIcon.Memory->"🧠" },
                fontSize = (14 * pulse).sp,
                modifier = Modifier.graphicsLayer(alpha = .5f + pulse * .5f)
            )
            Text(value, style=MaterialTheme.typography.titleMedium, color=color, fontWeight=FontWeight.ExtraBold)
            Text(label, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Broom RAM clear card with sweep animation ────────────────────────
@Composable
fun BroomStatCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var sweeping by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(
        if (sweeping) 30f else -10f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        finishedListener = { sweeping = false },
        label = "broom",
    )
    val inter = remember { MutableInteractionSource() }
    Box(modifier.graphicsLayer().clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
        .clickable(inter, null) { sweeping = true; onClick() },
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(vertical=12.dp, horizontal=6.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text("🧹", fontSize=18.sp, modifier=Modifier.graphicsLayer(rotationZ=angle))
            Text("Clear", style=MaterialTheme.typography.titleMedium, color=PurpleLight, fontWeight=FontWeight.ExtraBold)
            Text("RAM", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Profile chip ────────────────────────────────────────────────────
@Composable
fun ProfileChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val inter = remember { MutableInteractionSource() }
    val pressed by inter.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .90f else 1f, spring(Spring.DampingRatioMediumBouncy), label="chip")
    Box(Modifier.graphicsLayer(scaleX=scale, scaleY=scale).clip(CircleShape)
        .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape)
        .background(if (selected) Brush.horizontalGradient(listOf(GarnetDeep, GarnetRed, GarnetLight))
                    else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
        .clickable(inter, null, onClick=onClick).padding(horizontal=18.dp, vertical=9.dp),
        contentAlignment = Alignment.Center) {
        Text(label, style=MaterialTheme.typography.bodyMedium,
            color=if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight=if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ── Fancy toggle ─────────────────────────────────────────────────────
@Composable
fun FancyToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val offset by animateFloatAsState(if (checked) 1f else 0f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label="tog")
    val trackColor by animateColorAsState(if (checked) GarnetRed else MaterialTheme.colorScheme.surfaceContainerHigh, tween(200), label="tc")
    val borderColor by animateColorAsState(if (checked) GarnetLight.copy(.5f) else MaterialTheme.colorScheme.outline, tween(200), label="bc")
    Box(Modifier.graphicsLayer().size(width=52.dp, height=30.dp).clip(CircleShape)
        .border(1.5.dp, borderColor, CircleShape).background(trackColor).clickable { onCheckedChange(!checked) }) {
        val travel = 52.dp - 22.dp - 8.dp
        Box(Modifier.graphicsLayer().padding(start = 4.dp + travel * offset, top = 4.dp).size(22.dp).clip(CircleShape).background(Color.White))
    }
}

// ── Labeled switch ───────────────────────────────────────────────────
@Composable
fun LabeledSwitch(label: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCheckedChange(!checked) }.padding(vertical=10.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=12.dp)) {
            Text(label, style=MaterialTheme.typography.bodyLarge, fontWeight=FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant, lineHeight=17.sp)
        }
        FancyToggle(checked, onCheckedChange)
    }
}

// ── Slider (no info text) ────────────────────────────────────────────
@Composable
fun LabeledSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, onValueChange: (Float) -> Unit, valueLabel: String) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style=MaterialTheme.typography.bodyLarge, fontWeight=FontWeight.Medium)
            AnimatedContent(valueLabel, label="sv",
                transitionSpec={ (slideInVertically{-it}+fadeIn()).togetherWith(slideOutVertically{it}+fadeOut()) }
            ) { Text(it, style=MaterialTheme.typography.bodyMedium, color=GarnetLight, fontWeight=FontWeight.Bold) }
        }
        Slider(value=value, onValueChange=onValueChange, valueRange=valueRange, steps=steps,
            colors=SliderDefaults.colors(thumbColor=GarnetLight, activeTrackColor=GarnetRed,
                inactiveTrackColor=MaterialTheme.colorScheme.outline,
                activeTickColor=Color.Transparent, inactiveTickColor=Color.Transparent))
    }
}

// ── Pulsing dot ──────────────────────────────────────────────────────
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label="p")
    val a by inf.animateFloat(.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label="pa")
    Box(modifier.graphicsLayer(alpha=a).size(7.dp).clip(CircleShape).background(color))
}

// ── Improved Core chip ───────────────────────────────────────────────
@Composable
fun CoreChip(coreNum: Int, online: Boolean, onToggle: () -> Unit) {
    val inter = remember { MutableInteractionSource() }
    val pressed by inter.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .85f else 1f, spring(Spring.DampingRatioMediumBouncy), label="csc")
    val elevation by animateDpAsState(if (online) 4.dp else 0.dp, tween(200), label="cel")
    val bg by animateColorAsState(if (online) GarnetRed else MaterialTheme.colorScheme.surfaceContainerHigh, tween(200), label="cbg")
    val textColor = if (online) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.graphicsLayer(scaleX=scale, scaleY=scale).size(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, if (online) GarnetLight.copy(.7f) else MaterialTheme.colorScheme.outline),
        onClick = onToggle,
    ) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("C$coreNum", style=MaterialTheme.typography.bodyMedium, color=textColor, fontWeight=FontWeight.Bold)
            Text(if (online) "ON" else "OFF", style=MaterialTheme.typography.labelSmall.copy(fontSize=8.sp), color=textColor.copy(.8f))
        }
    }
}
