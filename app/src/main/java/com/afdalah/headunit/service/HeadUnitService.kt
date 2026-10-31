package com.afdalah.headunit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.afdalah.headunit.Log
import com.afdalah.headunit.MainActivity
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.mode.Mode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that runs only while a head-unit mode is active. It keeps GPS speed, status
 * monitors and the auto-mode automation alive while Maps/Spotify are in front, and keeps One UI's
 * battery management from killing the app mid-drive. In Standby GPS is paused to save battery.
 */
class HeadUnitService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        val app = app
        app.status.start()
        app.calls.start()
        app.media.start()
        app.autoMode.start(lifecycleScope)
        lifecycleScope.launch {
            app.modeManager.state.map { it.mode }.distinctUntilChanged().collect { mode ->
                when {
                    !mode.isHeadUnit -> stopSelf()
                    mode == Mode.STANDBY -> app.driving.stop()
                    else -> app.driving.start()
                }
            }
        }
        Log.i("HeadUnitService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Every startForegroundService() must be answered with startForeground(), even when already running.
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        val app = app
        app.autoMode.stop()
        app.driving.stop()
        app.status.stop()
        app.calls.stop()
        Log.i("HeadUnitService stopped")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Head unit active")
            .setContentText("Speed, media and mode automation are running")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL = "head_unit"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, HeadUnitService::class.java)) }
                .onFailure { Log.w("Could not start HeadUnitService (background start restriction?)", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeadUnitService::class.java))
        }
    }
}
