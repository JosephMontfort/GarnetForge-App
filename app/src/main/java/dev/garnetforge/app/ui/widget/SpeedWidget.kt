package dev.garnetforge.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.garnetforge.app.MainActivity
import dev.garnetforge.app.R

class SpeedWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(ctx, mgr, id, null, null) }
    }
    companion object {
        const val ACTION_RUN_TEST = "dev.garnetforge.app.ACTION_SPEED_TEST"
        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int, dl: Float?, ul: Float?) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_speed)
            val pi = PendingIntent.getActivity(ctx, id,
                Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    action = ACTION_RUN_TEST
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.widget_updated, pi)
            rv.setOnClickPendingIntent(R.id.widget_dl, pi)
            if (dl != null && ul != null) {
                rv.setTextViewText(R.id.widget_dl, if (dl < 0) "↓ Error" else "↓ ${"%.1f".format(dl)} Mbps")
                rv.setTextViewText(R.id.widget_ul, if (ul < 0) "↑ Error" else "↑ ${"%.1f".format(ul)} Mbps")
                rv.setTextViewText(R.id.widget_updated,
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
            }
            mgr.updateAppWidget(id, rv)
        }
    }
}
