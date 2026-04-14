package dev.garnetforge.app.service

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        // Only handle fully-booted case; LOCKED_BOOT_COMPLETED fires too early on some devices
        if (intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED") {
            scheduleBootJob(ctx)
            return
        }

        // Android 14+ restricts startForegroundService from background/boot receivers.
        // Wrap in try/catch and fall back to scheduling a job.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            scheduleBootJob(ctx)
        } else {
            try {
                GarnetService.start(ctx)
            } catch (e: Exception) {
                Log.w("GarnetForge", "Boot FGS start failed, scheduling job: ${e.message}")
                scheduleBootJob(ctx)
            }
        }
    }

    private fun scheduleBootJob(ctx: Context) {
        try {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val job = JobInfo.Builder(JOB_ID,
                ComponentName(ctx, GarnetBootJobService::class.java))
                .setMinimumLatency(5_000)     // 5s after boot
                .setOverrideDeadline(30_000)  // must run within 30s
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .build()
            js.schedule(job)
        } catch (e: Exception) {
            Log.e("GarnetForge", "Boot job scheduling failed: ${e.message}")
        }
    }

    companion object {
        const val JOB_ID = 1001
    }
}
