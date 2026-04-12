package dev.garnetforge.app.data.model

data class GarnetConfig(
    val cpuPolicy0Governor: String = "walt",
    val cpuPolicy0Min: Int = 691200,
    val cpuPolicy0Max: Int = 1958400,
    val cpuPolicy4Governor: String = "walt",
    val cpuPolicy4Min: Int = 691200,
    val cpuPolicy4Max: Int = 2400000,
    val gpuMin: Int = 295,
    val gpuMax: Int = 940,
    val gpuPwrlevel: Int = 0,
    val gpuIdleTimer: Int = 80,
    val vmSwappiness: Int = 0,
    val vmDirtyRatio: Int = 20,
    val vmDirtyBackgroundRatio: Int = 5,
    val vmVfsCachePressure: Int = 60,
    val zramAlgo: String = "lz4",
    val zramSize: Long = 4294967296L,
    val ioScheduler: String = "bfq",
    val readAheadKb: Int = 128,
    val thermalProfile: Int = 20,
    val thermalBoost: Int = 0,
    val tcpAlgo: String = "cubic",
    val netRxqueuelen: Int = 1000,
    val nightMode: Boolean = false,
    val thermalControl: Boolean = false,
    val perAppThermal: Boolean = false,
)

data class LiveStats(
    val cpu0FreqMhz: Int = 0,
    val cpu4FreqMhz: Int = 0,
    val gpuFreqMhz: Int = 0,
    val cpuTempC: Int = 0,
    val gpuTempC: Int = 0,
    val ddrTempC: Int = 0,
    val battTempC: Int = 0,
    val freeRamMb: Int = 0,
    val timeStr: String = "",
    val perCoreFreqMhz: List<Int> = List(8) { 0 },
)

data class ThermalApp(val pkg: String, val label: String, val profile: String = "")

// Per-app full profile
// offlinedCores: bitmask of cores to offline. core0 always stays online.
// Little cores 1-3 may be offlined (never all 3 at once — keep at least core0).
// Big cores 4-7 may be offlined (keep at least one big core = core4).
data class AppProfile(
    val pkg: String,
    val label: String = "",
    val enabled: Boolean = false,   // whether to APPLY the profile; settings are always stored
    val cpu0Min: Int? = null,
    val cpu0Max: Int? = null,
    val cpu4Min: Int? = null,
    val cpu4Max: Int? = null,
    val gpuMin: Int? = null,
    val gpuMax: Int? = null,
    val thermal: String? = null,
    val gov0: String? = null,
    val gov4: String? = null,
    val offlinedCores: Set<Int> = emptySet(),  // which cores to take offline (1-3, 5-7 only)
    val presetId: String? = null,   // null = custom; non-null = linked to a ProfilePreset.id
)

enum class ThermalProfile(val sconfig: String, val label: String) {
    DEFAULT("20", "Default"),
    GAMING("9", "Gaming"),
    CAMERA("12", "Camera"),
    CHARGING("32", "Charging");
    companion object {
        fun fromSconfig(s: String?) = values().find { it.sconfig == s } ?: DEFAULT
    }
}

// Live node values — read from sysfs when tuning tab opens
data class LiveNodeValues(
    val vmSwappiness: Int = -1,
    val vmDirtyRatio: Int = -1,
    val vmDirtyBackgroundRatio: Int = -1,
    val vmVfsCachePressure: Int = -1,
    val readAheadKb: Int = -1,
    val tcpAlgo: String = "",
    val netRxqueuelen: Int = -1,
    val gpuPwrlevel: Int = -1,
    val gpuIdleTimer: Int = -1,
    val thermalBoost: Boolean = false,
)

// Default values — read from defaults.prop (captured after clean boot)
data class NodeDefaults(
    val vmSwappiness: Int = 100,
    val vmDirtyRatio: Int = 20,
    val vmDirtyBackgroundRatio: Int = 5,
    val vmVfsCachePressure: Int = 100,
    val readAheadKb: Int = 512,
    val tcpAlgo: String = "cubic",
    val netRxqueuelen: Int = 1000,
    val gpuIdleTimer: Int = 64,
    val thermalBoostDefault: Boolean = false,
)

// Preset profile — named template reusable across apps
data class ProfilePreset(
    val id: String,           // unique id (timestamp string)
    val name: String,
    val cpu0Min: Int? = null,
    val cpu0Max: Int? = null,
    val cpu4Min: Int? = null,
    val cpu4Max: Int? = null,
    val gpuMin: Int? = null,
    val gpuMax: Int? = null,
    val thermal: String? = null,
    val gov0: String? = null,
    val gov4: String? = null,
    val offlinedCores: Set<Int> = emptySet(),
)
