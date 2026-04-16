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

        // Dynamic — populated from nodes.prop at runtime
        var CPU0_MAX   = CPU0_MAX_FB; var CPU0_MIN = CPU0_MIN_FB; var CPU0_GOV = CPU0_GOV_FB
        var CPU4_MAX   = CPU4_MAX_FB; var CPU4_MIN = CPU4_MIN_FB; var CPU4_GOV = CPU4_GOV_FB
        var CPU0_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_min_freq"
        var CPU4_INFO_MIN = "/sys/devices/system/cpu/cpufreq/policy4/cpuinfo_min_freq"
        var GPU_MAX    = GPU_MAX_FB; var GPU_MIN = GPU_MIN_FB; var GPU_CUR = GPU_CUR_FB
        var GPU_PWRLEVEL = GPU_PL_FB; var GPU_IDLE_TIMER = GPU_IT_FB
        var THERMAL_BOOST = THERMAL_BOOST_FB
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

        android.util.Log.i("GarnetForge", "Nodes loaded: little=$lp big=$bpol gpu=$gpuCur")
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

// ── Live stats ────────────────────────────────────────────────────
    // ── Live stats ────────────────────────────────────────────────
    // ── Live stats ────────────────────────────────────────────────────
    suspend fun getLiveStats(): LiveStats = withContext(Dispatchers.IO) {
        val np = Shell.cmd("cat $NODES 2>/dev/null").exec().out.associate { line ->
            val eq = line.indexOf('='); if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
        }
        fun tzp(k: String, fb: String) = np[k]?.trim()?.takeIf { it.startsWith("/") } ?: fb
        val cpuTz = tzp("thermal_zone_cpu",  tzp("thermal_zone_cpu_fb",  "/sys/class/thermal/thermal_zone67/temp"))
        val gpuTz = tzp("thermal_zone_gpu",  tzp("thermal_zone_gpu_fb",  "/sys/class/thermal/thermal_zone31/temp"))
        val ddrTz = tzp("thermal_zone_ddr",  tzp("thermal_zone_ddr_fb",  "/sys/class/thermal/thermal_zone43/temp"))
        val battN = tzp("battery_temp", "/sys/class/power_supply/battery/temp")
        val coreCount = np["cpu_core_count"]?.trim()?.toIntOrNull()?.coerceIn(1, 16) ?: 8
        val litN = CPU0_MAX.replace("scaling_max_freq", "scaling_cur_freq")
        val bigN = CPU4_MAX.replace("scaling_max_freq", "scaling_cur_freq")

        val clRaw = Shell.cmd(
            "printf '%s|%s|%s|%s|%s|%s|%s|%s' " +
            "\"$(cat $litN 2>/dev/null)\" " +
            "\"$(cat $bigN 2>/dev/null)\" " +
            "\"$(cat $GPU_CUR 2>/dev/null)\" " +
            "\"$(cat $cpuTz 2>/dev/null)\" " +
            "\"$(cat $gpuTz 2>/dev/null)\" " +
            "\"$(cat $ddrTz 2>/dev/null)\" " +
            "\"$(cat $battN 2>/dev/null)\" " +
            "\"$(awk '/MemAvailable/{print \$2}' /proc/meminfo 2>/dev/null)\""
        ).exec().out.firstOrNull() ?: ""

        val perCoreFreqs = (0 until coreCount).map { c ->
            val n = np["cpu${c}_cur_freq"]?.trim()
                ?: "/sys/devices/system/cpu/cpu${c}/cpufreq/scaling_cur_freq"
            Shell.cmd("cat $n 2>/dev/null").exec().out.firstOrNull()
                ?.trim()?.toLongOrNull()?.div(1000)?.toInt() ?: 0
        }.let { if (it.size < 8) it + List(8 - it.size) { 0 } else it }

        val cp = clRaw.split("|")
        fun ck(i: Int) = cp.getOrNull(i)?.trim()?.toLongOrNull() ?: 0L
        val h = LocalTime.now().hour
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "AM" else "PM"
        LiveStats(
            cpu0FreqMhz    = (ck(0) / 1000).toInt(),
            cpu4FreqMhz    = (ck(1) / 1000).toInt(),
            gpuFreqMhz     = (ck(2) / 1_000_000).toInt(),
            cpuTempC       = (ck(3) / 1000).toInt(),
            gpuTempC       = (ck(4) / 1000).toInt(),
            ddrTempC       = (ck(5) / 1000).toInt(),
            battTempC      = (ck(6) / 10).toInt(),
            freeRamMb      = (ck(7) / 1024).toInt(),
            timeStr        = "$h12:${LocalTime.now().minute.toString().padStart(2,'0')} $ampm",
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
        Shell.cmd("cat $out 2>/dev/null").exec().out.joinToString("
")
    }

    fun getDiagnosticFilePath(): String = "$INSTALL_DIR/diagnostic_report.txt"

    // ── Network speed test (using shell dd + /dev/urandom for throughput) ─
    suspend fun runSpeedTest(): Pair<Float, Float> = withContext(Dispatchers.IO) {
        // Download test: time downloading from a public test endpoint
        val dlRaw = Shell.cmd(
            "START=6262764059; " +
            "curl -s -o /dev/null --max-time 8 --connect-timeout 3 " +
            "  https://speed.cloudflare.com/__down?bytes=10000000 2>/dev/null; " +
            "END=6262764063; " +
            "printf '%d' """
        ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
        val dlMbps = if (dlRaw > 0) (10_000_000f * 8 / 1_000_000f) / (dlRaw / 1000f) else -1f

        // Upload test: time uploading 2MB of zeros
        val ulRaw = Shell.cmd(
            "START=6262764067; " +
            "dd if=/dev/urandom bs=1024 count=2048 2>/dev/null | " +
            "curl -s -o /dev/null --max-time 8 --connect-timeout 3 " +
            "  -X POST -d @- https://speed.cloudflare.com/__up 2>/dev/null; " +
            "END=6262764072; " +
            "printf '%d' """
        ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
        val ulMbps = if (ulRaw > 0) (2_000_000f * 8 / 1_000_000f) / (ulRaw / 1000f) else -1f

        Pair(dlMbps, ulMbps)
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
