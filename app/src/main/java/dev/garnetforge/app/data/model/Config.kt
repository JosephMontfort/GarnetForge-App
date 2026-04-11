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

// Used only by getInstalledApps() internally
data class ThermalApp(val pkg: String, val label: String, val profile: String = "")

// Per-app full profile — null field = don't override system setting
data class AppProfile(
    val pkg: String,
    val label: String = "",
    val enabled: Boolean = false,
    val cpu0Min: Int? = null,
    val cpu0Max: Int? = null,
    val cpu4Min: Int? = null,
    val cpu4Max: Int? = null,
    val gpuMin: Int? = null,
    val gpuMax: Int? = null,
    val thermal: String? = null,
    val gov0: String? = null,
    val gov4: String? = null,
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
