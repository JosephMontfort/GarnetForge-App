package dev.garnetforge.app.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log

/**
 * JobService that safely starts GarnetService after boot on Android 14+.
 * JobServices run in the foreground process window, so startForegroundService() is allowed.
 */
class GarnetBootJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        try {
            GarnetService.start(applicationContext)
        } catch (e: Exception) {
            Log.e("GarnetForge", "Boot job failed to start service: ${e.message}")
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
