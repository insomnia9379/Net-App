package com.netapp.marketplacescanner.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

object ScanScheduler {

    /** (Re)schedules the periodic scan. WorkManager enforces a 15-minute floor. */
    fun schedulePeriodic(context: Context, intervalMinutes: Long, wifiOnly: Boolean) {
        val interval = intervalMinutes.coerceAtLeast(15)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ScanWorker>(Duration.ofMinutes(interval))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ScanWorker.UNIQUE_PERIODIC,
            // Keep the existing schedule but pick up new constraints/interval.
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Kicks off an immediate scan (e.g. pull-to-refresh / "Scan now"). */
    fun scanNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ScanWorker.ONE_OFF,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ScanWorker.UNIQUE_PERIODIC)
    }
}
