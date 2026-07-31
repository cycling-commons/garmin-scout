package org.cyclingcommons.scout.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.cyclingcommons.scout.MainActivity
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.domain.TimerState

/**
 * Foreground service while a ride is open (RUNNING or PAUSED).
 * Type = location while GPS sampling is active (P2+).
 */
class RideForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val paused = intent?.getBooleanExtra(EXTRA_PAUSED, false) == true
                ensureChannel()
                val notification = buildNotification(paused)
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_ride),
            NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(paused: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (paused) {
            getString(R.string.ride_notification_paused)
        } else {
            getString(R.string.ride_notification_recording)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.ride_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scout_ride"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "org.cyclingcommons.scout.STOP_RIDE_SERVICE"
        const val EXTRA_PAUSED = "paused"

        fun sync(context: Context, timer: TimerState) {
            val app = context.applicationContext
            when (timer) {
                TimerState.IDLE -> {
                    val i = Intent(app, RideForegroundService::class.java).apply {
                        action = ACTION_STOP
                    }
                    app.startService(i)
                }
                TimerState.RUNNING, TimerState.PAUSED -> {
                    val i = Intent(app, RideForegroundService::class.java).apply {
                        putExtra(EXTRA_PAUSED, timer == TimerState.PAUSED)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(i)
                    } else {
                        app.startService(i)
                    }
                }
            }
        }
    }
}
