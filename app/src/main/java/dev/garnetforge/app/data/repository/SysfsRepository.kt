package dev.garnetforge.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.garnetforge.app.data.model.LiveStats
import dev.garnetforge.app.data.model.ThermalApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

class SysfsRepository(private val context: Context) {

    companion object {
        const val INSTALL_DIR   = "/data/adb/garnetforge"
        const val THERMAL_APPS  = "$INSTALL_DIR/thermal_apps.prop"
        const val SCONFIG       = "/sys/class/thermal/thermal_message/sconfig"
        const val CPU0_MAX      = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
        const val CPU0_MIN      = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        const val CPU0_GOV      = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
        const val CPU4_MAX      = "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"
        const val CPU4_MIN      = "/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq"
        const val CPU4_GOV      = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor"
        const val CPU0_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        const val CPU4_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_min_freq"
        const val GPU_MAX       = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/max_freq"
        const val GPU_MIN       = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/min_freq"
        const val GPU_CUR       = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/cur_freq"
        const val SWAPPINESS    = "/proc/sys/vm/swappiness"
        const val VFS_PRESSURE  = "/proc/sys/vm/vfs_cache_pressure"
        const val TCP_ALGO      = "/proc/sys/net/ipv4/tcp_congestion_control"
    }

    // ── Live stats including per-core freqs ────────────────────────────
    suspend fun getLiveStats(): LiveStats = withContext(Dispatchers.IO) {
        // Single compound shell call for all 8 core freqs + cluster freqs + temps
        val raw = Shell.cmd(
            "printf '%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s' " +
            "\"\$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpufreq/policy4/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat $GPU_CUR 2>/dev/null)\" " +
            "\"\$(cat /sys/class/thermal/thermal_zone67/temp 2>/dev/null)\" " +
            "\"\$(cat /sys/class/thermal/thermal_zone31/temp 2>/dev/null)\" " +
            "\"\$(cat /sys/class/thermal/thermal_zone43/temp 2>/dev/null)\" " +
            "\"\$(cat /sys/class/power_supply/battery/temp 2>/dev/null)\" " +
            "\"\$(awk '/MemAvailable/{print \$2}' /proc/meminfo 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu1/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu2/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu3/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu5/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu7/cpufreq/scaling_cur_freq 2>/dev/null)\" " +
            "\"\$(cat /sys/devices/system/cpu/cpu0/online 2>/dev/null || echo 1)\" " +
            "\"\$(for c in 1 2 3 4 5 6 7; do printf '%s ' \"\$(cat /sys/devices/system/cpu/cpu\${c}/online 2>/dev/null || echo 1)\"; done)\""
        ).exec().out.firstOrNull() ?: ""

        val p = raw.split("|")
        fun lk(i: Int) = p.getOrNull(i)?.trim()?.toLongOrNull() ?: 0L

        val perCore = (8..15).map { i -> (lk(i) / 1000).toInt() }

        val h = LocalTime.now().hour
        val h12 = if (h % 12 == 0) 12 else h % 12
        LiveStats(
            cpu0FreqMhz    = (lk(0) / 1000).toInt(),
            cpu4FreqMhz    = (lk(1) / 1000).toInt(),
            gpuFreqMhz     = (lk(2) / 1_000_000).toInt(),
            cpuTempC       = (lk(3) / 1000).toInt(),
            gpuTempC       = (lk(4) / 1000).toInt(),
            ddrTempC       = (lk(5) / 1000).toInt(),
            battTempC      = (lk(6) / 10).toInt(),
            freeRamMb      = (lk(7) / 1024).toInt(),
            timeStr        = "$h12:${LocalTime.now().minute.toString().padStart(2,'0')} ${if(h<12)"AM" else "PM"}",
            perCoreFreqMhz = perCore,
        )
    }

    suspend fun getCurrentSconfig(): String = withContext(Dispatchers.IO) {
        Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
    }

