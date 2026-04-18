package dev.garnetforge.app

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.*
import dev.garnetforge.app.DiagnosticState
import dev.garnetforge.app.SpeedTestState
import dev.garnetforge.app.service.GarnetService
import dev.garnetforge.app.ui.navigation.Screen
import dev.garnetforge.app.ui.screens.*
import dev.garnetforge.app.ui.theme.*
import dev.garnetforge.app.viewmodel.MainViewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }


    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { /* result ignored — service notif optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val accentTheme by vm.accentTheme.collectAsState()
            val blurEnabled by vm.blurEnabled.collectAsState()
            
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                1 -> true
                2 -> false
                else -> systemDark
            }
            
            GarnetForgeTheme(darkTheme = isDark, accentIndex = accentTheme) {
                val rootOk      by vm.rootOk.collectAsState()
                val checking    by vm.rootChecking.collectAsState()
                val config      by vm.config.collectAsState()
                val stats       by vm.stats.collectAsState()
                val sconfig     by vm.sconfig.collectAsState()
                val apps        by vm.apps.collectAsState()
                val presets     by vm.presets.collectAsState()
                val toast       by vm.toast.collectAsState()
                val appsLoading by vm.appsLoading.collectAsState()
                val deviceInfo  by vm.deviceInfo.collectAsState()
                val coreStates      by vm.coreStates.collectAsState()
                val dashboardReady  by vm.dashboardReady.collectAsState()
                val speedTestState  by vm.speedTestState.collectAsState()
                val diagnosticState by vm.diagnosticState.collectAsState()
                val entropyLevel    by vm.entropyLevel.collectAsState()
                val littleFreqLocked by vm.littleFreqLocked.collectAsState()
                val bigFreqLocked    by vm.bigFreqLocked.collectAsState()
                val gpuFreqLocked    by vm.gpuFreqLocked.collectAsState()
                val perCoreFreqMhz  by vm.perCoreFreqMhz.collectAsState()
                val availFreqsL     by vm.availFreqsL.collectAsState()
                val liveNodes       by vm.liveNodes.collectAsState()
                val nodeDefaults    by vm.nodeDefaults.collectAsState()
                val availFreqsB     by vm.availFreqsB.collectAsState()
                val availFreqsGpu   by vm.availFreqsGpu.collectAsState()

                when {
                    checking || (rootOk && !dashboardReady) -> AppIconSplash()
                    !rootOk  -> NoRootScreen(
                        onRetry = {
                            // Force kill and restart
                            val pm = packageManager
                            val intent = pm.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            finishAffinity()
                            startActivity(intent)
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    )
                    else -> {
                        LaunchedEffect(Unit) { GarnetService.start(this@MainActivity) }

                        var usagePrompt by remember { mutableStateOf(!hasUsageStats()) }
                        if (usagePrompt) {
                            AlertDialog(
                                onDismissRequest = { usagePrompt = false },
                                title = { Text("Usage Access Needed") },
                                text  = { Text("Required for Per-App Thermal Profiles.\nGo to: Settings → Special app access → Usage access → GarnetForge → Allow") },
                                confirmButton = { TextButton(onClick = {
                                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    usagePrompt = false
                                }) { Text("Open Settings") } },
                                dismissButton = { TextButton(onClick = { usagePrompt = false }) { Text("Later") } },
                            )
                        }

                        val navController = rememberNavController()
                        val current = navController.currentBackStackEntryAsState().value?.destination?.route

                        Scaffold(
                            bottomBar = {
                                NavigationBar {
                                    Screen.all.forEach { screen ->
                                        NavigationBarItem(
                                            selected = current == screen.route,
                                            onClick  = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                                    launchSingleTop = true; restoreState = true
                                                }
                                                vm.onTabSelected()
                                            },
                                            icon  = { Icon(screen.icon, screen.label) },
                                            label = { Text(screen.label) },
                                        )
                                    }
                                }
                            },
                            snackbarHost = {
                                if (toast != null) {
                                    LaunchedEffect(toast) { delay(2000); vm.clearToast() }
                                    Snackbar(Modifier.padding(12.dp)) { Text(toast!!) }
                                }
                            },
                        ) { padding ->
                            NavHost(
                                navController    = navController,
                                startDestination = Screen.Dashboard.route,
                                modifier         = Modifier.padding(padding),
                                enterTransition  = { slideInHorizontally(tween(250)) { it/4 } + fadeIn(tween(200)) },
                                exitTransition   = { slideOutHorizontally(tween(250)) { -it/4 } + fadeOut(tween(180)) },
                                popEnterTransition   = { slideInHorizontally(tween(250)) { -it/4 } + fadeIn(tween(200)) },
                                popExitTransition    = { slideOutHorizontally(tween(250)) { it/4 } + fadeOut(tween(180)) },
                            ) {
                                composable(Screen.Dashboard.route) {
                                    DashboardScreen(stats, config, sconfig, coreStates, onClearRam = { vm.clearRam() })
                                }
                                composable(Screen.Tuning.route) {
                                    TuningScreen(config, sconfig, coreStates, perCoreFreqMhz, availFreqsL, availFreqsB, availFreqsGpu, liveNodes, nodeDefaults,
                                        speedTestState = speedTestState, entropyLevel = entropyLevel,
                                        onRunSpeedTest = { vm.runSpeedTest() },
                                        littleFreqLocked = littleFreqLocked,
                                        bigFreqLocked    = bigFreqLocked,
                                        gpuFreqLocked    = gpuFreqLocked,
                                        onToggleLittleLock = { vm.toggleFreqLock(0) },
                                        onToggleBigLock    = { vm.toggleFreqLock(4) },
                                        onToggleGpuLock    = { vm.toggleGpuFreqLock() },
                                        blurEnabled = blurEnabled,
                                        onSet = { k, v -> vm.setConfig(k, v) },
                                        onProfileSelected = { vm.applyThermalProfile(it) },
                                        onToggleCore = { vm.toggleCore(it) })
                                }
                                composable(Screen.Intelligence.route) {
                                    IntelligenceScreen(
                                        config        = config,
                                        presets       = presets,
                                        onSavePreset  = { vm.savePreset(it) },
                                        onDeletePreset= { vm.deletePreset(it) },
                                        apps          = apps,
                                        appsLoading   = appsLoading,
                                        availFreqsL   = availFreqsL,
                                        availFreqsB   = availFreqsB,
                                        availFreqsGpu = availFreqsGpu,
                                        onSet         = { k, v -> vm.setConfig(k, v) },
                                        onLoadApps    = { vm.loadApps() },
                                        onSaveProfile = { pkg, prof -> vm.saveAppProfile(pkg, prof) },
                                        onBoostEntropy = { vm.boostEntropy() },
                                    )
                                }
                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        diagnosticState = diagnosticState,
                                        onRunDiagnostic = { vm.runDiagnostic() },
                                        config  = config,
                                        onSet   = { k, v -> vm.setConfig(k, v) },
                                        deviceInfo  = deviceInfo,
                                        themeMode   = themeMode,
                                        accentTheme = accentTheme,
                                        onAccent    = { vm.setAccentTheme(it) },
                                        blurEnabled = blurEnabled,
                                        onTheme     = { vm.setThemeMode(it) },
                                        onBlurToggle= { vm.setBlurEnabled(it) },
                                        onTelegram = {
                                            startActivity(Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://t.me/montfort_1607")))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check root on every resume (user might have granted in root manager)
        if (!vm.rootOk.value) vm.checkRoot()
    }

    private fun hasUsageStats(): Boolean {
        val usm = getSystemService(USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        return usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 60_000, now).isNotEmpty()
    }
}

@Composable
private fun AppIconSplash() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
               verticalArrangement = Arrangement.spacedBy(24.dp)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.mipmap.ic_launcher_raw),
                contentDescription = "GarnetForge",
                modifier = Modifier.size(96.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            )
            CircularProgressIndicator(
                color = GarnetLight,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    AppIconSplash()
}

@Composable
private fun NoRootScreen(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.padding(40.dp), horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text("🔒", fontSize=56.sp)
            Text("Root Access Required", style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.Bold)
            Text("Please grant root access to GarnetForge in your root manager, then tap Retry.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Button(onClick=onRetry, colors=ButtonDefaults.buttonColors(containerColor=GarnetRed)) {
                Text("Retry")
            }
        }
    }
}
