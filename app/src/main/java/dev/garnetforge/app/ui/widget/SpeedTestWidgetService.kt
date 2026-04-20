package dev.garnetforge.app.ui.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.Shell
import dev.garnetforge.app.R
import kotlinx.coroutines.*
import android.widget.RemoteViews

class SpeedTestWidgetService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val widgetId = intent?.getIntExtra("widget_id", -1) ?: -1
        scope.launch {
            val mgr = AppWidgetManager.getInstance(applicationContext)
            val ids = if (widgetId >= 0) intArrayOf(widgetId)
                      else mgr.getAppWidgetIds(ComponentName(applicationContext, SpeedWidget::class.java))

            // Show "Testing…" state
            ids.forEach { id ->
                val rv = RemoteViews(packageName, R.layout.widget_speed)
                rv.setTextViewText(R.id.widget_dl, "…")
                rv.setTextViewText(R.id.widget_ul, "…")
                rv.setTextViewText(R.id.widget_updated, "Testing…")
                mgr.updateAppWidget(id, rv)
            }

            // Run speed test
            val (dl, ul) = runSpeedTestShell()
            ids.forEach { id -> SpeedWidget.updateWidget(applicationContext, mgr, id, dl, ul) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun runSpeedTestShell(): Pair<Float, Float> {
        val tmp = "/data/data/dev.garnetforge.app/files/garnetforge/widget_st_tmp"
        Shell.cmd("rm -rf $tmp && mkdir -p $tmp").exec()

        fun mbps(bytes: Long, ms: Long) =
            if (ms > 300L && bytes > 0L) (bytes * 8f / 1_000_000f) / (ms / 1000f) else -1f

        return try {
            // ── Download (2 × 10 MB, max 12 s) ─────────────────────────
            val dlB64 = android.util.Base64.encodeToString("""
#!/system/bin/sh
curl -s -o $tmp/d1.tmp --max-time 12 --connect-timeout 5 'https://speed.cloudflare.com/__down?bytes=10000000' 2>/dev/null &
curl -s -o $tmp/d2.tmp --max-time 12 --connect-timeout 5 'https://speed.cloudflare.com/__down?bytes=10000000' 2>/dev/null &
wait
printf done > $tmp/dl_done
""".trimIndent().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.cmd(
                "printf '%s' '$dlB64' | base64 -d > $tmp/dl.sh",
                "chmod 755 $tmp/dl.sh",
                "sh $tmp/dl.sh > $tmp/dl.log 2>&1 &"
            ).exec()
            val dlStart = System.currentTimeMillis()
            var waited = 0
            while (waited < 15_000) {
                Thread.sleep(500)
                waited += 500
                if (Shell.cmd("[ -f $tmp/dl_done ] && echo y || echo n").exec().out.firstOrNull() == "y") break
            }
            val dlMs    = System.currentTimeMillis() - dlStart
            val dlBytes = Shell.cmd(
                "du -sk $tmp/d1.tmp $tmp/d2.tmp 2>/dev/null | awk '{sum+=\$1} END{print sum*1024}'"
            ).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            val dlMbps  = mbps(dlBytes, dlMs)

            // ── Upload (1 × 3 MB, max 10 s) ─────────────────────────────
            val ulB64 = android.util.Base64.encodeToString("""
#!/system/bin/sh
dd if=/dev/urandom bs=1048576 count=3 of=$tmp/ul_data 2>/dev/null
curl -s -X POST -H 'Content-Type: application/octet-stream' --data-binary @$tmp/ul_data \
  -o /dev/null -w '%{size_upload}' --max-time 10 --connect-timeout 5 \
  'https://speed.cloudflare.com/__up' 2>/dev/null > $tmp/ul.out
printf done > $tmp/ul_done
""".trimIndent().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
            Shell.cmd(
                "printf '%s' '$ulB64' | base64 -d > $tmp/ul.sh",
                "chmod 755 $tmp/ul.sh",
                "sh $tmp/ul.sh > $tmp/ul.log 2>&1 &"
            ).exec()
            val ulStart = System.currentTimeMillis()
            waited = 0
            while (waited < 14_000) {
                Thread.sleep(500)
                waited += 500
                if (Shell.cmd("[ -f $tmp/ul_done ] && echo y || echo n").exec().out.firstOrNull() == "y") break
            }
            val ulMs    = System.currentTimeMillis() - ulStart
            val ulBytes = Shell.cmd("cat $tmp/ul.out 2>/dev/null").exec().out
                .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            val ulMbps  = mbps(ulBytes, ulMs)

            android.util.Log.i("GarnetForge", "Widget speed: dl=${dlMbps}Mbps ul=${ulMbps}Mbps")
            Pair(dlMbps, ulMbps)
        } catch (e: Exception) {
            android.util.Log.e("GarnetForge", "Widget speed test failed: ${e.message}")
            Pair(-1f, -1f)
        } finally {
            Shell.cmd("rm -rf $tmp").exec()
        }
    }
}
