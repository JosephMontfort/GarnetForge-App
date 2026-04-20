package dev.garnetforge.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.garnetforge.app.data.model.AppProfile
import dev.garnetforge.app.data.model.LiveStats
import dev.garnetforge.app.data.model.ThermalApp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

class SysfsRepository(private val context: Context) {

    companion object {
        const val INSTALL_DIR   = "/data/data/dev.garnetforge.app/files/garnetforge"
        const val THERMAL_APPS  = "$INSTALL_DIR/thermal_apps.prop"
        const val APP_PROFILES  = "$INSTALL_DIR/app_profiles.prop"
        const val PRESETS       = "$INSTALL_DIR/profile_presets.prop"
        const val NODES         = "$INSTALL_DIR/nodes.prop"
        const val DEFAULTS      = "$INSTALL_DIR/defaults.prop"
        const val SCONFIG       = "/sys/class/thermal/thermal_message/sconfig"
        const val SWAPPINESS    = "/proc/sys/vm/swappiness"
        const val TCP_ALGO      = "/proc/sys/net/ipv4/tcp_congestion_control"

        // Fallback constants — used when nodes.prop hasn't been generated yet
        const val CPU0_MAX_FB   = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
        const val CPU0_MIN_FB   = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
        const val CPU0_GOV_FB   = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
        const val CPU4_MAX_FB   = "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq"
        const val CPU4_MIN_FB   = "/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq"
        const val CPU4_GOV_FB   = "/sys/devices/system/cpu/cpufreq/policy4/scaling_governor"
        const val GPU_MAX_FB    = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/max_freq"
        const val GPU_MIN_FB    = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/min_freq"
        const val GPU_CUR_FB    = "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/cur_freq"
        const val GPU_PL_FB     = "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel"
        const val GPU_IT_FB     = "/sys/class/kgsl/kgsl-3d0/idle_timer"
        const val THERMAL_BOOST_FB = "/sys/class/thermal/thermal_message/boost"

        // Dynamic — populated from nodes.prop at runtime via loadNodePaths()
        var CPU0_MAX   = CPU0_MAX_FB; var CPU0_MIN = CPU0_MIN_FB; var CPU0_GOV = CPU0_GOV_FB
        var CPU4_MAX   = CPU4_MAX_FB; var CPU4_MIN = CPU4_MIN_FB; var CPU4_GOV = CPU4_GOV_FB
        // cpuinfo_min_freq — updated by loadNodePaths() to use detected policy
        var CPU0_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        var CPU4_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_min_freq"
        var GPU_MAX    = GPU_MAX_FB; var GPU_MIN = GPU_MIN_FB; var GPU_CUR = GPU_CUR_FB
        var GPU_PWRLEVEL = GPU_PL_FB; var GPU_IDLE_TIMER = GPU_IT_FB
        var THERMAL_BOOST = THERMAL_BOOST_FB

        // Thermal zones + battery — populated from nodes.prop; fallbacks are Garnet-specific
        var THERMAL_ZONE_CPU = "/sys/class/thermal/thermal_zone67/temp"
        var THERMAL_ZONE_GPU = "/sys/class/thermal/thermal_zone31/temp"
        var THERMAL_ZONE_DDR = "/sys/class/thermal/thermal_zone43/temp"
        var BATTERY_TEMP     = "/sys/class/power_supply/battery/temp"

        // CPU topology — populated from nodes.prop
        var CPU_CORE_COUNT   = 8
        var PER_CORE_CUR_FREQ: Array<String> = Array(8) { c ->
            "/sys/devices/system/cpu/cpu$c/cpufreq/scaling_cur_freq"
        }

        /** True once loadNodePaths() has successfully run. */
        @Volatile var nodesLoaded = false
    }

