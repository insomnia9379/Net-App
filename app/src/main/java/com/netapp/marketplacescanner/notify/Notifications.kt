package com.netapp.marketplacescanner.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.netapp.marketplacescanner.R

/** Reason a listing produced an alert; affects the notification text. */
enum class AlertKind { NEW, PRICE_DROP, UNDER_PRICE }

data class Alert(
    val title: String,
    val priceText: String,
    val location: String,
    val url: String,
    val kind: AlertKind,
)

object Notifications {
    const val CHANNEL_ALERTS = "listing_alerts"
    const val CHANNEL_STATUS = "scan_status"
    private const val SUMMARY_ID = 1
    private const val GROUP = "marketplace_alerts"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.scan_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.scan_channel_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.status_channel_desc) }
        )
    }

    fun notifyAlerts(context: Context, searchLabel: String, alerts: List<Alert>) {
        if (alerts.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val nm = NotificationManagerCompat.from(context)
        alerts.forEach { alert ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                alert.url.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val prefix = when (alert.kind) {
                AlertKind.PRICE_DROP -> "Price drop"
                AlertKind.UNDER_PRICE -> "Under your price"
                AlertKind.NEW -> "New listing"
            }
            val line = listOf(alert.priceText, alert.location)
                .filter { it.isNotBlank() }.joinToString(" • ")

            val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("$prefix: ${alert.title}")
                .setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$searchLabel\n$line"))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .build()
            nm.notify(alert.url.hashCode(), notif)
        }

        val summary = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Marketplace Scanner")
            .setContentText("${alerts.size} new match(es) for \"$searchLabel\"")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify(SUMMARY_ID, summary)
    }
}
