package dev.garnetforge.app.service

import android.app.*
import android.app.usage.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.garnetforge.app.R
import dev.garnetforge.app.data.model.GarnetConfig
import dev.garnetforge.app.data.repository.ConfigRepository
import dev.garnetforge.app.data.repository.SysfsRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

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
    @Volatile private var thermalMap: Map<String, String> = emptyMap()
    @Volatile private var thermalMapAge = 0L
    @Volatile private var screenOn = true

    private var pollJob: Job? = null
    private var lastPkg     = ""
    private var prevSconfig = ""
    private var prevSaved   = false

    private val SCONFIG = SysfsRepository.SCONFIG
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
            thermalMap = runCatching { sysfsRepo.getThermalApps() }.getOrDefault(emptyMap())
            thermalMapAge = System.currentTimeMillis()
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
        // Restore per-app before night mode applies
        restoreIfNeeded()
        lastPkg = ""; prevSaved = false; prevSconfig = ""
        if (!cfg.nightMode) return
        // Only offline non-essential cores — do NOT touch frequencies
        Shell.cmd(
            "for c in 2 3 5 6 7; do echo 0 > /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null; done",
        ).exec()
    }

    private suspend fun onScreenOn() {
        if (cfg.nightMode) Shell.cmd(
            "for c in 1 2 3 4 5 6 7; do echo 1 > /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null; done",
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
            while (isActive && screenOn) {
                runCatching { tick() }
                delay(500)
            }
        }
    }

    private suspend fun tick() {
        if (!cfg.perAppThermal) return
        // Charging Control takes priority
        if (cfg.thermalControl) {
            val usb = Shell.cmd("cat $USB_NODE 2>/dev/null").exec().out.firstOrNull()?.trim()
            if (usb == "1") return
        }
        // Refresh thermal map every 10s
        val now = System.currentTimeMillis()
        if (now - thermalMapAge > 10_000) {
            thermalMap = runCatching { sysfsRepo.getThermalApps() }.getOrDefault(thermalMap)
            thermalMapAge = now
        }

        val rawPkg = getForegroundPackage()
        // isLauncher: user pressed home — treat as "no profile app"
        val isLauncher = rawPkg != null && isLauncherPkg(rawPkg)
        val pkg = if (rawPkg == null || isLauncher) null else rawPkg

        if (pkg == lastPkg && !isLauncher) return  // no change

        lastPkg = pkg ?: ""

        if (pkg == null) {
            // Home screen or no foreground — restore
            restoreIfNeeded()
            return
        }

        val profile = thermalMap[pkg]
        if (profile.isNullOrEmpty()) {
            restoreIfNeeded()
            return
        }

        if (!prevSaved) {
            prevSconfig = Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
            prevSaved = true
        }
        val cur = Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim()
        if (cur != profile) Shell.cmd("printf '$profile' > $SCONFIG 2>/dev/null").exec()
    }

    private suspend fun restoreIfNeeded() {
        if (prevSaved && prevSconfig.isNotEmpty()) {
            Shell.cmd("printf '$prevSconfig' > $SCONFIG 2>/dev/null").exec()
            prevSaved = false; prevSconfig = ""
        }
    }

    private fun getForegroundPackage(): String? = try {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3000, now) ?: return null
        val ev = UsageEvents.Event(); var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = ev.packageName
        }
        last?.takeIf { !isSystemService(it) }
    } catch (e: Exception) { null }

    /** True if this package IS a launcher/homescreen (triggers restore) */
    private fun isLauncherPkg(p: String) =
        p.startsWith("com.android.launcher") ||
        p.startsWith("com.google.android.apps.nexuslauncher") ||
        p.startsWith("com.miui.home") ||
        p.startsWith("com.oneplus.launcher") ||
        p.startsWith("com.sec.android.app.launcher")

    /** True if this is a system UI service we should completely ignore */
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
