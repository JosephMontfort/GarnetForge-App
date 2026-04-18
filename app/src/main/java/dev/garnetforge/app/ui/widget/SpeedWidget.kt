package dev.garnetforge.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.garnetforge.app.R

class SpeedWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(ctx, mgr, id, null, null) }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_RUN_TEST) {
            val widgetId = intent.getIntExtra("widget_id", -1)
            // Start background speed test service
            val svc = Intent(ctx, SpeedTestWidgetService::class.java).apply {
                putExtra("widget_id", widgetId)
            }
            ctx.startService(svc)
        }
    }

    companion object {
        const val ACTION_RUN_TEST = "dev.garnetforge.app.ACTION_WIDGET_SPEED_TEST"

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int, dl: Float?, ul: Float?) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_speed)

            // Run test button — fires ACTION_RUN_TEST to this widget
            val testIntent = Intent(ctx, SpeedWidget::class.java).apply {
                action = ACTION_RUN_TEST
                putExtra("widget_id", id)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, id, testIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_run_btn, pi)
            rv.setOnClickPendingIntent(R.id.widget_dl, pi)

            if (dl != null && ul != null) {
                rv.setTextViewText(R.id.widget_dl, if (dl < 0) "--" else "%.1f".format(dl))
                rv.setTextViewText(R.id.widget_ul, if (ul < 0) "--" else "%.1f".format(ul))
                rv.setTextViewText(R.id.widget_updated,
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date()))
            }
            mgr.updateAppWidget(id, rv)
        }
    }
}
