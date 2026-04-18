package dev.garnetforge.app.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ScriptManager {

    const val INSTALL_DIR    = "/data/data/dev.garnetforge.app/files/garnetforge"
    private val SCRIPTS      = listOf("apply.sh", "set_cfg.sh", "detect_nodes.sh", "detect_defaults.sh", "diagnostic.sh")
    private val VERSION_FILE = "$INSTALL_DIR/.app_version"
    private val DEFAULTS_FILE= "$INSTALL_DIR/defaults.prop"

    data class Progress(val step: Int, val total: Int, val message: String)

    suspend fun ensureInstalled(
        ctx: Context,
        appVersionCode: Int,
        onProgress: (Progress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val nodesExist = Shell.cmd("[ -s $INSTALL_DIR/nodes.prop ] && echo y")
                .exec().out.firstOrNull() == "y"
            val installedVersion = Shell.cmd("cat $VERSION_FILE 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            val needsUpdate = installedVersion < appVersionCode
            val isFirstLaunch = !nodesExist || needsUpdate

            val totalSteps = if (isFirstLaunch) 7 else 3

            onProgress(Progress(1, totalSteps, "Setting up directories…"))
            val bootstrapDest = "/data/adb/garnetforge_bootstrap.sh"
            copyAsset(ctx, "scripts/bootstrap.sh", bootstrapDest)
            Shell.cmd("chmod 755 $bootstrapDest; sh $bootstrapDest").exec()
            Shell.cmd("mkdir -p $INSTALL_DIR").exec()

            onProgress(Progress(2, totalSteps, "Installing scripts…"))
            val defaultsExist = Shell.cmd("[ -f $DEFAULTS_FILE ] && echo y")
                .exec().out.firstOrNull() == "y"
            SCRIPTS.forEach { script ->
                copyAsset(ctx, "scripts/$script", "$INSTALL_DIR/$script")
                Shell.cmd("chmod 755 $INSTALL_DIR/$script").exec()
            }

            onProgress(Progress(3, totalSteps, "Checking configuration…"))
            val configExists = Shell.cmd("[ -f $INSTALL_DIR/config.prop ] && echo y")
                .exec().out.firstOrNull() == "y"
            if (!configExists) copyAsset(ctx, "scripts/config.prop", "$INSTALL_DIR/config.prop")

            if (isFirstLaunch) {
                onProgress(Progress(4, totalSteps, "Detecting kernel nodes — first launch takes a moment…"))
                Shell.cmd("sh $INSTALL_DIR/detect_nodes.sh").exec()
                android.util.Log.i("GarnetForge", "Node detection complete")

                onProgress(Progress(5, totalSteps, "Reading default values…"))
                if (!defaultsExist || needsUpdate) {
                    Shell.cmd("sh $INSTALL_DIR/detect_defaults.sh").exec()
                }

                onProgress(Progress(6, totalSteps, "Finalizing setup…"))
                Shell.cmd("printf '%s' '$appVersionCode' > $VERSION_FILE").exec()

                onProgress(Progress(7, totalSteps, "Ready!"))
            } else {
                Shell.cmd("printf '%s' '$appVersionCode' > $VERSION_FILE").exec()
                onProgress(Progress(3, totalSteps, "Ready!"))
            }
            true
        }.getOrDefault(false)
    }

    private fun copyAsset(ctx: Context, assetPath: String, destPath: String) {
        ctx.assets.open(assetPath).use { input ->
            val bytes = input.readBytes()
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            Shell.cmd("printf '%s' '$b64' | base64 -d > $destPath 2>/dev/null").exec()
        }
    }

    fun isInstalled(): Boolean =
        Shell.cmd("[ -f $INSTALL_DIR/apply.sh ] && [ -f $INSTALL_DIR/config.prop ] && echo y")
            .exec().out.firstOrNull() == "y"
}
