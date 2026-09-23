package com.example.domain.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {

    const val CHANNEL_ID_SUPPLIES = "family_supply_restock"
    const val CHANNEL_ID_EXPIRY = "family_supply_expiry"
    private const val NOTIF_ID_RESTOCK = 1001
    private const val NOTIF_ID_EXPIRY = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val supplyChannel = NotificationChannel(
                CHANNEL_ID_SUPPLIES,
                "该买了提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "家里的东西快用完时提醒你"
                enableVibration(true)
            }

            val expiryChannel = NotificationChannel(
                CHANNEL_ID_EXPIRY,
                "快过期提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "食物快到保质期时提醒你"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(supplyChannel)
            notificationManager.createNotificationChannel(expiryChannel)
        }
    }

    fun showRestockAlert(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SUPPLIES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIF_ID_RESTOCK, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }

    fun showExpiryAlert(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EXPIRY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIF_ID_EXPIRY, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
