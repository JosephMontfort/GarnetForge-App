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
        val dlScript = """
S=$(date +%s%3N)
curl -s -o /dev/null -w '%{size_download}|%{time_total}\n' --max-time 12 --connect-timeout 5 \
  'https://speed.cloudflare.com/__down?bytes=10000000' 2>/dev/null
printf 'T:%d' "$(($(date +%s%3N) - S))"
        """.trimIndent()
        val dlOut = Shell.cmd(dlScript).exec().out
        val dlMs  = dlOut.lastOrNull { it.startsWith("T:") }?.removePrefix("T:")?.toLongOrNull() ?: -1L
        val dlByt = dlOut.firstOrNull { it.contains("|") }?.substringBefore("|")?.trim()?.toLongOrNull() ?: 10_000_000L
        val dlMbps = if (dlMs > 500) (dlByt * 8f / 1_000_000f) / (dlMs / 1000f) else -1f

        val ulScript = """
S=$(date +%s%3N)
dd if=/dev/urandom bs=1024 count=2048 2>/dev/null | \
curl -s -o /dev/null -w '%{size_upload}|%{time_total}\n' --max-time 10 --connect-timeout 5 \
  -X POST -H 'Content-Type: application/octet-stream' --data-binary @- \
  'https://speed.cloudflare.com/__up' 2>/dev/null
printf 'T:%d' "$(($(date +%s%3N) - S))"
        """.trimIndent()
        val ulOut  = Shell.cmd(ulScript).exec().out
        val ulMs   = ulOut.lastOrNull { it.startsWith("T:") }?.removePrefix("T:")?.toLongOrNull() ?: -1L
        val ulByt  = ulOut.firstOrNull { it.contains("|") }?.substringBefore("|")?.trim()?.toLongOrNull() ?: 2_000_000L
        val ulMbps = if (ulMs > 500) (ulByt * 8f / 1_000_000f) / (ulMs / 1000f) else -1f

        return Pair(dlMbps, ulMbps)
    }
}
