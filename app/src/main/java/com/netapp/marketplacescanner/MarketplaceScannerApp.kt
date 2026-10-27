package com.netapp.marketplacescanner

import android.app.Application
import com.netapp.marketplacescanner.data.net.SessionStore
import com.netapp.marketplacescanner.notify.Notifications
import com.netapp.marketplacescanner.work.ScanScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MarketplaceScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        // Re-arm the periodic scan with the user's saved cadence on launch.
        val session = SessionStore(this)
        CoroutineScope(Dispatchers.Default).launch {
            val interval = session.intervalMinutes.first()
            val wifiOnly = session.wifiOnly.first()
            ScanScheduler.schedulePeriodic(this@MarketplaceScannerApp, interval, wifiOnly)
        }
    }
}
