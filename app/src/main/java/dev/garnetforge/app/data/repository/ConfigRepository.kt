package dev.garnetforge.app.data.repository

import dev.garnetforge.app.data.model.GarnetConfig
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfigRepository {

    private val CONFIG = "/data/adb/garnetforge/config.prop"
    private val SET_CFG = "/data/adb/garnetforge/set_cfg.sh"

    suspend fun load(): GarnetConfig = withContext(Dispatchers.IO) {
        val raw = Shell.cmd("cat $CONFIG 2>/dev/null").exec().out.associate { line ->
            val eq = line.indexOf('=')
            if (eq > 0) line.substring(0, eq) to line.substring(eq + 1) else "" to ""
        }
        fun s(k: String) = raw[k] ?: ""
        fun i(k: String, d: Int) = raw[k]?.toIntOrNull() ?: d
        fun b(k: String) = raw[k] == "1"
        GarnetConfig(
            cpuPolicy0Governor     = s("cpu_policy0_governor").ifEmpty { "walt" },
            cpuPolicy0Min          = i("cpu_policy0_min",  691200),
            cpuPolicy0Max          = i("cpu_policy0_max",  1958400),
            cpuPolicy4Governor     = s("cpu_policy4_governor").ifEmpty { "walt" },
            cpuPolicy4Min          = i("cpu_policy4_min",  691200),
            cpuPolicy4Max          = i("cpu_policy4_max",  2400000),
            gpuMin                 = i("gpu_min",   295),
            gpuMax                 = i("gpu_max",   940),
            gpuPwrlevel            = i("gpu_pwrlevel", 0),
            gpuIdleTimer           = i("gpu_idle_timer", 80),
            vmSwappiness           = i("vm_swappiness", 0),
            vmDirtyRatio           = i("vm_dirty_ratio", 20),
            vmDirtyBackgroundRatio = i("vm_dirty_background_ratio", 5),
            vmVfsCachePressure     = i("vm_vfs_cache_pressure", 60),
            zramAlgo               = s("zram_algo").ifEmpty { "lz4" },
            zramSize               = raw["zram_size"]?.toLongOrNull() ?: 4294967296L,
            ioScheduler            = s("io_scheduler").ifEmpty { "bfq" },
            readAheadKb            = i("read_ahead_kb", 128),
            thermalProfile         = i("thermal_profile", 20),
            thermalBoost           = i("thermal_boost", 0),
            tcpAlgo                = s("tcp_algo").ifEmpty { "cubic" },
            netRxqueuelen          = i("net_rxqueuelen", 1000),
            nightMode              = b("night_mode"),
            thermalControl         = b("thermal_control"),
            perAppThermal          = b("per_app_thermal"),
        )
    }

    suspend fun setOnly(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd("sh $SET_CFG \"$key\" \"$value\" 2>/dev/null").exec()
    }

    suspend fun applyThermalProfile(sconfig: String): Unit = withContext(Dispatchers.IO) {
        Shell.cmd(
            "printf '%s' '$sconfig' > /sys/class/thermal/thermal_message/sconfig 2>/dev/null",
            "sh $SET_CFG thermal_profile '$sconfig' 2>/dev/null",
        ).exec()
    }
}
