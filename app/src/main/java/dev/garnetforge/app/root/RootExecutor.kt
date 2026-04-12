package dev.garnetforge.app.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootExecutor {

    /** Run command, return stdout lines joined. Empty string on failure. */
    suspend fun exec(cmd: String): String = withContext(Dispatchers.IO) {
        Shell.cmd(cmd).exec().out.joinToString("\n").trim()
    }

    /** Read a sysfs node. Returns null if unreadable. */
    suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        Shell.cmd("cat \"$path\" 2>/dev/null").exec()
            .out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Write value to sysfs node. Returns true on success. */
    suspend fun write(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("printf '%s' \"$value\" > \"$path\" 2>/dev/null").exec().isSuccess
    }

    /** Read config.prop key. */
    suspend fun getConfig(key: String): String? = withContext(Dispatchers.IO) {
        Shell.cmd("grep '^${key.replace(".", "\\.")}=' /data/data/dev.garnetforge.app/files/garnetforge/config.prop 2>/dev/null | head -1 | cut -d= -f2-")
            .exec().out.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Write config.prop key via set_cfg.sh. */
    suspend fun setConfig(key: String, value: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("sh /data/data/dev.garnetforge.app/files/garnetforge/set_cfg.sh \"$key\" \"$value\"").exec().isSuccess
    }

    /** Check if module is installed. */
    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("[ -f /data/data/dev.garnetforge.app/files/garnetforge/config.prop ] && echo yes")
            .exec().out.firstOrNull() == "yes"
    }

    /** Check root availability. */
    fun isRootAvailable(): Boolean = Shell.getShell().isRoot
}
