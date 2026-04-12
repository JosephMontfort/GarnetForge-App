package dev.garnetforge.app.service

import android.app.*
import android.app.usage.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.garnetforge.app.R
import dev.garnetforge.app.data.model.AppProfile
import dev.garnetforge.app.data.model.GarnetConfig
import dev.garnetforge.app.data.repository.ConfigRepository
import dev.garnetforge.app.data.repository.SysfsRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

private data class SystemSnapshot(
    val thermal: String = "20",
    val cpu0Min: String = "691200",
    val cpu0Max: String = "1958400",
    val cpu4Min: String = "691200",
    val cpu4Max: String = "2400000",
    val gpuMin: String = "295000000",
    val gpuMax: String = "940000000",
    val gov0: String = "walt",
    val gov4: String = "walt",
)

class GarnetService : Service() {

    companion object {
        const val CHANNEL_ID = "garnet_svc"
        const val NOTIF_ID   = 1
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, GarnetService::class.java))
        fun stop(ctx: Context)  = ctx.stopService(Intent(ctx, GarnetService::class.java))
    }

    private val scope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val configRepo = ConfigRepository()
    private val sysfsRepo  = SysfsRepository(this)

    @Volatile private var cfg: GarnetConfig = GarnetConfig()
    @Volatile private var appProfileMap: Map<String, AppProfile> = emptyMap()
    @Volatile private var profileMapAge = 0L
    @Volatile private var screenOn = true

    private var pollJob: Job? = null
    private var lastPkg      = ""
    private var prevSnapshot: SystemSnapshot? = null
    private var prevSaved    = false

    private val SCONFIG  = SysfsRepository.SCONFIG
    private val USB_NODE = "/sys/class/thermal/thermal_message/usb_online"

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; scope.launch { onScreenOff() } }
                Intent.ACTION_SCREEN_ON  -> { screenOn = true;  scope.launch { onScreenOn()  } }
            }
        }
    }

    private val chargerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED    -> scope.launch { onChargerConnected()    }
                Intent.ACTION_POWER_DISCONNECTED -> scope.launch { onChargerDisconnected() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotif("GarnetForge active"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        })
        registerReceiver(chargerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        screenOn = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        scope.launch {
            cfg = runCatching { configRepo.load() }.getOrDefault(GarnetConfig())
            appProfileMap = runCatching { sysfsRepo.getAppProfiles().filter { it.value.enabled } }.getOrDefault(emptyMap())
            profileMapAge = System.currentTimeMillis()
            if (screenOn) startPolling()
        }
        scope.launch { while (isActive) { delay(30_000); cfg = runCatching { configRepo.load() }.getOrDefault(cfg) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver)  }
        runCatching { unregisterReceiver(chargerReceiver) }
        super.onDestroy()
    }

    private suspend fun onScreenOff() {
        pollJob?.cancel()
        restoreIfNeeded()
        lastPkg = ""; prevSaved = false; prevSnapshot = null
        if (!cfg.nightMode) return
        Shell.cmd("for c in 2 3 5 6 7; do echo 0 > /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null; done").exec()
    }

    private suspend fun onScreenOn() {
        if (cfg.nightMode) Shell.cmd(
            "for c in 1 2 3 4 5 6 7; do echo 1 > /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null; done"
        ).exec()
        startPolling()
    }

    private suspend fun onChargerConnected() {
        if (cfg.thermalControl) Shell.cmd("printf '32' > $SCONFIG 2>/dev/null").exec()
    }

    private suspend fun onChargerDisconnected() {
        if (cfg.thermalControl) Shell.cmd("printf '${cfg.thermalProfile}' > $SCONFIG 2>/dev/null").exec()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && screenOn) { runCatching { tick() }; delay(500) }
        }
    }

    private suspend fun tick() {
        if (!cfg.perAppThermal) return
        if (cfg.thermalControl) {
            val usb = Shell.cmd("cat $USB_NODE 2>/dev/null").exec().out.firstOrNull()?.trim()
            if (usb == "1") return
        }
        val now = System.currentTimeMillis()
        if (now - profileMapAge > 10_000) {
            appProfileMap = runCatching { sysfsRepo.getAppProfiles().filter { it.value.enabled } }.getOrDefault(appProfileMap)
            profileMapAge = now
        }

        val rawPkg   = getForegroundPackage()
        val isLauncher = rawPkg != null && isLauncherPkg(rawPkg)
        val pkg = if (rawPkg == null || isLauncher) null else rawPkg

        if (pkg == lastPkg && !isLauncher) return
        // Brief dwell — ignore if pkg flipped back within one poll cycle
        if (pkg != null && pkg != lastPkg) {
            delay(600)
            val recheck = getForegroundPackage()
            val recheckIsLauncher = recheck != null && isLauncherPkg(recheck)
            val stable = if (recheck == null || recheckIsLauncher) null else recheck
            if (stable != pkg) return   // already changed, skip
        }
        lastPkg = pkg ?: ""

        if (pkg == null) { restoreIfNeeded(); return }

        val profile = appProfileMap[pkg]
        if (profile == null) { restoreIfNeeded(); return }

        if (!prevSaved) {
            prevSnapshot = captureSnapshot()
            prevSaved = true
        }
        applyProfile(profile)
    }

    private suspend fun captureSnapshot(): SystemSnapshot {
        val r = { p: String -> Shell.cmd("cat $p 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "" }
        return SystemSnapshot(
            thermal = r(SCONFIG),
            cpu0Min = r(SysfsRepository.CPU0_MIN),
            cpu0Max = r(SysfsRepository.CPU0_MAX),
            cpu4Min = r(SysfsRepository.CPU4_MIN),
            cpu4Max = r(SysfsRepository.CPU4_MAX),
            gpuMin  = r(SysfsRepository.GPU_MIN),
            gpuMax  = r(SysfsRepository.GPU_MAX),
            gov0    = r(SysfsRepository.CPU0_GOV),
            gov4    = r(SysfsRepository.CPU4_GOV),
        )
    }

    @Volatile private var presetCache: Map<String, ProfilePreset> = emptyMap()
    @Volatile private var presetCacheAge = 0L

    private suspend fun resolveProfile(p: AppProfile): AppProfile {
        if (p.presetId == null) return p
        val now = System.currentTimeMillis()
        if (now - presetCacheAge > 15_000) {
            presetCache = runCatching { sysfsRepo.getPresets().associateBy { it.id } }.getOrDefault(presetCache)
            presetCacheAge = now
        }
        val preset = presetCache[p.presetId] ?: return p
        return p.copy(
            thermal = preset.thermal, gov0 = preset.gov0, gov4 = preset.gov4,
            cpu0Min = preset.cpu0Min, cpu0Max = preset.cpu0Max,
            cpu4Min = preset.cpu4Min, cpu4Max = preset.cpu4Max,
            gpuMin  = preset.gpuMin,  gpuMax  = preset.gpuMax,
            offlinedCores = preset.offlinedCores,
        )
    }

    private suspend fun applyProfile(raw: AppProfile) {
        val p = resolveProfile(raw)
        val cmds = mutableListOf<String>()
        p.thermal?.let  { cmds.add("printf '$it' > $SCONFIG 2>/dev/null") }
        p.gov0?.let     { cmds.add("echo $it > ${SysfsRepository.CPU0_GOV} 2>/dev/null") }
        p.gov4?.let     { cmds.add("echo $it > ${SysfsRepository.CPU4_GOV} 2>/dev/null") }
        p.cpu0Max?.let  { cmds.add("echo $it > ${SysfsRepository.CPU0_MAX} 2>/dev/null") }
        p.cpu0Min?.let  { cmds.add("echo $it > ${SysfsRepository.CPU0_MIN} 2>/dev/null") }
        p.cpu4Max?.let  { cmds.add("echo $it > ${SysfsRepository.CPU4_MAX} 2>/dev/null") }
        p.cpu4Min?.let  { cmds.add("echo $it > ${SysfsRepository.CPU4_MIN} 2>/dev/null") }
        p.gpuMax?.let   { cmds.add("echo ${it * 1_000_000L} > ${SysfsRepository.GPU_MAX} 2>/dev/null") }
        p.gpuMin?.let   { cmds.add("echo ${it * 1_000_000L} > ${SysfsRepository.GPU_MIN} 2>/dev/null") }
        val allowed = (1..3).toSet() + (5..7).toSet()
        val toOffline = p.offlinedCores.intersect(allowed)
        val toOnline  = allowed - toOffline
        toOffline.forEach { cmds.add("echo 0 > /sys/devices/system/cpu/cpu${it}/online 2>/dev/null") }
        toOnline.forEach  { cmds.add("echo 1 > /sys/devices/system/cpu/cpu${it}/online 2>/dev/null") }
        if (cmds.isNotEmpty()) Shell.cmd(*cmds.toTypedArray()).exec()
    }

    private suspend fun restoreIfNeeded() {
        val snap = prevSnapshot ?: return
        if (!prevSaved) return
        Shell.cmd(
            "printf '${snap.thermal}' > $SCONFIG 2>/dev/null",
            "echo ${snap.gov0} > ${SysfsRepository.CPU0_GOV} 2>/dev/null",
            "echo ${snap.gov4} > ${SysfsRepository.CPU4_GOV} 2>/dev/null",
            "echo ${snap.cpu0Max} > ${SysfsRepository.CPU0_MAX} 2>/dev/null",
            "echo ${snap.cpu0Min} > ${SysfsRepository.CPU0_MIN} 2>/dev/null",
            "echo ${snap.cpu4Max} > ${SysfsRepository.CPU4_MAX} 2>/dev/null",
            "echo ${snap.cpu4Min} > ${SysfsRepository.CPU4_MIN} 2>/dev/null",
            "echo ${snap.gpuMax} > ${SysfsRepository.GPU_MAX} 2>/dev/null",
            "echo ${snap.gpuMin} > ${SysfsRepository.GPU_MIN} 2>/dev/null",
            // Bring all cores back online
            "for c in 1 2 3 4 5 6 7; do echo 1 > /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null; done",
        ).exec()
        prevSaved = false; prevSnapshot = null
    }

    private fun getForegroundPackage(): String? = try {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Use a 2s window. Prefer ACTIVITY_RESUMED (type 7) which only fires for the
        // true top activity — not freeform floating windows. Fall back to MOVE_TO_FOREGROUND.
        val events = usm.queryEvents(now - 2000, now) ?: return null
        val ev = UsageEvents.Event()
        var lastFg: String? = null
        var lastFgTime = 0L
        var lastResumed: String? = null
        var lastResumedTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> if (ev.timeStamp > lastFgTime) { lastFg = ev.packageName; lastFgTime = ev.timeStamp }
                7 /* ACTIVITY_RESUMED */ -> if (ev.timeStamp > lastResumedTime) { lastResumed = ev.packageName; lastResumedTime = ev.timeStamp }
            }
        }
        // Prefer ACTIVITY_RESUMED; it doesn't fire for freeform overlays
        val pkg = (lastResumed ?: lastFg)?.takeIf { !isSystemService(it) }
        pkg
    } catch (e: Exception) { null }

    private fun isLauncherPkg(p: String) =
        p.startsWith("com.android.launcher") || p.startsWith("com.google.android.apps.nexuslauncher") ||
        p.startsWith("com.miui.home") || p.startsWith("com.oneplus.launcher") || p.startsWith("com.sec.android.app.launcher")

    private fun isSystemService(p: String) =
        p == packageName || p.startsWith("com.android.systemui") || p == "android"

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "GarnetForge", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GarnetForge").setContentText(text)
            .setSmallIcon(R.drawable.ic_tile).setOngoing(true).build()
}
