package dev.garnetforge.app.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.garnetforge.app.data.model.*
import dev.garnetforge.app.data.repository.ConfigRepository
import dev.garnetforge.app.data.repository.SysfsRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "ui_prefs")

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val configRepo = ConfigRepository()
    val sysfsRepo  = SysfsRepository(app)

    private val dataStore = app.dataStore
    val themeMode: StateFlow<Int> = dataStore.data.map { it[intPreferencesKey("theme_mode")] ?: 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val blurEnabled: StateFlow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("blur_enabled")] ?: true }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setThemeMode(mode: Int) = viewModelScope.launch { dataStore.edit { it[intPreferencesKey("theme_mode")] = mode } }
    fun setBlurEnabled(enabled: Boolean) = viewModelScope.launch { dataStore.edit { it[booleanPreferencesKey("blur_enabled")] = enabled } }

    private val _config      = MutableStateFlow(GarnetConfig())
    private val _stats       = MutableStateFlow(LiveStats())
    private val _sconfig     = MutableStateFlow("0")
    private val _apps        = MutableStateFlow<List<ThermalApp>>(emptyList())
    private val _thermalMap  = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _rootChecking= MutableStateFlow(true)
    private val _rootOk      = MutableStateFlow(false)
    private val _toast       = MutableStateFlow<String?>(null)
    private val _appsLoading = MutableStateFlow(false)
    private val _deviceInfo  = MutableStateFlow(DeviceInfo())
    private val _coreStates  = MutableStateFlow(List(8) { true })

    val config      : StateFlow<GarnetConfig>       = _config.asStateFlow()
    val stats       : StateFlow<LiveStats>           = _stats.asStateFlow()
    val sconfig     : StateFlow<String>              = _sconfig.asStateFlow()
    val apps        : StateFlow<List<ThermalApp>>    = _apps.asStateFlow()
    val thermalMap  : StateFlow<Map<String, String>> = _thermalMap.asStateFlow()
    val rootChecking: StateFlow<Boolean>             = _rootChecking.asStateFlow()
    val rootOk      : StateFlow<Boolean>             = _rootOk.asStateFlow()
    val toast       : StateFlow<String?>             = _toast.asStateFlow()
    val appsLoading : StateFlow<Boolean>             = _appsLoading.asStateFlow()
    val deviceInfo  : StateFlow<DeviceInfo>          = _deviceInfo.asStateFlow()
    val coreStates  : StateFlow<List<Boolean>>       = _coreStates.asStateFlow()

    // Derived: per-core freqs from LiveStats
    val perCoreFreqMhz: StateFlow<List<Int>> = _stats.map { it.perCoreFreqMhz }
        .stateIn(viewModelScope, SharingStarted.Eagerly, List(8) { 0 })

    private var pollJob: Job? = null

    init { checkRoot() }

    fun checkRoot() = viewModelScope.launch {
        _rootChecking.value = true
        val isRoot = withContext(Dispatchers.IO) {
            runCatching {
                val r = Shell.cmd("id").exec()
                r.isSuccess && r.out.joinToString().contains("uid=0")
            }.getOrDefault(false)
        }
        _rootOk.value = isRoot
        _rootChecking.value = false
        if (isRoot) onRootConfirmed()
    }

    private fun onRootConfirmed() = viewModelScope.launch {
        val app = getApplication<Application>()
        val vc = runCatching {
            if (Build.VERSION.SDK_INT >= 28)
                app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt()
            else @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(app.packageName, 0).versionCode
        }.getOrDefault(1)
        dev.garnetforge.app.root.ScriptManager.ensureInstalled(app, vc)
        refreshConfig()
        refreshSconfig()
        refreshCoreStates()
        loadDeviceInfo()
        startPolling()
    }

    private fun refreshConfig() = viewModelScope.launch {
        runCatching { _config.value = configRepo.load() }
    }

    private fun refreshSconfig() = viewModelScope.launch {
        runCatching { _sconfig.value = sysfsRepo.getCurrentSconfig() }
    }

    fun onTabSelected() = viewModelScope.launch {
        runCatching {
            refreshConfig()
            refreshSconfig()
            refreshCoreStates()
        }
    }

    fun refreshCoreStates() = viewModelScope.launch {
        runCatching {
            val states = withContext(Dispatchers.IO) {
                (0..7).map { core ->
                    if (core == 0) true
                    else {
                        val v = Shell.cmd("cat /sys/devices/system/cpu/cpu${core}/online 2>/dev/null")
                            .exec().out.firstOrNull()?.trim()
                        v == "1"
                    }
                }
            }
            _coreStates.value = states
        }
    }

    fun toggleCore(core: Int) = viewModelScope.launch {
        if (core == 0) return@launch
        val current = _coreStates.value
        val newState = !current[core]
        _coreStates.value = current.toMutableList().also { it[core] = newState }
        runCatching {
            val v = if (newState) "1" else "0"
            Shell.cmd("echo $v > /sys/devices/system/cpu/cpu${core}/online 2>/dev/null").exec()
        }.onFailure {
            _coreStates.value = current
        }
    }

    private fun loadDeviceInfo() = viewModelScope.launch {
        runCatching {
            _deviceInfo.value = withContext(Dispatchers.IO) {
                fun prop(k: String) = Shell.cmd("getprop $k").exec().out.firstOrNull()?.trim() ?: ""
                fun cat(p: String)  = Shell.cmd("cat $p 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
                val hw = cat("/proc/cpuinfo").lines().find { it.startsWith("Hardware") }
                    ?.substringAfter(":")?.trim() ?: ""
                val soc = if (hw.contains("7435", ignoreCase = true)) "Snapdragon 4 Gen 2 (SM7435)"
                          else hw.ifEmpty { prop("ro.board.platform") }
                DeviceInfo(
                    deviceName     = "${prop("ro.product.brand")} ${prop("ro.product.model")}".trim(),
                    codename       = prop("ro.product.device"),
                    socModel       = soc.ifEmpty { "Snapdragon 4 Gen 2 (SM7435)" },
                    kernelVersion  = cat("/proc/version").let { v ->
                        Regex("Linux version ([\\d.\\-a-zA-Z]+)").find(v)?.groupValues?.get(1) ?: v.take(40) },
                    androidVersion = prop("ro.build.version.release"),
                    romName        = "",
                    cpuHardware    = hw,
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                runCatching {
                    _stats.value   = sysfsRepo.getLiveStats()
                    _sconfig.value = sysfsRepo.getCurrentSconfig()
                    if (++tick >= 15) { tick = 0; _config.value = configRepo.load() }
                }
                delay(1000)
            }
        }
    }

    fun setConfig(key: String, value: String) = viewModelScope.launch {
        _config.value = applyOptimistic(_config.value, key, value)
        launch { writeSysfs(key, value) }
        launch { runCatching { configRepo.setOnly(key, value) } }
    }

    private suspend fun writeSysfs(key: String, value: String) = withContext(Dispatchers.IO) {
        runCatching {
            when (key) {
                "cpu_policy0_max"      -> sysfsRepo.writeCpuFreq(0, null, value.toIntOrNull())
                "cpu_policy0_min"      -> sysfsRepo.writeCpuFreq(0, value.toIntOrNull(), null)
                "cpu_policy4_max"      -> sysfsRepo.writeCpuFreq(4, null, value.toIntOrNull())
                "cpu_policy4_min"      -> sysfsRepo.writeCpuFreq(4, value.toIntOrNull(), null)
                "cpu_policy0_governor" -> sysfsRepo.writeCpuGovernor(0, value)
                "cpu_policy4_governor" -> sysfsRepo.writeCpuGovernor(4, value)
                "gpu_max"              -> sysfsRepo.writeGpuFreq(null, value.toIntOrNull())
                "gpu_min"              -> sysfsRepo.writeGpuFreq(value.toIntOrNull(), null)
                "vm_swappiness"        -> sysfsRepo.writeSwappiness(value.toIntOrNull() ?: return@runCatching)
                "vm_dirty_ratio"       -> sysfsRepo.writeVmParam("/proc/sys/vm/dirty_ratio", value)
                "vm_dirty_background_ratio" -> sysfsRepo.writeVmParam("/proc/sys/vm/dirty_background_ratio", value)
                "vm_vfs_cache_pressure"-> sysfsRepo.writeVmParam("/proc/sys/vm/vfs_cache_pressure", value)
                "zram_size"            -> {
                    val cfg = _config.value
                    sysfsRepo.writeZram(value.toLongOrNull() ?: return@runCatching, cfg.zramAlgo)
                }
                "zram_algo"            -> {
                    val cfg = _config.value
                    sysfsRepo.writeZram(cfg.zramSize, value)
                }
                "io_scheduler"         -> sysfsRepo.writeIoScheduler(value)
                "read_ahead_kb"        -> sysfsRepo.writeReadAhead(value.toIntOrNull() ?: return@runCatching)
                "tcp_algo"             -> sysfsRepo.writeTcp(value)
                "net_rxqueuelen"       -> sysfsRepo.writeNetRxqueuelen(value.toIntOrNull() ?: return@runCatching)
            }
        }
    }

    fun applyThermalProfile(profile: ThermalProfile) = viewModelScope.launch {
        _sconfig.value = profile.sconfig
        runCatching { configRepo.applyThermalProfile(profile.sconfig) }
    }

    fun clearRam() = viewModelScope.launch {
        runCatching { sysfsRepo.clearRam(); toast("RAM cleared") }
    }

    fun loadApps() = viewModelScope.launch {
        if (_appsLoading.value) return@launch
        _appsLoading.value = true
        runCatching {
            val tMap = sysfsRepo.getThermalApps()
            _thermalMap.value = tMap
            val list = sysfsRepo.getInstalledApps()
            _apps.value = list.map { it.copy(profile = tMap[it.pkg] ?: "") }
        }.onFailure { toast("App list error: ${it.message}") }
        _appsLoading.value = false
    }

    fun setAppThermal(pkg: String, profile: String) = viewModelScope.launch {
        val m = _thermalMap.value.toMutableMap()
        if (profile.isEmpty()) m.remove(pkg) else m[pkg] = profile
        _thermalMap.value = m
        _apps.value = _apps.value.map { if (it.pkg == pkg) it.copy(profile = profile) else it }
        runCatching { sysfsRepo.setAppThermal(pkg, profile) }
    }

    fun toast(msg: String) { _toast.value = msg }
    fun clearToast()       { _toast.value = null }
    override fun onCleared() { pollJob?.cancel(); super.onCleared() }

    private fun applyOptimistic(c: GarnetConfig, key: String, value: String) = when (key) {
        "night_mode"                   -> c.copy(nightMode            = value == "1")
        "thermal_control"              -> c.copy(thermalControl       = value == "1")
        "per_app_thermal"              -> c.copy(perAppThermal        = value == "1")
        "thermal_boost"                -> c.copy(thermalBoost         = value.toIntOrNull() ?: c.thermalBoost)
        "cpu_policy0_min"              -> c.copy(cpuPolicy0Min        = value.toIntOrNull() ?: c.cpuPolicy0Min)
        "cpu_policy0_max"              -> c.copy(cpuPolicy0Max        = value.toIntOrNull() ?: c.cpuPolicy0Max)
        "cpu_policy4_min"              -> c.copy(cpuPolicy4Min        = value.toIntOrNull() ?: c.cpuPolicy4Min)
        "cpu_policy4_max"              -> c.copy(cpuPolicy4Max        = value.toIntOrNull() ?: c.cpuPolicy4Max)
        "cpu_policy0_governor"         -> c.copy(cpuPolicy0Governor   = value)
        "cpu_policy4_governor"         -> c.copy(cpuPolicy4Governor   = value)
        "gpu_min"                      -> c.copy(gpuMin               = value.toIntOrNull() ?: c.gpuMin)
        "gpu_max"                      -> c.copy(gpuMax               = value.toIntOrNull() ?: c.gpuMax)
        "gpu_pwrlevel"                 -> c.copy(gpuPwrlevel          = value.toIntOrNull() ?: c.gpuPwrlevel)
        "vm_swappiness"                -> c.copy(vmSwappiness         = value.toIntOrNull() ?: c.vmSwappiness)
        "vm_dirty_ratio"               -> c.copy(vmDirtyRatio         = value.toIntOrNull() ?: c.vmDirtyRatio)
        "vm_dirty_background_ratio"    -> c.copy(vmDirtyBackgroundRatio = value.toIntOrNull() ?: c.vmDirtyBackgroundRatio)
        "vm_vfs_cache_pressure"        -> c.copy(vmVfsCachePressure   = value.toIntOrNull() ?: c.vmVfsCachePressure)
        "zram_size"                    -> c.copy(zramSize             = value.toLongOrNull() ?: c.zramSize)
        "zram_algo"                    -> c.copy(zramAlgo             = value)
        "io_scheduler"                 -> c.copy(ioScheduler          = value)
        "read_ahead_kb"                -> c.copy(readAheadKb          = value.toIntOrNull() ?: c.readAheadKb)
        "tcp_algo"                     -> c.copy(tcpAlgo              = value)
        "net_rxqueuelen"               -> c.copy(netRxqueuelen        = value.toIntOrNull() ?: c.netRxqueuelen)
        else                           -> c
    }
}
