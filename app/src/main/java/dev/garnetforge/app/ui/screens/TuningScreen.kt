@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalAnimationApi::class
)
package dev.garnetforge.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.ui.components.*
import dev.garnetforge.app.ui.theme.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// ── Data ─────────────────────────────────────────────────────────────
private val CPU_L  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,1958400)
private val CPU_B  = listOf(691200,960000,1190400,1344000,1497600,1651200,1900800,2054400,2112000,2208000,2304000,2400000)
private val GPU_S  = listOf(295,345,500,600,650,734,816,875,940)
private val GOVS   = listOf("walt","conservative","powersave","performance","schedutil")
private val ZRAM_B = listOf(1L,2L,3L,4L).map { it * 1_073_741_824L }
private val ZRAM_ALGOS = listOf("lz4", "lzo", "zstd", "lzo-rle")
private val READ_AHEAD_VALS = listOf(64, 128, 256, 512, 1024, 2048)
private fun ci(l: List<Int>, v: Int) = l.indices.minByOrNull { kotlin.math.abs(l[it] - v) } ?: 0

// ── Section definitions ───────────────────────────────────────────────
private data class TuningSection(val id: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
private val SECTIONS = listOf(
    TuningSection("cpu_little", Icons.Default.Memory,       "Little Cluster"),
    TuningSection("cpu_big",    Icons.Default.Speed,        "Big Cluster"),
    TuningSection("core",       Icons.Default.ViewModule,   "Core Control"),
    TuningSection("gpu",        Icons.Default.Gamepad,      "GPU"),
    TuningSection("memory",     Icons.Default.Storage,      "Memory"),
    TuningSection("thermal",    Icons.Default.Thermostat,   "Thermal"),
    TuningSection("io",         Icons.Default.Tune,         "I/O"),
    TuningSection("network",    Icons.Default.NetworkCheck, "Network"),
)

// ── Chip position capture ─────────────────────────────────────────────
private data class ChipBounds(val offset: IntOffset, val size: IntSize)

// ── Main composable ───────────────────────────────────────────────────
@Composable
fun TuningScreen(
    config: GarnetConfig,
    sconfig: String,
    coreStates: List<Boolean>,
    perCoreFreqMhz: List<Int>,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    liveNodes: dev.garnetforge.app.data.model.LiveNodeValues,
    nodeDefaults: dev.garnetforge.app.data.model.NodeDefaults,
    blurEnabled: Boolean,
    onSet: (String, String) -> Unit,
    onProfileSelected: (ThermalProfile) -> Unit,
    onToggleCore: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f

    var targetExpandedId by remember { mutableStateOf<String?>(null) }
    val activeOverlayIds = remember { mutableStateListOf<String>() }
    val currentBounds = remember { mutableStateMapOf<String, ChipBounds>() }
    var parentOffset by remember { mutableStateOf(Offset.Zero) }

    val anyExpanded = targetExpandedId != null
    val backgroundBlur by animateDpAsState(
        targetValue = if (anyExpanded && blurEnabled) 12.dp else 0.dp,
        animationSpec = tween(400), label = "blur"
    )

    val accent = LocalAccent.current
    val cardBg = if (isLight) {
        Brush.verticalGradient(listOf(Color.White, accent.lightCard2))
    } else {
        Brush.verticalGradient(listOf(accent.darkCard, accent.darkBg0))
    }
    val borderColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f)
    val adaptiveTint = if (isLight) GarnetRed else GarnetLight

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).onGloballyPositioned { parentOffset = it.positionInRoot() }) {
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .blur(backgroundBlur)
        ) {
            items(SECTIONS, key = { it.id }) { section ->
                val isExpanded = activeOverlayIds.contains(section.id)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            currentBounds[section.id] = ChipBounds(
                                IntOffset(pos.x.toInt() - parentOffset.x.toInt(), pos.y.toInt() - parentOffset.y.toInt()),
                                coords.size
                            )
                        }
                        .graphicsLayer { alpha = if (isExpanded) 0f else 1f }
                        .shadow(if (isLight) 2.dp else 0.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        .clickable {
                            if (!isExpanded) {
                                activeOverlayIds.add(section.id)
                            }
                            targetExpandedId = section.id
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(adaptiveTint.copy(alpha = if (isLight) 0.12f else 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(section.icon, null, modifier = Modifier.size(26.dp), tint = adaptiveTint)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = section.label, 
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        activeOverlayIds.forEach { id ->
            key(id) {
                val zIndex = if (targetExpandedId == id) 100f else 1f
                Box(Modifier.zIndex(zIndex).fillMaxSize()) {
                    HeroOverlay(
                        sectionId   = id,
                        startBoundsProvider = { currentBounds[id] },
                        isExpanded  = (targetExpandedId == id),
                        config      = config,
                        sconfig     = sconfig,
                        coreStates  = coreStates,
                        perCoreFreqMhz = perCoreFreqMhz,
                        availFreqsL = availFreqsL,
                        availFreqsB = availFreqsB,
                        availFreqsGpu = availFreqsGpu,
                        liveNodes   = liveNodes,
                        nodeDefaults= nodeDefaults,
                        onSet       = onSet,
                        onProfileSelected = onProfileSelected,
                        onToggleCore= onToggleCore,
                        onDismissRequest = { if (targetExpandedId == id) targetExpandedId = null },
                        onFullyClosed    = { activeOverlayIds.remove(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroOverlay(
    sectionId: String,
    startBoundsProvider: () -> ChipBounds?,
    isExpanded: Boolean,
    config: GarnetConfig,
    sconfig: String,
    coreStates: List<Boolean>,
    perCoreFreqMhz: List<Int>,
    availFreqsL: List<Int>,
    availFreqsB: List<Int>,
    availFreqsGpu: List<Int>,
    liveNodes: dev.garnetforge.app.data.model.LiveNodeValues,
    nodeDefaults: dev.garnetforge.app.data.model.NodeDefaults,
    onSet: (String, String) -> Unit,
    onProfileSelected: (ThermalProfile) -> Unit,
    onToggleCore: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onFullyClosed: () -> Unit,
) {
    val density = LocalDensity.current
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    
    var internalExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isExpanded) { internalExpanded = isExpanded }

    var dragY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val DISMISS_THRESHOLD = 150f

    val transition = updateTransition(targetState = internalExpanded, label = "hero")

    fun triggerDismiss() {
        dragY = 0f
        onDismissRequest()
    }

    BackHandler(enabled = isExpanded) { triggerDismiss() }

    val scrimAlpha by transition.animateFloat(transitionSpec = { if (targetState) tween(550) else tween(350) }, label = "scrim") { if (it) 0.65f else 0f }
    val rotationY by transition.animateFloat(transitionSpec = { if (targetState) tween(550, easing = FastOutSlowInEasing) else tween(350, easing = FastOutSlowInEasing) }, label = "rotY") { if (it) 180f else 0f }

    val startBounds = startBoundsProvider() ?: return
    val startLeft   = startBounds.offset.x.toFloat()
    val startTop    = startBounds.offset.y.toFloat()
    val startWidth  = startBounds.size.width.toFloat()
    val startHeight = startBounds.size.height.toFloat()

    val showBack = rotationY > 90f

    val snapTranslateY by animateFloatAsState(
        targetValue = if (isDragging) dragY else 0f,
        animationSpec = if (isDragging) snap() else spring(0.85f, Spring.StiffnessMediumLow), label = "snapY",
    )
    val dragScale = if (isDragging) (1f - (kotlin.math.abs(dragY) / 3000f)).coerceIn(0.85f, 1f) else 1f
    val snapScale by animateFloatAsState(
        targetValue = dragScale,
        animationSpec = if (isDragging) snap() else spring(0.85f, Spring.StiffnessMediumLow), label = "snapScale",
    )

    LaunchedEffect(transition.currentState, internalExpanded) {
        if (!internalExpanded && transition.currentState == false) {
            onFullyClosed()
        }
    }

    val scrimModifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)).then(
        if (isExpanded) Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { triggerDismiss() } else Modifier
    )

    Box(scrimModifier)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()

        val targetW = fullW * 0.88f
        val targetH = with(density) {
            when(sectionId) {
                "thermal"  -> 420.dp.toPx()
                "io"       -> 440.dp.toPx()
                "network"  -> 440.dp.toPx()
                "memory"   -> 560.dp.toPx()
                "gpu"      -> 460.dp.toPx()
                "core"     -> 400.dp.toPx()
                else       -> 620.dp.toPx()  // cpu_little / cpu_big with graphs
            }
        }.coerceAtMost(fullH * 0.95f)

        val targetL = (fullW - targetW) / 2f
        val targetT = (fullH - targetH) / 2f

        val animWidth by transition.animateFloat(label = "w", transitionSpec = { if (targetState) spring(0.75f, Spring.StiffnessLow) else spring(0.7f, Spring.StiffnessMediumLow) }) { if (it) targetW else startWidth }
        val animHeight by transition.animateFloat(label = "h", transitionSpec = { if (targetState) spring(0.75f, Spring.StiffnessLow) else spring(0.7f, Spring.StiffnessMediumLow) }) { if (it) targetH else startHeight }
        val left by transition.animateFloat(label = "left", transitionSpec = { if (targetState) spring(0.75f, Spring.StiffnessLow) else spring(0.75f, Spring.StiffnessMediumLow) }) { if (it) targetL else startLeft }
        val top by transition.animateFloat(label = "top", transitionSpec = { if (targetState) spring(0.75f, Spring.StiffnessLow) else spring(0.75f, Spring.StiffnessMediumLow) }) { if (it) targetT else startTop }

        val accent2 = LocalAccent.current
        val cardBg = if (isLight) {
            Brush.verticalGradient(listOf(Color.White, accent2.lightCard2))
        } else {
            Brush.verticalGradient(listOf(accent2.darkCard, accent2.darkBg0))
        }
        val borderColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f)
        val closeIconTint = if (isLight) Color.Black else Color.White
        val closeIconBg = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
        val adaptiveTint = if (isLight) GarnetRed else GarnetLight

        val overlayContent = @Composable {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 16.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = { triggerDismiss() },
                        modifier = Modifier.background(closeIconBg, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = closeIconTint, modifier = Modifier.size(18.dp))
                    }
                }
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SectionContent(sectionId, config, sconfig, coreStates, perCoreFreqMhz, availFreqsL, availFreqsB, availFreqsGpu, liveNodes, nodeDefaults, onSet, onProfileSelected, onToggleCore)
                    Spacer(Modifier.height(40.dp))
                }
            }
        }

        // LAYER 1: The 3D Shell (Visual only)
        Box(
            modifier = Modifier
                .offset { IntOffset(0, 0) }
                .size(
                    width  = with(density) { animWidth.toDp() },
                    height = with(density) { animHeight.toDp() },
                )
                .graphicsLayer(
                    rotationY       = rotationY,
                    cameraDistance  = 12f * density.density,
                    scaleX          = snapScale,
                    scaleY          = snapScale,
                    translationX    = left,
                    translationY    = top + snapTranslateY,
                )
                .shadow(if (isLight) { if (internalExpanded) 16.dp else 2.dp } else 0.dp, RoundedCornerShape(if (internalExpanded) 32.dp else 20.dp))
                .clip(RoundedCornerShape(if (internalExpanded) 32.dp else 20.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(if (internalExpanded) 32.dp else 20.dp))
        ) {
            if (!showBack) {
                val section = SECTIONS.find { it.id == sectionId }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    Box(Modifier.requiredSize(with(density) { startWidth.toDp() }, with(density) { startHeight.toDp() }), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, 
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(adaptiveTint.copy(alpha = if (isLight) 0.12f else 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(section?.icon ?: Icons.Default.Memory, null, modifier = Modifier.size(26.dp), tint = adaptiveTint)
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = section?.label ?: "", 
                                style = MaterialTheme.typography.bodyLarge, 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                val isAnimationFinished = (rotationY >= 179.9f) && isExpanded
                val ghostAlpha = if (isAnimationFinished) 0f else 1f
                Box(Modifier.fillMaxSize().graphicsLayer(rotationY = 180f, cameraDistance = 12f * density.density, alpha = ghostAlpha), contentAlignment = Alignment.Center) {
                    Box(Modifier.requiredSize(with(density) { targetW.toDp() }, with(density) { targetH.toDp() })) {
                        overlayContent()
                    }
                }
            }
        }

        // LAYER 2: The Flat Core (Touch accurate)
        if (showBack) {
            val isAnimationFinished = (rotationY >= 179.9f) && isExpanded
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.toInt(), (top + snapTranslateY).toInt()) }
                    .size(
                        width  = with(density) { animWidth.toDp() },
                        height = with(density) { animHeight.toDp() },
                    )
                    .graphicsLayer(
                        scaleX = snapScale,
                        scaleY = snapScale,
                        alpha = if (isAnimationFinished) 1f else 0f
                    )
                    .shadow(if (isLight) 16.dp else 0.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(cardBg)
                    .border(1.dp, borderColor, RoundedCornerShape(32.dp))
                    .pointerInput(isExpanded) {
                        if (!isExpanded) return@pointerInput
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true; dragY = 0f },
                            onDragEnd = {
                                isDragging = false
                                if (kotlin.math.abs(dragY) > DISMISS_THRESHOLD) triggerDismiss() else dragY = 0f
                            },
                            onDragCancel = { isDragging = false; dragY = 0f },
                            onVerticalDrag = { _, delta -> dragY = (dragY + delta).coerceIn(-fullH, fullH) },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val nestedScrollConnection = remember {
                    object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                        var overscrollAccum = 0f
                        val OVERSCROLL_SLOP = 40f
                        var hasScrolledThisGesture = false

                        override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                            if (isDragging) { dragY = (dragY + available.y).coerceIn(-fullH, fullH); return available }
                            return Offset.Zero
                        }
                        override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                            if (consumed.y != 0f) hasScrolledThisGesture = true
                            if (available.y != 0f && !hasScrolledThisGesture) {
                                if (overscrollAccum != 0f && (available.y * overscrollAccum < 0f)) overscrollAccum = 0f
                                overscrollAccum += available.y
                                if (kotlin.math.abs(overscrollAccum) >= OVERSCROLL_SLOP) {
                                    isDragging = true
                                    dragY = (dragY + available.y).coerceIn(-fullH, fullH)
                                }
                                return Offset(0f, available.y)
                            } else {
                                overscrollAccum = 0f
                            }
                            return Offset.Zero
                        }
                        override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            overscrollAccum = 0f
                            hasScrolledThisGesture = false
                            if (isDragging) { isDragging = false; if (kotlin.math.abs(dragY) > DISMISS_THRESHOLD) triggerDismiss() else dragY = 0f; return available }
                            return androidx.compose.ui.unit.Velocity.Zero
                        }
                        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                            overscrollAccum = 0f
                            hasScrolledThisGesture = false
                            if (isDragging) { isDragging = false; if (kotlin.math.abs(dragY) > DISMISS_THRESHOLD) triggerDismiss() else dragY = 0f; return available }
                            return androidx.compose.ui.unit.Velocity.Zero
                        }
                    }
                }
                
                Box(Modifier.requiredSize(with(density) { targetW.toDp() }, with(density) { targetH.toDp() }).nestedScroll(nestedScrollConnection).verticalScroll(rememberScrollState())) {
                     overlayContent()
                }
            }
        }
    }
}

