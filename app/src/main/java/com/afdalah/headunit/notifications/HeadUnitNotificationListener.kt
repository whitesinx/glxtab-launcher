package com.afdalah.headunit.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.afdalah.headunit.Log
import com.afdalah.headunit.app

/**
 * Gives the app access to media sessions (Now Playing), notification banners and the caller name
 * of incoming calls. Enable it under Settings > Notifications > Device & app notifications.
 */
class HeadUnitNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i("Notification listener connected")
        app.notificationHub.setConnected(true)
        app.media.start()
        runCatching { activeNotifications?.filter {
            it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) || app.notificationHub.isNavigation(it)
        }
            ?.forEach { app.notificationHub.onPosted(it) } }
    }

    override fun onListenerDisconnected() {
        app.notificationHub.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        app.notificationHub.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        app.notificationHub.onRemoved(sbn)
    }
}
