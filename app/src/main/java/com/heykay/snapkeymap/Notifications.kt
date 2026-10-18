package com.heykay.snapkeymap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.heykay.snapkeymap.ui.MainActivity

object Notifications {
    private const val CHANNEL = "status"
    private const val PAUSED_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Snap Key status", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when the Snap Key needs to be turned back on, for example after a restart."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun showPaused(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_snap)
            .setContentTitle("Snap Key is paused")
            .setContentText("Tap to turn it back on.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(PAUSED_ID, notification)
    }

    fun cancelPaused(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(PAUSED_ID)
    }
}
