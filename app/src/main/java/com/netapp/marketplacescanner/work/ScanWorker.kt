package com.netapp.marketplacescanner.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.netapp.marketplacescanner.data.repo.ScanOutcome
import com.netapp.marketplacescanner.data.repo.ScannerRepository
import com.netapp.marketplacescanner.notify.Notifications

/**
 * Periodic background scan of every enabled search. Runs under WorkManager so
 * it survives process death and reboots, respecting the user's interval and
 * network constraints.
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ScannerRepository.get(applicationContext)
        val searches = repo.enabledSearches()
        if (searches.isEmpty()) return Result.success()

        for (search in searches) {
            when (val outcome = repo.scan(search)) {
                is ScanOutcome.Completed -> {
                    val label = search.query.ifBlank { search.category.ifBlank { "Marketplace" } }
                    Notifications.notifyAlerts(applicationContext, label, outcome.alerts)
                }
                // Auth expiry can't be fixed by a retry (the user must re-login),
                // and transient failures are picked up on the next periodic cycle —
                // so we don't fail the whole job for one bad search.
                is ScanOutcome.AuthRequired -> Unit
                is ScanOutcome.Failed -> Unit
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC = "marketplace_periodic_scan"
        const val ONE_OFF = "marketplace_one_off_scan"
    }
}