    /** Load discovered node paths from nodes.prop into companion vars. Call after detection. */
    suspend fun loadNodePaths(): Unit = withContext(Dispatchers.IO) {
        val np = Shell.cmd("cat $NODES 2>/dev/null").exec().out.associate { line ->
            val eq = line.indexOf('='); if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
        }
        fun node(key: String, fallback: String): String {
            val path = np[key]?.trim() ?: return fallback
            // The value is a path (e.g. /sys/...). Verify it exists if we can check.
            return if (path.startsWith("/")) path else fallback
        }
        val lp  = np["cpu_little_policy"]?.trim() ?: "policy0"
        val bp  = np["cpu_big_policy"]?.trim() ?: "policy4"
        val bpol= np["cpu_prime_policy"]?.trim() ?: bp   // prime = big if no prime

        CPU0_MIN  = "/sys/devices/system/cpu/cpufreq/$lp/scaling_min_freq"
        CPU0_MAX  = "/sys/devices/system/cpu/cpufreq/$lp/scaling_max_freq"
        CPU0_GOV  = "/sys/devices/system/cpu/cpufreq/$lp/scaling_governor"
        CPU0_INFO_MIN = "/sys/devices/system/cpu/cpufreq/$lp/cpuinfo_min_freq"
        CPU4_MIN  = "/sys/devices/system/cpu/cpufreq/$bpol/scaling_min_freq"
        CPU4_MAX  = "/sys/devices/system/cpu/cpufreq/$bpol/scaling_max_freq"
        CPU4_GOV  = "/sys/devices/system/cpu/cpufreq/$bpol/scaling_governor"
        CPU4_INFO_MIN = "/sys/devices/system/cpu/cpufreq/$bpol/cpuinfo_min_freq"

        val gpuMax = node("gpu_max_node", GPU_MAX_FB)
        val gpuMin = node("gpu_min_node", GPU_MIN_FB)
        val gpuCur = node("gpu_cur_freq", GPU_CUR_FB)
        GPU_MAX = gpuMax; GPU_MIN = gpuMin; GPU_CUR = gpuCur

        val pl = node("gpu_thermal_pwrlevel", node("gpu_devfreq_governor", GPU_PL_FB))
        val actualPl = np.keys.firstOrNull { it.startsWith("gpu_") && it.contains("pwrlevel") }?.let { np[it]?.trim() } ?: GPU_PL_FB
        GPU_PWRLEVEL = actualPl

        val it = node("gpu_idle_timer", GPU_IT_FB)
        GPU_IDLE_TIMER = it

        val boost = node("thermal_msg_boost", THERMAL_BOOST_FB)
        THERMAL_BOOST = boost

        // ── Thermal zones ─────────────────────────────────────────────
        THERMAL_ZONE_CPU = node("thermal_zone_cpu", node("thermal_zone_cpu_fb", "/sys/class/thermal/thermal_zone67/temp"))
        THERMAL_ZONE_GPU = node("thermal_zone_gpu", node("thermal_zone_gpu_fb", "/sys/class/thermal/thermal_zone31/temp"))
        THERMAL_ZONE_DDR = node("thermal_zone_ddr", node("thermal_zone_ddr_fb", "/sys/class/thermal/thermal_zone43/temp"))
        BATTERY_TEMP     = np["battery_temp"]?.trim()?.takeIf { it.startsWith("/") }
                           ?: "/sys/class/power_supply/battery/temp"

        // ── CPU topology ──────────────────────────────────────────────
        CPU_CORE_COUNT = np["cpu_core_count"]?.trim()?.toIntOrNull()?.coerceIn(2, 16) ?: 8
        PER_CORE_CUR_FREQ = Array(CPU_CORE_COUNT) { c ->
            np["cpu${c}_cur_freq"]?.trim()?.takeIf { it.startsWith("/") }
                ?: "/sys/devices/system/cpu/cpu$c/cpufreq/scaling_cur_freq"
        }

        nodesLoaded = true
        android.util.Log.i("GarnetForge",
            "Nodes loaded: little=$lp big=$bpol gpu=$gpuCur tz_cpu=$THERMAL_ZONE_CPU cores=$CPU_CORE_COUNT")
    }

    // ── Available frequencies from nodes.prop ─────────────────────────
    suspend fun getAvailableFreqsKhz(policy: Int): List<Int> = withContext(Dispatchers.IO) {
        val key = "cpu_policy${policy}_avail_freqs"
        val raw = Shell.cmd("grep '^${key}=' $NODES 2>/dev/null | head -1 | cut -d= -f2-")
            .exec().out.firstOrNull()?.trim() ?: ""
        if (raw.isEmpty()) return@withContext emptyList()
        raw.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.sorted()
    }

