package dev.garnetforge.app.viewmodel

import dev.garnetforge.app.DiagnosticState
import dev.garnetforge.app.SpeedTestState
import android.app.Application
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
    private val _presets = MutableStateFlow<List<ProfilePreset>>(emptyList())
    val presets: StateFlow<List<ProfilePreset>> = _presets.asStateFlow()


    private val configRepo = ConfigRepository()
    val sysfsRepo = SysfsRepository(app)

    private val dataStore = app.dataStore
    val themeMode: StateFlow<Int> = dataStore.data.map { it[intPreferencesKey("theme_mode")] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val blurEnabled: StateFlow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("blur_enabled")] ?: false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val accentTheme: StateFlow<Int> = dataStore.data.map { it[intPreferencesKey("accent_theme")] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setThemeMode(m: Int) = viewModelScope.launch { dataStore.edit { it[intPreferencesKey("theme_mode")] = m } }
    fun setBlurEnabled(e: Boolean) = viewModelScope.launch { dataStore.edit { it[booleanPreferencesKey("blur_enabled")] = e } }
    fun setAccentTheme(i: Int) = viewModelScope.launch { dataStore.edit { it[intPreferencesKey("accent_theme")] = i } }

    private val _config       = MutableStateFlow(GarnetConfig())
    private val _stats        = MutableStateFlow(LiveStats())
    private val _sconfig      = MutableStateFlow("20")
    private val _apps         = MutableStateFlow<List<AppProfile>>(emptyList())
    private val _appProfileMap= MutableStateFlow<Map<String, AppProfile>>(emptyMap())
    private val _rootChecking = MutableStateFlow(true)
    private val _rootOk       = MutableStateFlow(false)
    private val _toast        = MutableStateFlow<String?>(null)
    private val _appsLoading  = MutableStateFlow(false)
    private val _deviceInfo   = MutableStateFlow(DeviceInfo())
    private val _coreStates   = MutableStateFlow(List(8) { true })
    private val _dashboardReady= MutableStateFlow(false)
    val dashboardReady = _dashboardReady.asStateFlow()
    private val _liveNodes    = MutableStateFlow(dev.garnetforge.app.data.model.LiveNodeValues())
    private val _nodeDefaults = MutableStateFlow(dev.garnetforge.app.data.model.NodeDefaults())
    private val _availFreqsL  = MutableStateFlow<List<Int>>(emptyList())
    private val _availFreqsB  = MutableStateFlow<List<Int>>(emptyList())
    private val _availFreqsGpu= MutableStateFlow<List<Int>>(emptyList())

    val config        : StateFlow<GarnetConfig>       = _config.asStateFlow()
    val stats         : StateFlow<LiveStats>           = _stats.asStateFlow()
    val sconfig       : StateFlow<String>              = _sconfig.asStateFlow()
    val apps          : StateFlow<List<AppProfile>>    = _apps.asStateFlow()
    val appProfileMap : StateFlow<Map<String, AppProfile>> = _appProfileMap.asStateFlow()
    val rootChecking  : StateFlow<Boolean>             = _rootChecking.asStateFlow()
    val rootOk        : StateFlow<Boolean>             = _rootOk.asStateFlow()
    val toast         : StateFlow<String?>             = _toast.asStateFlow()
    val appsLoading   : StateFlow<Boolean>             = _appsLoading.asStateFlow()
    val deviceInfo    : StateFlow<DeviceInfo>          = _deviceInfo.asStateFlow()
    val coreStates    : StateFlow<List<Boolean>>       = _coreStates.asStateFlow()
    val availFreqsL   : StateFlow<List<Int>>           = _availFreqsL.asStateFlow()
    val availFreqsB   : StateFlow<List<Int>>           = _availFreqsB.asStateFlow()
    val availFreqsGpu : StateFlow<List<Int>>           = _availFreqsGpu.asStateFlow()
    val liveNodes     : StateFlow<LiveNodeValues>      = _liveNodes.asStateFlow()
    val nodeDefaults  : StateFlow<NodeDefaults>        = _nodeDefaults.asStateFlow()

    val perCoreFreqMhz: StateFlow<List<Int>> = _stats.map { it.perCoreFreqMhz }
        .stateIn(viewModelScope, SharingStarted.Eagerly, List(8) { 0 })

    private var pollJob: Job? = null

    init { checkRoot() }

    fun checkRoot() = viewModelScope.launch {
        _rootChecking.value = true
        val isRoot = withContext(Dispatchers.IO) {
            runCatching { Shell.cmd("id").exec().let { it.isSuccess && it.out.joinToString().contains("uid=0") } }.getOrDefault(false)
        }
        _rootOk.value = isRoot
        _rootChecking.value = false
        if (isRoot) onRootConfirmed()
    }

    private fun onRootConfirmed() = viewModelScope.launch {
        val app = getApplication<Application>()
        val vc = runCatching {
            if (Build.VERSION.SDK_INT >= 28) app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode.toInt()
            else @Suppress("DEPRECATION") app.packageManager.getPackageInfo(app.packageName, 0).versionCode
        }.getOrDefault(1)
        dev.garnetforge.app.root.ScriptManager.ensureInstalled(app, vc)
        refreshConfig()
        refreshSconfig()
        refreshCoreStates()
        loadDeviceInfo()
        loadAvailableFreqs()
        loadNodeDefaults()
        viewModelScope.launch { runCatching { sysfsRepo.loadNodePaths() } }
        refreshLiveNodes()
        startPolling()
    }

    private fun loadNodeDefaults() = viewModelScope.launch {
        runCatching { _nodeDefaults.value = sysfsRepo.readNodeDefaults() }
    }

    fun refreshLiveNodes() = viewModelScope.launch {
        runCatching { _liveNodes.value = sysfsRepo.readLiveNodes() }
    }

    private fun loadAvailableFreqs() = viewModelScope.launch {
        runCatching {
            _availFreqsL.value   = sysfsRepo.getAvailableFreqsKhz(0)
            _availFreqsB.value   = sysfsRepo.getAvailableFreqsKhz(4)
            _availFreqsGpu.value = sysfsRepo.getAvailableGpuFreqsMhz()
        }
    }

    private fun refreshConfig() = viewModelScope.launch { runCatching { _config.value = configRepo.load() } }
    private fun refreshSconfig() = viewModelScope.launch { runCatching { _sconfig.value = sysfsRepo.getCurrentSconfig() } }

    fun onTabSelected() = viewModelScope.launch {
        runCatching { refreshConfig(); refreshSconfig(); refreshCoreStates(); refreshLiveNodes() }
    }

    fun refreshCoreStates() = viewModelScope.launch {
        runCatching {
            _coreStates.value = withContext(Dispatchers.IO) {
                (0..7).map { c ->
                    if (c == 0) true
                    else Shell.cmd("cat /sys/devices/system/cpu/cpu${c}/online 2>/dev/null").exec().out.firstOrNull()?.trim() == "1"
                }
            }
        }
    }

    fun toggleCore(core: Int) = viewModelScope.launch {
        if (core == 0) return@launch
        val cur = _coreStates.value; val ns = !cur[core]
        _coreStates.value = cur.toMutableList().also { it[core] = ns }
        runCatching {
            Shell.cmd("echo ${if (ns) "1" else "0"} > /sys/devices/system/cpu/cpu${core}/online 2>/dev/null").exec()
        }.onFailure { _coreStates.value = cur }
    }

    private fun loadDeviceInfo() = viewModelScope.launch {
        runCatching {
            _deviceInfo.value = withContext(Dispatchers.IO) {
                fun prop(k: String) = Shell.cmd("getprop $k").exec().out.firstOrNull()?.trim() ?: ""
                fun cat(p: String)  = Shell.cmd("cat $p 2>/dev/null").exec().out.firstOrNull()?.trim() ?: ""
                val hw = cat("/proc/cpuinfo").lines().find { it.startsWith("Hardware") }?.substringAfter(":")?.trim() ?: ""
                val soc = if (hw.contains("7435", true)) "Snapdragon 4 Gen 2 (SM7435)" else hw.ifEmpty { prop("ro.board.platform") }
                DeviceInfo(
                    deviceName    = "${prop("ro.product.brand")} ${prop("ro.product.model")}".trim(),
                    codename      = prop("ro.product.device"),
                    socModel      = soc.ifEmpty { "Snapdragon 4 Gen 2 (SM7435)" },
                    kernelVersion = cat("/proc/version").let { v -> Regex("Linux version ([\\d.\\-a-zA-Z]+)").find(v)?.groupValues?.get(1) ?: v.take(40) },
                    androidVersion= prop("ro.build.version.release"),
                    romName       = "", cpuHardware = hw,
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
                    val s = sysfsRepo.getLiveStats()
                    _stats.value = s
                    if (!_dashboardReady.value && s.timeStr.isNotEmpty() && s.freeRamMb > 0) _dashboardReady.value = true
                    _sconfig.value = sysfsRepo.getCurrentSconfig()
                    if (++tick >= 15) { tick = 0; _config.value = configRepo.load() }
                }
                delay(1000)
            }
        }
    }

    fun setConfig(key: String, value: String) = viewModelScope.launch {
        _config.value = applyOptimistic(_config.value, key, value)
        _liveNodes.value = applyLiveOptimistic(_liveNodes.value, key, value)
        launch { writeSysfs(key, value) }
        launch { runCatching { configRepo.setOnly(key, value) } }
    }

    private fun applyLiveOptimistic(ln: LiveNodeValues, key: String, value: String) = when (key) {
        "vm_swappiness"             -> ln.copy(vmSwappiness           = value.toIntOrNull() ?: ln.vmSwappiness)
        "vm_dirty_ratio"            -> ln.copy(vmDirtyRatio           = value.toIntOrNull() ?: ln.vmDirtyRatio)
        "vm_dirty_background_ratio" -> ln.copy(vmDirtyBackgroundRatio = value.toIntOrNull() ?: ln.vmDirtyBackgroundRatio)
        "vm_vfs_cache_pressure"     -> ln.copy(vmVfsCachePressure     = value.toIntOrNull() ?: ln.vmVfsCachePressure)
        "net_rxqueuelen"            -> ln.copy(netRxqueuelen          = value.toIntOrNull() ?: ln.netRxqueuelen)
        "gpu_pwrlevel"              -> ln.copy(gpuPwrlevel            = value.toIntOrNull() ?: ln.gpuPwrlevel)
        "gpu_idle_timer"            -> ln.copy(gpuIdleTimer           = value.toIntOrNull() ?: ln.gpuIdleTimer)
        "thermal_boost"             -> ln.copy(thermalBoost           = value == "1")
        "read_ahead_kb"             -> ln.copy(readAheadKb            = value.toIntOrNull() ?: ln.readAheadKb)
        "tcp_algo"                  -> ln.copy(tcpAlgo                = value)
        else -> ln
    }

    private suspend fun writeSysfs(key: String, value: String) = withContext(Dispatchers.IO) {
        runCatching {
            when (key) {
                "cpu_policy0_max"           -> sysfsRepo.writeCpuFreq(0, null, value.toIntOrNull())
                "cpu_policy0_min"           -> sysfsRepo.writeCpuFreq(0, value.toIntOrNull(), null)
                "cpu_policy4_max"           -> sysfsRepo.writeCpuFreq(4, null, value.toIntOrNull())
                "cpu_policy4_min"           -> sysfsRepo.writeCpuFreq(4, value.toIntOrNull(), null)
                "cpu_policy0_governor"      -> sysfsRepo.writeCpuGovernor(0, value)
                "cpu_policy4_governor"      -> sysfsRepo.writeCpuGovernor(4, value)
                "gpu_max"                   -> sysfsRepo.writeGpuFreq(null, value.toIntOrNull())
                "gpu_min"                   -> sysfsRepo.writeGpuFreq(value.toIntOrNull(), null)
                "vm_swappiness"             -> sysfsRepo.writeSwappiness(value.toIntOrNull() ?: return@runCatching)
                "vm_dirty_ratio"            -> sysfsRepo.writeVmParam("/proc/sys/vm/dirty_ratio", value)
                "vm_dirty_background_ratio" -> sysfsRepo.writeVmParam("/proc/sys/vm/dirty_background_ratio", value)
                "vm_vfs_cache_pressure"     -> sysfsRepo.writeVmParam("/proc/sys/vm/vfs_cache_pressure", value)
                "zram_size"                 -> sysfsRepo.writeZram(value.toLongOrNull() ?: return@runCatching, _config.value.zramAlgo)
                "zram_algo"                 -> sysfsRepo.writeZram(_config.value.zramSize, value)
                "io_scheduler"              -> sysfsRepo.writeIoScheduler(value)
                "read_ahead_kb"             -> sysfsRepo.writeReadAhead(value.toIntOrNull() ?: return@runCatching)
                "tcp_algo"                  -> sysfsRepo.writeTcp(value)
                "net_rxqueuelen"            -> sysfsRepo.writeNetRxqueuelen(value.toIntOrNull() ?: return@runCatching)
                "gpu_pwrlevel"              -> sysfsRepo.writeGpuPwrlevel(value.toIntOrNull() ?: return@runCatching)
                "gpu_idle_timer"            -> sysfsRepo.writeGpuIdleTimer(value.toIntOrNull() ?: return@runCatching)
                "thermal_boost"             -> sysfsRepo.writeThermalBoost(value == "1")
            }
        }
    }

    fun applyThermalProfile(profile: ThermalProfile) = viewModelScope.launch {
        _sconfig.value = profile.sconfig
        runCatching { configRepo.applyThermalProfile(profile.sconfig) }
    }

    fun clearRam() = viewModelScope.launch { runCatching { sysfsRepo.clearRam(); toast("RAM cleared") } }

    // ── Entropy ───────────────────────────────────────────────────────
    private val _entropyLevel = MutableStateFlow(-1)
    val entropyLevel: StateFlow<Int> = _entropyLevel.asStateFlow()

    // Frequency lock state
    private val _littleFreqLocked = MutableStateFlow(false)
    private val _bigFreqLocked    = MutableStateFlow(false)
    private val _gpuFreqLocked    = MutableStateFlow(false)
    val littleFreqLocked: StateFlow<Boolean> = _littleFreqLocked.asStateFlow()
    val bigFreqLocked   : StateFlow<Boolean> = _bigFreqLocked.asStateFlow()
    val gpuFreqLocked   : StateFlow<Boolean> = _gpuFreqLocked.asStateFlow()

    fun toggleFreqLock(policy: Int) = viewModelScope.launch {
        val cur = if (policy==0) _littleFreqLocked.value else _bigFreqLocked.value
        val next = !cur
        runCatching { sysfsRepo.setFreqLocked(policy, next) }
        if (policy==0) _littleFreqLocked.value=next else _bigFreqLocked.value=next
    }
    fun toggleGpuFreqLock() = viewModelScope.launch {
        val next = !_gpuFreqLocked.value
        runCatching { sysfsRepo.setGpuFreqLocked(next) }
        _gpuFreqLocked.value = next
    }
    fun refreshLockStates() = viewModelScope.launch {
        runCatching {
            _littleFreqLocked.value = sysfsRepo.isFreqLocked(0)
            _bigFreqLocked.value    = sysfsRepo.isFreqLocked(4)
            _gpuFreqLocked.value    = sysfsRepo.isGpuFreqLocked()
        }
    }

    fun boostEntropy() = viewModelScope.launch {
        runCatching { sysfsRepo.boostEntropy(); delay(500); refreshEntropy() }
        toast("Entropy boosted")
    }
    fun refreshEntropy() = viewModelScope.launch {
        runCatching { _entropyLevel.value = sysfsRepo.getEntropyAvailable() }
    }

    // ── Speed test ────────────────────────────────────────────────────
    private val _speedTestState = MutableStateFlow<SpeedTestState>(SpeedTestState.Idle)
    val speedTestState: StateFlow<SpeedTestState> = _speedTestState.asStateFlow()

    fun runSpeedTest() = viewModelScope.launch {
        _speedTestState.value = SpeedTestState.Running
        val result = runCatching { sysfsRepo.runSpeedTest() }
        _speedTestState.value = result.fold(
            { (dl, ul) -> SpeedTestState.Done(dl, ul) },
            { SpeedTestState.Error(it.message ?: "Failed") }
        )
    }

    // ── Diagnostic ────────────────────────────────────────────────────
    private val _diagnosticState = MutableStateFlow<DiagnosticState>(DiagnosticState.Idle)
    val diagnosticState: StateFlow<DiagnosticState> = _diagnosticState.asStateFlow()

    fun runDiagnostic() = viewModelScope.launch {
        _diagnosticState.value = DiagnosticState.Running
        val result = runCatching { sysfsRepo.runDiagnostic() }
        _diagnosticState.value = result.fold(
            { DiagnosticState.Done(sysfsRepo.getDiagnosticFilePath()) },
            { DiagnosticState.Error(it.message ?: "Failed") }
        )
    }

    fun loadApps() = viewModelScope.launch {
        if (_appsLoading.value) return@launch
        _appsLoading.value = true
        runCatching {
            _presets.value = sysfsRepo.getPresets()
            val profileMap = sysfsRepo.getAppProfiles()
            _appProfileMap.value = profileMap
            val installed = sysfsRepo.getInstalledApps()
            _apps.value = installed.map { ta ->
                profileMap[ta.pkg]?.copy(label = ta.label) ?: AppProfile(pkg = ta.pkg, label = ta.label)
            }
        }.onFailure { toast("App list error: ${it.message}") }
        _appsLoading.value = false
    }

    fun savePreset(preset: ProfilePreset) = viewModelScope.launch {
        val list = _presets.value.toMutableList()
        val idx = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        _presets.value = list
        runCatching { sysfsRepo.savePreset(preset) }
    }

    fun deletePreset(id: String) = viewModelScope.launch {
        _presets.value = _presets.value.filter { it.id != id }
        runCatching { sysfsRepo.deletePreset(id) }
    }

    fun saveAppProfile(pkg: String, profile: AppProfile?) = viewModelScope.launch {
        val map = _appProfileMap.value.toMutableMap()
        if (profile == null || !profile.enabled) map.remove(pkg) else map[pkg] = profile
        _appProfileMap.value = map
        _apps.value = _apps.value.map { a ->
            if (a.pkg == pkg) profile?.copy(label = a.label) ?: AppProfile(pkg = a.pkg, label = a.label) else a
        }
        runCatching { sysfsRepo.setAppProfile(pkg, profile) }
    }

    fun toast(msg: String) { _toast.value = msg }
    fun clearToast()       { _toast.value = null }
    override fun onCleared() { pollJob?.cancel(); super.onCleared() }

    private fun applyOptimistic(c: GarnetConfig, key: String, value: String) = when (key) {
        "night_mode"                   -> c.copy(nightMode            = value == "1")
        "thermal_control"              -> c.copy(thermalControl       = value == "1")
        "per_app_thermal"              -> c.copy(perAppThermal        = value == "1")
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
        "gpu_idle_timer"               -> c.copy(gpuIdleTimer          = value.toIntOrNull() ?: c.gpuIdleTimer)
        "thermal_boost"                -> c.copy(thermalBoost          = value.toIntOrNull() ?: c.thermalBoost)
        "apply_on_boot"               -> c.copy(applyOnBoot            = value == "1")
        "screen_off_little_cores_off"  -> c.copy(screenOffLittleCoresOff= value.toIntOrNull() ?: c.screenOffLittleCoresOff)
        "screen_off_big_cores_off"     -> c.copy(screenOffBigCoresOff   = value.toIntOrNull() ?: c.screenOffBigCoresOff)
        "screen_off_gpu_max_mhz"       -> c.copy(screenOffGpuMaxMhz     = value.toIntOrNull() ?: c.screenOffGpuMaxMhz)
        "screen_off_gov_little"        -> c.copy(screenOffGovLittle     = value)
        "screen_off_gov_big"           -> c.copy(screenOffGovBig        = value)
        "screen_off_time_enabled"      -> c.copy(screenOffTimeEnabled    = value == "1")
        "screen_off_time_start"        -> c.copy(screenOffTimeStart      = value.toIntOrNull() ?: c.screenOffTimeStart)
        "screen_off_time_end"          -> c.copy(screenOffTimeEnd        = value.toIntOrNull() ?: c.screenOffTimeEnd)
        else                           -> c
    }
}
