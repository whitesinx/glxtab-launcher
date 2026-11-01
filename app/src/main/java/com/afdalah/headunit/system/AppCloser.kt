package com.afdalah.headunit.system

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.afdalah.headunit.Log
import com.afdalah.headunit.notifications.HeadUnitNotificationListener
import kotlinx.coroutines.delay

/**
 * Leaving the head unit: stop what the drive left running so the tablet keeps its battery.
 *
 * One UI's ActivityManager refuses to end other apps' processes for a normal app (Android 14 rule, backported),
 * so this does what a person would: pause every media session (Spotify, YouTube, … then drop their
 * foreground service) and tap "Close all" in Recents via the accessibility service.
 * Alarms, push messages and scheduled jobs still work: the system restarts an app when it needs to.
 */
object AppCloser {

    suspend fun closeAll(context: Context) {
        pauseMedia(context)
        // Let One UI Home come forward first (Tablet mode forwards HOME to it).
        delay(1_500)
        if (!LockScreenService.closeAllRecents()) Log.w("Head unit off: apps not closed (accessibility service off?)")
    }

    private fun pauseMedia(context: Context) {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, HeadUnitNotificationListener::class.java)
        runCatching {
            msm.getActiveSessions(listener).forEach { c ->
                val s = c.playbackState?.state
                if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING) {
                    c.transportControls.pause()
                    Log.i("Head unit off: paused ${c.packageName}")
                }
            }
        }.onFailure { Log.w("Cannot pause media (notification access?)", it) }
    }
}
