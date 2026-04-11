package dev.garnetforge.app.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.garnetforge.app.R
import dev.garnetforge.app.data.model.ThermalProfile
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

class ThermalTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val SCONFIG = "/sys/class/thermal/thermal_message/sconfig"
    private val CYCLE = listOf(ThermalProfile.DEFAULT, ThermalProfile.GAMING, ThermalProfile.CAMERA, ThermalProfile.CHARGING)

    override fun onStartListening() {
        scope.launch { updateTile() }
    }

    override fun onClick() {
        scope.launch {
            val cur = Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
            val curIdx = CYCLE.indexOfFirst { it.sconfig == cur }.coerceAtLeast(0)
            val next = CYCLE[(curIdx + 1) % CYCLE.size]
            Shell.cmd("printf '%s' '${next.sconfig}' > $SCONFIG 2>/dev/null").exec()
            // Also update config.prop so it persists across module apply
            Shell.cmd("sh /data/adb/garnetforge/set_cfg.sh thermal_profile ${next.sconfig} 2>/dev/null").exec()
            updateTile()
        }
    }

    private suspend fun updateTile() {
        val cur = Shell.cmd("cat $SCONFIG 2>/dev/null").exec().out.firstOrNull()?.trim() ?: "0"
        val profile = ThermalProfile.fromSconfig(cur)
        qsTile?.apply {
            state    = Tile.STATE_ACTIVE
            label    = profile.label
            icon     = Icon.createWithResource(this@ThermalTileService, R.drawable.ic_tile)
            updateTile()
        }
    }

    override fun onStopListening() { /* no-op */ }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