// ── Section content per id ─────────────────────────────────────────────
@Composable
private fun SectionContent(
    id: String, config: GarnetConfig, sconfig: String,
    coreStates: List<Boolean>, perCoreFreqMhz: List<Int>,
    availFreqsL: List<Int>, availFreqsB: List<Int>, availFreqsGpu: List<Int>,
    liveNodes: dev.garnetforge.app.data.model.LiveNodeValues,
    nodeDefaults: dev.garnetforge.app.data.model.NodeDefaults,
    onSet: (String,String)->Unit,
    onProfileSelected: (ThermalProfile)->Unit, onToggleCore: (Int)->Unit,
) {
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val tRed    = if (isLight) GarnetRed    else GarnetLight
    val tBlue   = if (isLight) Color(0xFF0288D1) else ColorBlue
    val tGreen  = if (isLight) Color(0xFF2E7D32) else ColorGreen
    val tGold   = if (isLight) Color(0xFFF57F17) else ColorGold
    val tCool   = if (isLight) Color(0xFF00695C) else ColorCool
    val tPurple = if (isLight) Color(0xFF6A1B9A) else PurpleLight
    val textSub = MaterialTheme.colorScheme.onSurfaceVariant

    when (id) {
        "cpu_little" -> {
            val freqL = availFreqsL.ifEmpty { CPU_L }
            val maxMhzL = freqL.lastOrNull()?.div(1000) ?: 1958
            Text("Little Cluster", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tRed)
            Spacer(Modifier.height(4.dp))
            CoreFreqGrid(cores = (0..3).toList(), freqs = perCoreFreqMhz, maxFreq = maxMhzL, color = tRed)
            Spacer(Modifier.height(8.dp))
            ChipRowTuning("Governor", GOVS, config.cpuPolicy0Governor,
                info = "Frequency scaling algorithm. walt = best for interactive use.") { onSet("cpu_policy0_governor", it) }
            Spacer(Modifier.height(8.dp))
            var minI by remember(config.cpuPolicy0Min, freqL) { mutableIntStateOf(ci(freqL, config.cpuPolicy0Min)) }
            var maxI by remember(config.cpuPolicy0Max, freqL) { mutableIntStateOf(ci(freqL, config.cpuPolicy0Max)) }
            RevertableSlider("Min Frequency", minI.toFloat(), 0f, freqL.lastIndex.toFloat(), (freqL.size-2).coerceAtLeast(0),
                "${freqL.getOrElse(minI){0}/1000} MHz", tRed, { minI=it.toInt() },
                onRevert = { minI = 0; onSet("cpu_policy0_min", freqL.first().toString()) },
                info = "Minimum allowed frequency. Lower = better idle battery life.",
            ) { onSet("cpu_policy0_min", freqL[minI].toString()) }
            RevertableSlider("Max Frequency", maxI.toFloat(), 0f, freqL.lastIndex.toFloat(), (freqL.size-2).coerceAtLeast(0),
                "${freqL.getOrElse(maxI){0}/1000} MHz", tRed, { maxI=it.toInt() },
                onRevert = { maxI = freqL.lastIndex; onSet("cpu_policy0_max", freqL.last().toString()) },
                info = "Maximum allowed frequency. Limit to save battery or thermals.",
            ) { onSet("cpu_policy0_max", freqL[maxI].toString()) }
        }
        "cpu_big" -> {
            val freqB = availFreqsB.ifEmpty { CPU_B }
            val maxMhzB = freqB.lastOrNull()?.div(1000) ?: 2400
            Text("Big Cluster", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tRed)
            Spacer(Modifier.height(4.dp))
            CoreFreqGrid(cores = (4..7).toList(), freqs = perCoreFreqMhz, maxFreq = maxMhzB, color = tRed)
            Spacer(Modifier.height(8.dp))
            ChipRowTuning("Governor", GOVS, config.cpuPolicy4Governor,
                info = "Frequency scaling algorithm. walt = best for interactive use.") { onSet("cpu_policy4_governor", it) }
            Spacer(Modifier.height(8.dp))
            var minI by remember(config.cpuPolicy4Min, freqB) { mutableIntStateOf(ci(freqB, config.cpuPolicy4Min)) }
            var maxI by remember(config.cpuPolicy4Max, freqB) { mutableIntStateOf(ci(freqB, config.cpuPolicy4Max)) }
            RevertableSlider("Min Frequency", minI.toFloat(), 0f, freqB.lastIndex.toFloat(), (freqB.size-2).coerceAtLeast(0),
                "${freqB.getOrElse(minI){0}/1000} MHz", tRed, { minI=it.toInt() },
                onRevert = { minI = 0; onSet("cpu_policy4_min", freqB.first().toString()) },
                info = "Minimum allowed frequency for big cores.",
            ) { onSet("cpu_policy4_min", freqB[minI].toString()) }
            RevertableSlider("Max Frequency", maxI.toFloat(), 0f, freqB.lastIndex.toFloat(), (freqB.size-2).coerceAtLeast(0),
                "${freqB.getOrElse(maxI){0}/1000} MHz", tRed, { maxI=it.toInt() },
                onRevert = { maxI = freqB.lastIndex; onSet("cpu_policy4_max", freqB.last().toString()) },
                info = "Maximum frequency for big cores. Limit for thermals.",
            ) { onSet("cpu_policy4_max", freqB[maxI].toString()) }
        }
        "core" -> {
            Text("Core Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tRed)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Little (0–3)", color = textSub)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..3).forEach { c -> CoreChip(c, coreStates.getOrElse(c){true}) { onToggleCore(c) } }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Big (4–7)", color = textSub)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (4..7).forEach { c -> CoreChip(c, coreStates.getOrElse(c){true}) { onToggleCore(c) } }
                }
            }
        }
        "gpu" -> {
            // GPU freqs from scanned nodes (MHz)
            val freqG = availFreqsGpu.ifEmpty { GPU_S }
            Text("GPU", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tPurple)
            Spacer(Modifier.height(8.dp))
            var minI by remember(config.gpuMin, freqG) { mutableIntStateOf(ci(freqG, config.gpuMin)) }
            var maxI by remember(config.gpuMax, freqG) { mutableIntStateOf(ci(freqG, config.gpuMax)) }
            // Power level: live read, range 0..8 (0=max perf, 8=min)
            val livePl = liveNodes.gpuPwrlevel
            var pl by remember(config.gpuPwrlevel, livePl) { mutableIntStateOf(if (livePl >= 0) livePl else config.gpuPwrlevel) }
            // Idle timer: live read, 1..1000 ms
            val liveIt = liveNodes.gpuIdleTimer
            var idleTimer by remember(config.gpuIdleTimer, liveIt) { mutableIntStateOf(if (liveIt >= 0) liveIt else config.gpuIdleTimer) }
            RevertableSlider("Min Frequency", minI.toFloat(), 0f, freqG.lastIndex.toFloat(), (freqG.size-2).coerceAtLeast(0),
                "${freqG.getOrElse(minI){0}} MHz", tPurple, { minI=it.toInt() },
                onRevert = { minI = 0; onSet("gpu_min", freqG.first().toString()) },
                info = "Minimum GPU frequency. Higher = smoother at cost of battery.",
            ) { onSet("gpu_min", freqG[minI].toString()) }
            RevertableSlider("Max Frequency", maxI.toFloat(), 0f, freqG.lastIndex.toFloat(), (freqG.size-2).coerceAtLeast(0),
                "${freqG.getOrElse(maxI){0}} MHz", tPurple, { maxI=it.toInt() },
                onRevert = { maxI = freqG.lastIndex; onSet("gpu_max", freqG.last().toString()) },
                info = "Maximum GPU frequency. Limit for thermals during gaming.",
            ) { onSet("gpu_max", freqG[maxI].toString()) }
            // Power level — continuous 0-8
            RevertableSlider("Power Level", pl.toFloat(), 0f, 8f, 0,
                "$pl (0=max perf, 8=min)", tPurple, { pl=it.toInt() },
                onRevert = { pl = 0; onSet("gpu_pwrlevel", "0") },
                info = "GPU power level override. 0 = max performance, 8 = minimum power. Writes /sys/class/kgsl/kgsl-3d0/pwrlevel.",
            ) { onSet("gpu_pwrlevel", pl.toString()) }
            // Idle timer — continuous 1-1000ms
            RevertableSlider("Idle Timer", idleTimer.toFloat(), 1f, 1000f, 0,
                "$idleTimer ms", tPurple, { idleTimer=it.toInt() },
                onRevert = { idleTimer = nodeDefaults.gpuIdleTimer; onSet("gpu_idle_timer", nodeDefaults.gpuIdleTimer.toString()) },
                info = "GPU goes idle after this many ms of no work. Lower = faster idle, more battery saving.",
            ) { onSet("gpu_idle_timer", idleTimer.toString()) }
        }
        "memory" -> {
            Text("Memory & VM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tBlue)
            Spacer(Modifier.height(8.dp))
            // Use live node values as the initial state; fall back to config
            val initSwap   = if (liveNodes.vmSwappiness >= 0)           liveNodes.vmSwappiness           else config.vmSwappiness
            val initDirty  = if (liveNodes.vmDirtyRatio >= 0)           liveNodes.vmDirtyRatio           else config.vmDirtyRatio
            val initDirtyBg= if (liveNodes.vmDirtyBackgroundRatio >= 0) liveNodes.vmDirtyBackgroundRatio else config.vmDirtyBackgroundRatio
            val initVfs    = if (liveNodes.vmVfsCachePressure >= 0)     liveNodes.vmVfsCachePressure     else config.vmVfsCachePressure
            val initZI     = ZRAM_B.indexOfFirst { it == config.zramSize }.coerceAtLeast(0)
            var swap    by remember(initSwap)    { mutableIntStateOf(initSwap) }
            var dirty   by remember(initDirty)   { mutableIntStateOf(initDirty) }
            var dirtyBg by remember(initDirtyBg) { mutableIntStateOf(initDirtyBg) }
            var vfs     by remember(initVfs)     { mutableIntStateOf(initVfs) }
            var zI      by remember(config.zramSize) { mutableIntStateOf(initZI) }
            // Continuous sliders (steps=0) — these are arbitrary kernel values, not discrete
            RevertableSlider("Swappiness", swap.toFloat(), 0f, 200f, 0,
                "$swap", tBlue, { swap = it.toInt() },
                onRevert = { swap = nodeDefaults.vmSwappiness; onSet("vm_swappiness", nodeDefaults.vmSwappiness.toString()) },
                info = "How aggressively kernel swaps. 0 = prefer RAM, 100 = balanced, 200 = max swap.",
            ) { onSet("vm_swappiness", swap.toString()) }
            RevertableSlider("Dirty Ratio", dirty.toFloat(), 1f, 90f, 0,
                "$dirty %", tBlue, { dirty = it.toInt() },
                onRevert = { dirty = nodeDefaults.vmDirtyRatio; onSet("vm_dirty_ratio", nodeDefaults.vmDirtyRatio.toString()) },
                info = "Max dirty memory % before process blocks to write.",
            ) { onSet("vm_dirty_ratio", dirty.toString()) }
            RevertableSlider("Dirty BG Ratio", dirtyBg.toFloat(), 1f, 50f, 0,
                "$dirtyBg %", tBlue, { dirtyBg = it.toInt() },
                onRevert = { dirtyBg = nodeDefaults.vmDirtyBackgroundRatio; onSet("vm_dirty_background_ratio", nodeDefaults.vmDirtyBackgroundRatio.toString()) },
                info = "Dirty memory % that triggers background writeback.",
            ) { onSet("vm_dirty_background_ratio", dirtyBg.toString()) }
            RevertableSlider("VFS Cache Pressure", vfs.toFloat(), 10f, 500f, 0,
                "$vfs", tBlue, { vfs = it.toInt() },
                onRevert = { vfs = nodeDefaults.vmVfsCachePressure; onSet("vm_vfs_cache_pressure", nodeDefaults.vmVfsCachePressure.toString()) },
                info = "Tendency to reclaim inode/dentry cache. 100 = kernel default.",
            ) { onSet("vm_vfs_cache_pressure", vfs.toString()) }
            RevertableSlider("ZRAM Size", zI.toFloat(), 0f, 3f, 2,
                "${zI + 1} GB", tBlue, { zI = it.toInt() },
                onRevert = { zI = 3; onSet("zram_size", ZRAM_B[3].toString()) },
                info = "Compressed swap size in RAM. Applied via ZRAM reset+mkswap.",
            ) { onSet("zram_size", ZRAM_B[zI].toString()) }
            Spacer(Modifier.height(4.dp))
            ChipRowTuning("ZRAM Algorithm", ZRAM_ALGOS, config.zramAlgo,
                info = "Compression: lz4 = fastest, zstd = best ratio, lzo = balanced.") { onSet("zram_algo", it) }
        }
        "thermal" -> {
            Text("Thermal Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tRed)
            Spacer(Modifier.height(12.dp))
            val prof = ThermalProfile.fromSconfig(sconfig)
            val activeCol = when(sconfig){"9"->tGreen;"12"->tGold;"32"->tRed;else->tBlue}
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulsingDot(activeCol)
                Text("Active: ${prof.label}", style=MaterialTheme.typography.bodyLarge,
                    color=activeCol, fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ThermalProfile.values().forEach { p -> ProfileChip(p.label, sconfig==p.sconfig) { onProfileSelected(p) } }
            }
            Spacer(Modifier.height(16.dp))
            // Thermal Boost
            var boostOn by remember(liveNodes.thermalBoost) { mutableStateOf(liveNodes.thermalBoost) }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Thermal Boost", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Writes /sys/class/thermal/thermal_message/boost",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FancyToggle(boostOn) { v -> boostOn = v; onSet("thermal_boost", if (v) "1" else "0") }
            }
        }
        "io" -> {
            Text("I/O", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tCool)
            Spacer(Modifier.height(8.dp))
            ChipRowTuning("Scheduler", listOf("bfq","mq-deadline","kyber","none"), config.ioScheduler,
                info = "Block I/O scheduler. bfq = interactive, mq-deadline = low latency, kyber = throughput.") { onSet("io_scheduler", it) }
            Spacer(Modifier.height(12.dp))
            // Read-ahead: discrete list, use indexing
            val liveRa = liveNodes.readAheadKb
            val initRa = if (liveRa > 0) READ_AHEAD_VALS.indexOfFirst { it == liveRa }.let { if (it < 0) READ_AHEAD_VALS.indexOfFirst { v -> v >= liveRa }.coerceAtLeast(0) else it }
                         else READ_AHEAD_VALS.indexOfFirst { it == config.readAheadKb }.coerceAtLeast(0)
            var readAheadIdx by remember(liveRa, config.readAheadKb) { mutableIntStateOf(initRa) }
            RevertableSlider("Read-Ahead", readAheadIdx.toFloat(), 0f, READ_AHEAD_VALS.lastIndex.toFloat(), READ_AHEAD_VALS.lastIndex - 1,
                "${READ_AHEAD_VALS[readAheadIdx]} KB", tCool, { readAheadIdx = it.toInt() },
                onRevert = { readAheadIdx = READ_AHEAD_VALS.indexOfFirst { it == nodeDefaults.readAheadKb }.coerceAtLeast(0); onSet("read_ahead_kb", nodeDefaults.readAheadKb.toString()) },
                info = "Pre-fetch buffer per block device. Higher = better sequential read, more RAM.",
            ) { onSet("read_ahead_kb", READ_AHEAD_VALS[readAheadIdx].toString()) }
        }
        "network" -> {
            Text("Network", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tBlue)
            Spacer(Modifier.height(8.dp))
            val liveTcp = liveNodes.tcpAlgo.ifEmpty { config.tcpAlgo }
            ChipRowTuning("TCP Congestion", listOf("cubic","reno","westwood","bbr"), liveTcp,
                info = "TCP congestion control. bbr = low latency+high throughput, cubic = Linux default.") { onSet("tcp_algo", it) }
            Spacer(Modifier.height(12.dp))
            val liveRxq = liveNodes.netRxqueuelen
            val initRxq = if (liveRxq > 0) liveRxq else config.netRxqueuelen
            var rxq by remember(liveRxq, config.netRxqueuelen) { mutableIntStateOf(initRxq) }
            // Continuous slider — queue len is arbitrary
            RevertableSlider("TX Queue Length", rxq.toFloat(), 100f, 10000f, 0,
                "$rxq", tBlue, { rxq = it.toInt() },
                onRevert = { rxq = nodeDefaults.netRxqueuelen; onSet("net_rxqueuelen", nodeDefaults.netRxqueuelen.toString()) },
                info = "Network interface TX queue depth. Higher = better throughput under load.",
            ) { onSet("net_rxqueuelen", rxq.toString()) }
        }
    }
}

