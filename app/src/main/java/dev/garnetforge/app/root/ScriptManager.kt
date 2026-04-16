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

    suspend fun ensureInstalled(ctx: Context, appVersionCode: Int): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                // Step 1: Bootstrap — ensure app data dir exists with correct permissions
                val bootstrapDest = "/data/adb/garnetforge_bootstrap.sh"
                copyAsset(ctx, "scripts/bootstrap.sh", bootstrapDest)
                Shell.cmd("chmod 755 $bootstrapDest; sh $bootstrapDest").exec()

                // Step 2: Create install dir (in case bootstrap didn't run as expected)
                Shell.cmd("mkdir -p $INSTALL_DIR").exec()

                val installedVersion = Shell.cmd("cat $VERSION_FILE 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                val needsUpdate = installedVersion < appVersionCode
                val defaultsExist = Shell.cmd("[ -f $DEFAULTS_FILE ] && echo y")
                    .exec().out.firstOrNull() == "y"

                // Copy all scripts
                SCRIPTS.forEach { script ->
                    copyAsset(ctx, "scripts/$script", "$INSTALL_DIR/$script")
                    Shell.cmd("chmod 755 $INSTALL_DIR/$script").exec()
                }

                // Copy default config only if missing
                val configExists = Shell.cmd("[ -f $INSTALL_DIR/config.prop ] && echo y")
                    .exec().out.firstOrNull() == "y"
                if (!configExists) {
                    copyAsset(ctx, "scripts/config.prop", "$INSTALL_DIR/config.prop")
                }

                // Run detect_nodes only when missing, version changed, or forced by reboot flag
                val nodesExist = Shell.cmd("[ -s $INSTALL_DIR/nodes.prop ] && echo y")
                    .exec().out.firstOrNull() == "y"
                if (!nodesExist || needsUpdate) {
                    Shell.cmd("sh $INSTALL_DIR/detect_nodes.sh").exec()
                    android.util.Log.i("GarnetForge", "Node detection complete")
                } else {
                    android.util.Log.i("GarnetForge", "Skipping node detection — nodes.prop exists")
                }

                // detect_defaults only on first install or if defaults.prop missing
                if (!defaultsExist || needsUpdate) {
                    Shell.cmd("sh $INSTALL_DIR/detect_defaults.sh").exec()
                }

                Shell.cmd("printf '%s' '$appVersionCode' > $VERSION_FILE").exec()
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