    suspend fun getAvailableGpuFreqsMhz(): List<Int> = withContext(Dispatchers.IO) {
        val raw = Shell.cmd("grep '^gpu_avail_freqs=' $NODES 2>/dev/null | head -1 | cut -d= -f2-")
            .exec().out.firstOrNull()?.trim() ?: ""
        if (raw.isEmpty()) return@withContext emptyList()
        val vals = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0 }.sorted()
        if (vals.isEmpty()) return@withContext emptyList()
        // Detect unit: if max value > 10_000, values are in Hz → divide by 1_000_000
        // If max value is 100..10000 range, already MHz
        val maxVal = vals.last()
        when {
            maxVal > 100_000_000L -> vals.map { (it / 1_000_000L).toInt() }.filter { it >= 100 }
            maxVal > 1_000L       -> vals.map { (it / 1_000L).toInt() }.filter { it >= 100 }
            else                  -> vals.map { it.toInt() }.filter { it >= 100 }
        }.distinct().sorted()
    }

    // ── Read defaults from defaults.prop ─────────────────────────────
    suspend fun readNodeDefaults(): dev.garnetforge.app.data.model.NodeDefaults = withContext(Dispatchers.IO) {
        val raw = Shell.cmd("cat $DEFAULTS 2>/dev/null").exec().out.associate { line ->
            val eq = line.indexOf('='); if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
        }
        fun i(k: String, d: Int) = raw[k]?.trim()?.toIntOrNull() ?: d
        fun s(k: String, d: String) = raw[k]?.trim()?.ifEmpty { null } ?: d
        dev.garnetforge.app.data.model.NodeDefaults(
            vmSwappiness             = i("default_vm_swappiness", 100),
            vmDirtyRatio             = i("default_vm_dirty_ratio", 20),
            vmDirtyBackgroundRatio   = i("default_vm_dirty_background_ratio", 5),
            vmVfsCachePressure       = i("default_vm_vfs_cache_pressure", 100),
            readAheadKb              = i("default_read_ahead_kb", 512),
            tcpAlgo                  = s("default_tcp_algo", "cubic"),
            netRxqueuelen            = i("default_net_rxqueuelen", 1000),
            gpuIdleTimer             = i("default_gpu_idle_timer", 64),
        )
    }

    // ── Read live node values for tuning screen ───────────────────────
    suspend fun readLiveNodes(): dev.garnetforge.app.data.model.LiveNodeValues = withContext(Dispatchers.IO) {
        val raw = Shell.cmd(
            "printf '%s|%s|%s|%s|%s|%s|%s|%s|%s' " +
            "\"\$(cat /proc/sys/vm/swappiness 2>/dev/null)\" " +
            "\"\$(cat /proc/sys/vm/dirty_ratio 2>/dev/null)\" " +
            "\"\$(cat /proc/sys/vm/dirty_background_ratio 2>/dev/null)\" " +
            "\"\$(cat /proc/sys/vm/vfs_cache_pressure 2>/dev/null)\" " +
            "\"\$(for ra in /sys/block/*/queue/read_ahead_kb; do [ -f \"\$ra\" ] && cat \"\$ra\" && break; done 2>/dev/null)\" " +
            "\"\$(cat /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null)\" " +
            "\"\$(ip link show 2>/dev/null | grep -m1 'qlen' | sed 's/.*qlen //' | cut -d' ' -f1)\" " +
            "\"\$(cat $GPU_PWRLEVEL 2>/dev/null)\" " +
            "\"\$(cat $GPU_IDLE_TIMER 2>/dev/null)\""
        ).exec().out.firstOrNull() ?: ""
        val p = raw.split("|")
        fun pi(i: Int) = p.getOrNull(i)?.trim()?.toIntOrNull() ?: -1
        fun ps(i: Int) = p.getOrNull(i)?.trim() ?: ""
        val boost = Shell.cmd("cat $THERMAL_BOOST 2>/dev/null").exec().out.firstOrNull()?.trim() == "1"
        dev.garnetforge.app.data.model.LiveNodeValues(
            vmSwappiness           = pi(0),
            vmDirtyRatio           = pi(1),
            vmDirtyBackgroundRatio = pi(2),
            vmVfsCachePressure     = pi(3),
            readAheadKb            = pi(4),
            tcpAlgo                = ps(5),
            netRxqueuelen          = pi(6),
            gpuPwrlevel            = pi(7),
            gpuIdleTimer           = pi(8),
            thermalBoost           = boost,
        )
    }