// ── Live per-core frequency bars ─────────────────────────────────────
@Composable
private fun CoreFreqGrid(cores: List<Int>, freqs: List<Int>, maxFreq: Int, color: Color) {
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    val trackBg   = if (isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.08f)
    val barColor  = color
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cores.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { coreIdx ->
                    val mhz     = freqs.getOrElse(coreIdx) { 0 }
                    val frac    = (mhz.toFloat() / maxFreq.toFloat()).coerceIn(0f, 1f)
                    val animFrac by animateFloatAsState(frac, spring(0.8f, 120f), label = "cf$coreIdx")
                    val animMhz  by animateIntAsState(mhz, tween(300), label = "cm$coreIdx")
                    Box(
                        Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)).background(trackBg)
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(animFrac)
                                .background(Brush.horizontalGradient(listOf(barColor.copy(0.45f), barColor)), RoundedCornerShape(10.dp))
                        )
                        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("C$coreIdx",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (animFrac > 0.4f) Color.White.copy(if (isLight) 0.9f else 1f) else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (mhz == 0) "off" else "$animMhz",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                color = if (animFrac > 0.7f) Color.White.copy(if (isLight) 0.9f else 1f) else barColor)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
// ── Slider with long-press revert + info button ───────────────────────
@Composable
private fun RevertableSlider(
    label: String, value: Float, min: Float, max: Float, steps: Int,
    display: String, tint: Color, onDrag: (Float)->Unit,
    onRevert: () -> Unit,
    info: String,
    onCommit: ()->Unit,
) {
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    var showInfo by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            // Label + info button
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(label, style=MaterialTheme.typography.bodyLarge, fontWeight=FontWeight.Medium, color=MaterialTheme.colorScheme.onSurface)
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                        .clickable { showInfo = !showInfo },
                    contentAlignment = Alignment.Center
                ) {
                    Text("i", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            AnimatedContent(display, label="dv",
                transitionSpec={ (slideInVertically{-it}+fadeIn()).togetherWith(slideOutVertically{it}+fadeOut()) }
            ) { Text(it, style=MaterialTheme.typography.bodyMedium, color=tint, fontWeight=FontWeight.Bold) }
        }
        AnimatedVisibility(showInfo, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isLight) Color(0xFFf0f4f8) else Color(0xFF1a1a2e))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value, onValueChange = onDrag,
                valueRange = min..max, steps = steps,
                onValueChangeFinished = onCommit,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = tint, activeTrackColor = tint,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha=if(isLight) 0.3f else 0.5f),
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
            // Long-press revert icon
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = {}, onLongClick = onRevert)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, "Revert to default", tint = tint, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Chip row with info button ─────────────────────────────────────────
@Composable
private fun ChipRowTuning(label: String, opts: List<String>, sel: String, info: String = "", onSel: (String)->Unit) {
    val isLight = MaterialTheme.colorScheme.surface.red > 0.5f
    var showInfo by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style=MaterialTheme.typography.bodyLarge, color=MaterialTheme.colorScheme.onSurface)
        if (info.isNotEmpty()) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                    .clickable { showInfo = !showInfo },
                contentAlignment = Alignment.Center
            ) {
                Text("i", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
    AnimatedVisibility(showInfo, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLight) Color(0xFFf0f4f8) else Color(0xFF1a1a2e))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
        opts.forEach { o -> ProfileChip(o, o==sel) { onSel(o) } }
    }
}