    suspend fun clearRam(): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null").exec()
    }

    // ── Direct sysfs writes ────────────────────────────────────────────
    suspend fun writeCpuFreq(policy: Int, minKhz: Int?, maxKhz: Int?): Unit = withContext(Dispatchers.IO) {
        val minNode = if (policy == 0) CPU0_MIN else CPU4_MIN
        val maxNode = if (policy == 0) CPU0_MAX else CPU4_MAX
        val infoMin = if (policy == 0) CPU0_INFO_MIN else CPU4_INFO_MIN
        if (maxKhz != null) {
            val hwMin = Shell.cmd("cat $infoMin 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "691200"
            Shell.cmd("echo $hwMin > $minNode 2>/dev/null; echo $maxKhz > $maxNode 2>/dev/null").exec()
            val actual = Shell.cmd("cat $maxNode 2>/dev/null").exec().out.firstOrNull()?.trim()
            if (actual != maxKhz.toString()) Shell.cmd("echo $maxKhz > $maxNode 2>/dev/null").exec()
        }
        if (minKhz != null) Shell.cmd("echo $minKhz > $minNode 2>/dev/null").exec()
    }

    suspend fun writeCpuGovernor(policy: Int, gov: String): Unit = withContext(Dispatchers.IO) {
        val node = if (policy == 0) CPU0_GOV else CPU4_GOV
        val cur = Shell.cmd("cat $node 2>/dev/null").exec().out.firstOrNull()?.trim()
        if (cur != gov) Shell.cmd("echo $gov > $node 2>/dev/null").exec()
    }

    suspend fun writeGpuFreq(minMhz: Int?, maxMhz: Int?): Unit = withContext(Dispatchers.IO) {
        maxMhz?.let { Shell.cmd("echo ${it * 1_000_000L} > $GPU_MAX 2>/dev/null").exec() }
        minMhz?.let { Shell.cmd("echo ${it * 1_000_000L} > $GPU_MIN 2>/dev/null").exec() }
    }

    suspend fun writeSwappiness(v: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("echo $v > $SWAPPINESS 2>/dev/null").exec()
    }

    suspend fun writeVmParam(node: String, v: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("echo $v > $node 2>/dev/null").exec()
    }

    suspend fun writeZram(sizeBytes: Long, algo: String): Unit = withContext(Dispatchers.IO) {
        // Proper ZRAM resize: reset → set algo → set disksize → mkswap → swapon
        Shell.cmd(
            "swapoff /dev/zram0 2>/dev/null; " +
            "echo 1 > /sys/block/zram0/reset 2>/dev/null; " +
            "echo $algo > /sys/block/zram0/comp_algorithm 2>/dev/null; " +
            "echo $sizeBytes > /sys/block/zram0/disksize 2>/dev/null; " +
            "mkswap /dev/zram0 2>/dev/null; " +
            "swapon /dev/zram0 2>/dev/null"
        ).exec()
    }

    suspend fun writeIoScheduler(scheduler: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd(
            "for q in /sys/block/*/queue/scheduler; do " +
            "  echo $scheduler > \"\$q\" 2>/dev/null; " +
            "done"
        ).exec()
    }

    suspend fun writeReadAhead(kb: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd(
            "for ra in /sys/block/*/queue/read_ahead_kb; do " +
            "  echo $kb > \"\$ra\" 2>/dev/null; " +
            "done"
        ).exec()
    }

    suspend fun writeTcp(algo: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("printf '%s' '$algo' > $TCP_ALGO 2>/dev/null").exec()
    }

    suspend fun writeNetRxqueuelen(v: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd(
            "for iface in \$(ls /sys/class/net/ 2>/dev/null); do " +
            "  ip link set \$iface txqueuelen $v 2>/dev/null; " +
            "done"
        ).exec()
    }

    // ── Thermal apps ───────────────────────────────────────────────────
    suspend fun getThermalApps(): Map<String, String> = withContext(Dispatchers.IO) {
        Shell.cmd("cat $THERMAL_APPS 2>/dev/null").exec().out.mapNotNull { line ->
            val eq = line.indexOf('='); if (eq <= 0) null
            else {
                val key = line.substring(0, eq).trim()
                val pkg = if (key.startsWith("thermal_")) key.substring(8) else key
                pkg to line.substring(eq + 1).trim()
            }
        }.toMap()
    }

    suspend fun setAppThermal(pkg: String, profile: String): Unit = withContext(Dispatchers.IO) {
        val esc = pkg.replace(".", "\\.")
        if (profile.isEmpty()) {
            Shell.cmd("grep -v '^thermal_${esc}=' $THERMAL_APPS > ${THERMAL_APPS}.tmp 2>/dev/null && mv ${THERMAL_APPS}.tmp $THERMAL_APPS 2>/dev/null || true").exec()
        } else {
            Shell.cmd(
                "grep -v '^thermal_${esc}=' $THERMAL_APPS > ${THERMAL_APPS}.tmp 2>/dev/null; " +
                "printf 'thermal_${pkg}=${profile}\\n' >> ${THERMAL_APPS}.tmp; " +
                "mv ${THERMAL_APPS}.tmp $THERMAL_APPS"
            ).exec()
        }
    }

    suspend fun getInstalledApps(): List<ThermalApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).also {
            it.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val flags: Int = if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0
        val ris = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        else
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, flags)

        ris.mapNotNull { ri ->
            val ai  = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val pkg = ai.packageName ?: return@mapNotNull null
            val lbl = runCatching { pm.getApplicationLabel(ai).toString() }.getOrElse { pkg }
            ThermalApp(pkg, lbl, "")
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
}