// ── Live stats (uses cached companion vars — no nodes.prop re-read) ──────
    suspend fun getLiveStats(): LiveStats = withContext(Dispatchers.IO) {
        val litN = CPU0_MAX.replace("scaling_max_freq", "scaling_cur_freq")
        val bigN = CPU4_MAX.replace("scaling_max_freq", "scaling_cur_freq")

        // Single batched command for all primary metrics
        val clRaw = Shell.cmd(
            "printf '%s|%s|%s|%s|%s|%s|%s|%s'" +
            " \"\$(cat $litN 2>/dev/null)\"" +
            " \"\$(cat $bigN 2>/dev/null)\"" +
            " \"\$(cat $GPU_CUR 2>/dev/null)\"" +
            " \"\$(cat $THERMAL_ZONE_CPU 2>/dev/null)\"" +
            " \"\$(cat $THERMAL_ZONE_GPU 2>/dev/null)\"" +
            " \"\$(cat $THERMAL_ZONE_DDR 2>/dev/null)\"" +
            " \"\$(cat $BATTERY_TEMP 2>/dev/null)\"" +
            " \"\$(awk '/MemAvailable/{print \$2}' /proc/meminfo 2>/dev/null)\""
        ).exec().out.firstOrNull() ?: ""

        // Single batched command for all per-core frequencies
        val n = CPU_CORE_COUNT
        val coreCmd = buildString {
            append("printf '")
            repeat(n) { append("%s|") }
            append("'")
            PER_CORE_CUR_FREQ.take(n).forEach { path ->
                append(" \"\$(cat $path 2>/dev/null || echo 0)\"")
            }
        }
        val coreRaw = Shell.cmd(coreCmd).exec().out.firstOrNull() ?: ""

        val perCoreFreqs = coreRaw.trimEnd('|').split("|")
            .take(n)
            .map { it.trim().toLongOrNull()?.div(1000)?.toInt() ?: 0 }
            .let { list -> if (list.size < 8) list + List(8 - list.size) { 0 } else list }

        val cp = clRaw.split("|")
        fun ck(i: Int) = cp.getOrNull(i)?.trim()?.toLongOrNull() ?: 0L

        // GPU cur_freq may be reported in Hz (>1M), kHz, or MHz — normalise to MHz
        val gpuRaw = ck(2)
        val gpuMhz = when {
            gpuRaw > 1_000_000L -> (gpuRaw / 1_000_000L).toInt()
            gpuRaw > 1_000L     -> (gpuRaw / 1_000L).toInt()
            else                -> gpuRaw.toInt()
        }

        val h    = LocalTime.now().hour
        val h12  = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "AM" else "PM"
        LiveStats(
            cpu0FreqMhz    = (ck(0) / 1000).toInt(),
            cpu4FreqMhz    = (ck(1) / 1000).toInt(),
            gpuFreqMhz     = gpuMhz,
            cpuTempC       = (ck(3) / 1000).toInt(),
            gpuTempC       = (ck(4) / 1000).toInt(),
            ddrTempC       = (ck(5) / 1000).toInt(),
            battTempC      = (ck(6) / 10).toInt(),
            freeRamMb      = (ck(7) / 1024).toInt(),
            timeStr        = "$h12:${LocalTime.now().minute.toString().padStart(2, '0')} $ampm",
            perCoreFreqMhz = perCoreFreqs,
        )
    }

    suspend fun getCurrentSconfig(): String = withContext(Dispatchers.IO) {
        Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "20"
    }

    suspend fun clearRam(): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null").exec()
    }

    // ── Sysfs writes ──────────────────────────────────────────────────
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
        Shell.cmd(
            "swapoff /dev/block/zram0 2>/dev/null || true",
            "echo 1 > /sys/block/zram0/reset 2>/dev/null",
            "echo $algo > /sys/block/zram0/comp_algorithm 2>/dev/null",
            "echo $sizeBytes > /sys/block/zram0/disksize 2>/dev/null",
            "mkswap /dev/block/zram0 2>/dev/null",
            "swapon /dev/block/zram0 2>/dev/null"
        ).exec()
    }

    suspend fun writeIoScheduler(scheduler: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("for q in /sys/block/*/queue/scheduler; do echo $scheduler > \"\$q\" 2>/dev/null; done").exec()
    }

    suspend fun writeReadAhead(kb: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("for ra in /sys/block/*/queue/read_ahead_kb; do echo $kb > \"\$ra\" 2>/dev/null; done").exec()
    }

    suspend fun writeTcp(algo: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("printf '%s' '$algo' > $TCP_ALGO 2>/dev/null").exec()
    }

    suspend fun writeGpuPwrlevel(level: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("echo $level > $GPU_PWRLEVEL 2>/dev/null").exec()
    }

    suspend fun writeGpuIdleTimer(ms: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("echo $ms > $GPU_IDLE_TIMER 2>/dev/null").exec()
    }

    suspend fun writeThermalBoost(on: Boolean): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("echo ${if (on) "1" else "0"} > $THERMAL_BOOST 2>/dev/null").exec()
    }

    suspend fun writeNetRxqueuelen(v: Int): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("for iface in \$(ls /sys/class/net/ 2>/dev/null); do ip link set \$iface txqueuelen $v 2>/dev/null; done").exec()
    }

    // ── App profiles ──────────────────────────────────────────────────
    suspend fun getAppProfiles(): Map<String, AppProfile> = withContext(Dispatchers.IO) {
        Shell.cmd("cat $APP_PROFILES 2>/dev/null").exec().out.mapNotNull { line ->
            val eq = line.indexOf('='); if (eq <= 0) return@mapNotNull null
            val pkg = line.substring(0, eq).trim()
            val m = line.substring(eq + 1).split(",").mapNotNull { pair ->
                val ci = pair.indexOf(':'); if (ci <= 0) null else pair.substring(0, ci) to pair.substring(ci + 1)
            }.toMap()
            val offlined = m["cores"]?.split("+")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            pkg to AppProfile(
                pkg           = pkg,
                enabled       = m["enabled"] == "1",
                cpu0Min       = m["cpu0_min"]?.toIntOrNull(),
                cpu0Max       = m["cpu0_max"]?.toIntOrNull(),
                cpu4Min       = m["cpu4_min"]?.toIntOrNull(),
                cpu4Max       = m["cpu4_max"]?.toIntOrNull(),
                gpuMin        = m["gpu_min"]?.toIntOrNull(),
                gpuMax        = m["gpu_max"]?.toIntOrNull(),
                thermal       = m["thermal"],
                gov0          = m["gov0"],
                gov4          = m["gov4"],
                offlinedCores = offlined,
                presetId      = m["preset_id"],
            )
        }.toMap()
    }

    suspend fun setAppProfile(pkg: String, profile: AppProfile?): Unit = withContext(Dispatchers.IO) {
        val esc = pkg.replace(".", "\\.")
        // Remove existing entry
        Shell.cmd(
            "grep -v '^${esc}=' $APP_PROFILES > ${APP_PROFILES}.tmp 2>/dev/null || true",
            "[ -f ${APP_PROFILES}.tmp ] && mv ${APP_PROFILES}.tmp $APP_PROFILES || true"
        ).exec()
        if (profile == null) return@withContext
        // Always persist — even if disabled, so settings survive toggle off/on
        val data = buildString {
            append("enabled:${if (profile.enabled) "1" else "0"}")
            profile.cpu0Min?.let { append(",cpu0_min:$it") }
            profile.cpu0Max?.let { append(",cpu0_max:$it") }
            profile.cpu4Min?.let { append(",cpu4_min:$it") }
            profile.cpu4Max?.let { append(",cpu4_max:$it") }
            profile.gpuMin?.let  { append(",gpu_min:$it") }
            profile.gpuMax?.let  { append(",gpu_max:$it") }
            profile.thermal?.let { append(",thermal:$it") }
            profile.gov0?.let    { append(",gov0:$it") }
            profile.gov4?.let    { append(",gov4:$it") }
            if (profile.offlinedCores.isNotEmpty()) append(",cores:${profile.offlinedCores.sorted().joinToString("+")}")
            profile.presetId?.let { append(",preset_id:$it") }
        }
        Shell.cmd("printf '${pkg}=${data}\\n' >> $APP_PROFILES").exec()
    }

    // ── Profile presets ───────────────────────────────────────────────
    // Format: id=name|cpu0_min:v,cpu0_max:v,...
    suspend fun getPresets(): List<dev.garnetforge.app.data.model.ProfilePreset> = withContext(Dispatchers.IO) {
        Shell.cmd("cat $PRESETS 2>/dev/null").exec().out.mapNotNull { line ->
            val eqIdx = line.indexOf('='); if (eqIdx <= 0) return@mapNotNull null
            val id = line.substring(0, eqIdx).trim()
            val rest = line.substring(eqIdx + 1)
            val pipeIdx = rest.indexOf('|')
            val name = if (pipeIdx > 0) rest.substring(0, pipeIdx) else rest
            val dataStr = if (pipeIdx > 0) rest.substring(pipeIdx + 1) else ""
            val m = dataStr.split(",").mapNotNull { pair ->
                val ci = pair.indexOf(':'); if (ci <= 0) null else pair.substring(0, ci) to pair.substring(ci + 1)
            }.toMap()
            val offlined = m["cores"]?.split("+")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
            dev.garnetforge.app.data.model.ProfilePreset(
                id = id, name = name,
                cpu0Min = m["cpu0_min"]?.toIntOrNull(), cpu0Max = m["cpu0_max"]?.toIntOrNull(),
                cpu4Min = m["cpu4_min"]?.toIntOrNull(), cpu4Max = m["cpu4_max"]?.toIntOrNull(),
                gpuMin  = m["gpu_min"]?.toIntOrNull(),  gpuMax  = m["gpu_max"]?.toIntOrNull(),
                thermal = m["thermal"], gov0 = m["gov0"], gov4 = m["gov4"],
                offlinedCores = offlined,
            )
        }
    }

    suspend fun savePreset(preset: dev.garnetforge.app.data.model.ProfilePreset): Unit = withContext(Dispatchers.IO) {
        val esc = preset.id.replace(".", "\\.")
        Shell.cmd(
            "grep -v '^${esc}=' $PRESETS > ${PRESETS}.tmp 2>/dev/null || true",
            "[ -f ${PRESETS}.tmp ] && mv ${PRESETS}.tmp $PRESETS || true"
        ).exec()
        val data = buildString {
            preset.cpu0Min?.let { append("cpu0_min:$it") }
            preset.cpu0Max?.let { append(",cpu0_max:$it") }
            preset.cpu4Min?.let { append(",cpu4_min:$it") }
            preset.cpu4Max?.let { append(",cpu4_max:$it") }
            preset.gpuMin?.let  { append(",gpu_min:$it")  }
            preset.gpuMax?.let  { append(",gpu_max:$it")  }
            preset.thermal?.let { append(",thermal:$it")  }
            preset.gov0?.let    { append(",gov0:$it")     }
            preset.gov4?.let    { append(",gov4:$it")     }
            if (preset.offlinedCores.isNotEmpty()) append(",cores:${preset.offlinedCores.sorted().joinToString("\\+")}")
        }.trimStart(',')
        Shell.cmd("printf '${preset.id}=${preset.name}|${data}\n' >> $PRESETS").exec()
    }

    suspend fun deletePreset(id: String): Unit = withContext(Dispatchers.IO) {
        val esc = id.replace(".", "\\.")
        Shell.cmd(
            "grep -v '^${esc}=' $PRESETS > ${PRESETS}.tmp 2>/dev/null || true",
            "[ -f ${PRESETS}.tmp ] && mv ${PRESETS}.tmp $PRESETS || true"
        ).exec()
    }

    // ── Entropy boost ─────────────────────────────────────────────────
    suspend fun boostEntropy(): Unit = withContext(Dispatchers.IO) {
        // Write random data to /dev/urandom (stimulates entropy pool),
        // then lower wakeup threshold so reads get data sooner
        Shell.cmd(
            "cat /dev/hwrng > /dev/urandom 2>/dev/null & " +
            "[ -f /proc/sys/kernel/random/write_wakeup_threshold ] && " +
            "echo 256 > /proc/sys/kernel/random/write_wakeup_threshold 2>/dev/null; " +
            "cat /dev/urandom | head -c 512 > /dev/null 2>/dev/null"
        ).exec()
    }

    suspend fun getEntropyAvailable(): Int = withContext(Dispatchers.IO) {
        Shell.cmd("cat /proc/sys/kernel/random/entropy_avail 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: -1
    }

    // ── Diagnostic report ─────────────────────────────────────────────
    suspend fun runDiagnostic(): String = withContext(Dispatchers.IO) {
        val script = "$INSTALL_DIR/diagnostic.sh"
        val out    = "$INSTALL_DIR/diagnostic_report.txt"
        Shell.cmd("[ -f $script ] && sh $script").exec()
        Shell.cmd("cat $out 2>/dev/null").exec().out.joinToString("\n")
    }

    fun getDiagnosticFilePath(): String = "$INSTALL_DIR/diagnostic_report.txt"

    // ── Network speed test — background curls + Kotlin polling for live Mbps ──
    /**
     * Runs a professional multi-stream speed test with live progress callbacks.
     *
     * Architecture: each phase writes curl processes to background in a shell script,
     * then Kotlin polls downloaded/uploaded byte counts every 700 ms to compute
     * live Mbps and emit it via [onProgress]. This allows the UI speedometer to
     * animate in real time rather than only updating at phase completion.
     *
     * Download: 4 × 25 MB parallel curl streams (~15 s)
     * Upload:   4 × 3 MB parallel curl streams  (~12 s)
     */
    suspend fun runSpeedTest(
        onProgress: (phase: String, fraction: Float, currentMbps: Float) -> Unit = { _, _, _ -> }
    ): Pair<Float, Float> = withContext(Dispatchers.IO) {

        val tmp = "$INSTALL_DIR/speedtest_tmp"
        var dlFinalMbps = -1f
        var ulFinalMbps = -1f

        Shell.cmd("rm -rf $tmp && mkdir -p $tmp").exec()

        try {
            // ── DOWNLOAD ──────────────────────────────────────────────
            onProgress("download", 0f, 0f)

            // Build the download script without shell-variable interpolation issues.
            // $tmp is a Kotlin variable (interpolated here); the shell script itself
            // has no variables, so no Kotlin/$-escaping conflicts.
            val dlScriptContent = buildString {
                appendLine("#!/system/bin/sh")
                for (i in 1..4) {
                    appendLine("curl -s -o $tmp/dl$i.tmp --max-time 15 --connect-timeout 5 'https://speed.cloudflare.com/__down?bytes=25000000' 2>/dev/null &")
                }
                appendLine("wait")
                appendLine("printf done > $tmp/dl_done")
            }
            val dlB64 = android.util.Base64.encodeToString(dlScriptContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.cmd(
                "printf '%s' '$dlB64' | base64 -d > $tmp/dl.sh",
                "chmod 755 $tmp/dl.sh",
                "sh $tmp/dl.sh > $tmp/dl.log 2>&1 &"   // background — output redirected
            ).exec()

            val dlStartMs   = System.currentTimeMillis()
            var dlLastBytes = 0L
            var dlLastMs    = dlStartMs

            while (true) {
                kotlinx.coroutines.delay(700)
                val now     = System.currentTimeMillis()
                val elapsed = now - dlStartMs
                val done    = Shell.cmd("[ -f $tmp/dl_done ] && echo y || echo n")
                                  .exec().out.firstOrNull()?.trim() == "y"

                // Sum sizes of all dl*.tmp files in one awk pass (no shell $-var issues in Kotlin)
                val totalBytes = Shell.cmd(
                    "du -sk $tmp/dl1.tmp $tmp/dl2.tmp $tmp/dl3.tmp $tmp/dl4.tmp 2>/dev/null | awk '{sum+=\$1} END{print sum*1024}'"
                ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

                val deltaBytes  = (totalBytes - dlLastBytes).coerceAtLeast(0L)
                val deltaMs     = (now - dlLastMs).coerceAtLeast(1L)
                val currentMbps = (deltaBytes * 8f / 1_000_000f) / (deltaMs / 1000f)
                val fraction    = if (done) 1f else (elapsed / 15_000f).coerceIn(0f, 0.98f)

                onProgress("download", fraction, currentMbps.coerceAtLeast(0f))
                dlLastBytes = totalBytes
                dlLastMs    = now

                if (done || elapsed > 18_000L) {
                    val totalMs = now - dlStartMs
                    // Re-read final sizes accurately
                    val finalBytes = Shell.cmd(
                        "du -sk $tmp/dl1.tmp $tmp/dl2.tmp $tmp/dl3.tmp $tmp/dl4.tmp 2>/dev/null | awk '{sum+=\$1} END{print sum*1024}'"
                    ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: totalBytes
                    dlFinalMbps = if (totalMs > 500L && finalBytes > 0L)
                        (finalBytes * 8f / 1_000_000f) / (totalMs / 1000f) else -1f
                    onProgress("download", 1f, dlFinalMbps.coerceAtLeast(0f))
                    android.util.Log.i("GarnetForge", "DL done: ${finalBytes/1024}KB in ${totalMs}ms → %.1f Mbps".format(dlFinalMbps))
                    break
                }
            }

            // ── UPLOAD ────────────────────────────────────────────────
            onProgress("upload", 0f, 0f)

            // Pre-generate 3 MB random upload data, then run 4 parallel uploads.
            // Each curl writes its size_upload count to ul$i.out; we poll those files.
            val ulScriptContent = buildString {
                appendLine("#!/system/bin/sh")
                appendLine("dd if=/dev/urandom bs=1048576 count=3 of=$tmp/ul_data 2>/dev/null")
                for (i in 1..4) {
                    appendLine(
                        "curl -s -X POST -H 'Content-Type: application/octet-stream'" +
                        " --data-binary @$tmp/ul_data -o /dev/null -w '%{size_upload}'" +
                        " --max-time 12 --connect-timeout 5" +
                        " 'https://speed.cloudflare.com/__up' 2>/dev/null > $tmp/ul$i.out &"
                    )
                }
                appendLine("wait")
                appendLine("printf done > $tmp/ul_done")
            }
            val ulB64 = android.util.Base64.encodeToString(ulScriptContent.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.cmd(
                "printf '%s' '$ulB64' | base64 -d > $tmp/ul.sh",
                "chmod 755 $tmp/ul.sh",
                "sh $tmp/ul.sh > $tmp/ul.log 2>&1 &"
            ).exec()

            val ulStartMs = System.currentTimeMillis()
            var ulPrevBytes = 0L

            while (true) {
                kotlinx.coroutines.delay(700)
                val now     = System.currentTimeMillis()
                val elapsed = now - ulStartMs
                val done    = Shell.cmd("[ -f $tmp/ul_done ] && echo y || echo n")
                                  .exec().out.firstOrNull()?.trim() == "y"

                // Count completed uploads + sum bytes from ul*.out files
                val completedBytes = Shell.cmd(
                    "cat $tmp/ul1.out $tmp/ul2.out $tmp/ul3.out $tmp/ul4.out 2>/dev/null | awk '{sum+=\$1} END{print sum+0}'"
                ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

                val completedCount = Shell.cmd(
                    "find $tmp -name 'ul*.out' -size +0 2>/dev/null | wc -l"
                ).exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0

                val deltaBytes  = (completedBytes - ulPrevBytes).coerceAtLeast(0L)
                val deltaMs     = (now - ulStartMs).coerceAtLeast(1L)
                val currentMbps = if (completedBytes > 0L)
                    (completedBytes * 8f / 1_000_000f) / (deltaMs / 1000f) else 0f
                val fraction    = if (done) 1f else (completedCount / 4f).coerceIn(0f, 0.98f)

                onProgress("upload", fraction, currentMbps.coerceAtLeast(0f))
                ulPrevBytes = completedBytes

                if (done || elapsed > 20_000L) {
                    val totalMs    = now - ulStartMs
                    val finalBytes = Shell.cmd(
                        "cat $tmp/ul1.out $tmp/ul2.out $tmp/ul3.out $tmp/ul4.out 2>/dev/null | awk '{sum+=\$1} END{print sum+0}'"
                    ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: completedBytes
                    ulFinalMbps = if (totalMs > 500L && finalBytes > 0L)
                        (finalBytes * 8f / 1_000_000f) / (totalMs / 1000f) else -1f
                    onProgress("upload", 1f, ulFinalMbps.coerceAtLeast(0f))
                    android.util.Log.i("GarnetForge", "UL done: ${finalBytes/1024}KB in ${totalMs}ms → %.1f Mbps".format(ulFinalMbps))
                    break
                }
            }

        } finally {
            Shell.cmd("rm -rf $tmp").exec()
        }

        Pair(
            if (dlFinalMbps > 0) dlFinalMbps else -1f,
            if (ulFinalMbps > 0) ulFinalMbps else -1f
        )
    }

    // ── Frequency lock (chmod on scaling nodes) ──────────────────────
    suspend fun setFreqLocked(policy: Int, locked: Boolean): Unit = withContext(Dispatchers.IO) {
        val nodes = if (policy == 0)
            listOf(CPU0_MIN, CPU0_MAX)
        else
            listOf(CPU4_MIN, CPU4_MAX)
        val perm = if (locked) "444" else "644"
        nodes.forEach { n -> Shell.cmd("chmod $perm $n 2>/dev/null").exec() }
    }

    suspend fun setGpuFreqLocked(locked: Boolean): Unit = withContext(Dispatchers.IO) {
        val perm = if (locked) "444" else "644"
        Shell.cmd("chmod $perm $GPU_MIN 2>/dev/null; chmod $perm $GPU_MAX 2>/dev/null").exec()
    }

    suspend fun isFreqLocked(policy: Int): Boolean = withContext(Dispatchers.IO) {
        val node = if (policy == 0) CPU0_MAX else CPU4_MAX
        val perms = Shell.cmd("stat -c '%a' $node 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "644"
        perms == "444" || perms == "444"
    }

    suspend fun isGpuFreqLocked(): Boolean = withContext(Dispatchers.IO) {
        val perms = Shell.cmd("stat -c '%a' $GPU_MAX 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "644"
        perms == "444"
    }

        // ── Installed app list ────────────────────────────────────────────
    suspend fun getInstalledApps(): List<ThermalApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).also {
            it.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val flags: Int = if (Build.VERSION.SDK_INT >= 23) PackageManager.MATCH_ALL else 0
        val ris = if (Build.VERSION.SDK_INT >= 33)
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, flags)
        ris.mapNotNull { ri ->
            val ai  = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val pkg = ai.packageName ?: return@mapNotNull null
            val lbl = runCatching { pm.getApplicationLabel(ai).toString() }.getOrElse { pkg }
            ThermalApp(pkg, lbl, "")
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
}
