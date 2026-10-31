package com.afdalah.headunit.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.afdalah.headunit.calls.CallMonitor
import com.afdalah.headunit.data.ConfigRepository
import com.afdalah.headunit.media.MediaRepository
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.mode.ModeManager
import com.afdalah.headunit.system.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A brief, large banner: icon, sender, short text. No inline reply. */
data class Banner(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val icon: ImageBitmap?,
    val sender: String,
    val text: String,
    val contentIntent: PendingIntent?,
)

/** Turn-by-turn state mirrored from Google Maps' ongoing navigation notification. */
data class NavGuidance(
    /** e.g. "300 m" or "Turn left". */
    val title: String,
    /** e.g. "toward Jl. Sudirman". */
    val text: String,
    /** e.g. "12 min · 5.2 km · 18:03 ETA". */
    val eta: String,
    val maneuver: ImageBitmap?,
    val contentIntent: PendingIntent?,
    /** Maps' own "Exit navigation" button. */
    val exitAction: PendingIntent? = null,
)

class NotificationHub(
    private val context: Context,
    private val config: ConfigRepository,
    private val modeManager: ModeManager,
    private val calls: CallMonitor,
    private val media: MediaRepository,
    private val scope: CoroutineScope,
) {
    private val _banner = MutableStateFlow<Banner?>(null)
    val banner: StateFlow<Banner?> = _banner.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    private val _guidance = MutableStateFlow<NavGuidance?>(null)
    val guidance: StateFlow<NavGuidance?> = _guidance.asStateFlow()
    private var guidanceKey: String? = null

    private var hideJob: Job? = null
    private val lastShown = HashMap<String, String>()

    fun setConnected(connected: Boolean) { _listenerConnected.value = connected }

    fun onPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            media.onMediaNotificationArt(sbn.packageName, runCatching {
                n.getLargeIcon()?.loadDrawable(context)?.toBitmap()
            }.getOrNull())
            return
        }
        if (n.category == Notification.CATEGORY_CALL) {
            calls.onCallNotification(sbn)
            return
        }
        if (isNavigation(sbn)) {
            onNavigation(sbn)
            return
        }
        val mode = modeManager.state.value.mode
        if (!mode.isHeadUnit || mode == Mode.STANDBY) return
        if (!shouldShow(sbn)) return

        val extras = n.extras
        val sender = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: return
        val raw = lastMessage(n) ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
        val max = config.config.value.notifications.maxTextChars
        val text = raw.toString().replace('\n', ' ').let { if (it.length > max) it.take(max - 1).trimEnd() + "…" else it }

        // Same text re-posted (progress, re-sorts): don't flash again.
        val fingerprint = "$sender|$text"
        if (lastShown[sbn.key] == fingerprint) return
        lastShown[sbn.key] = fingerprint

        _banner.value = Banner(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            icon = loadIcon(sbn),
            sender = sender.toString(),
            text = text,
            contentIntent = n.contentIntent,
        )
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(config.config.value.notifications.bannerSeconds * 1000L)
            _banner.value = null
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        lastShown.remove(sbn.key)
        if (sbn.notification.category == Notification.CATEGORY_CALL) calls.onCallNotificationRemoved(sbn)
        if (sbn.key == guidanceKey) { guidanceKey = null; _guidance.value = null }
        if (_banner.value?.key == sbn.key) dismiss()
    }

    fun dismiss() {
        hideJob?.cancel()
        _banner.value = null
    }

    fun isNavigation(sbn: StatusBarNotification) =
        sbn.packageName == SystemActions.GOOGLE_MAPS && sbn.isOngoing &&
            (sbn.notification.category == null || sbn.notification.category == Notification.CATEGORY_NAVIGATION ||
                sbn.notification.category == Notification.CATEGORY_SERVICE)

    private fun onNavigation(sbn: StatusBarNotification) {
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if (title.isBlank()) return
        guidanceKey = sbn.key
        _guidance.value = NavGuidance(
            title = title,
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            eta = e.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            maneuver = runCatching { sbn.notification.getLargeIcon()?.loadDrawable(context)?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull(),
            contentIntent = sbn.notification.contentIntent,
            exitAction = sbn.notification.actions.orEmpty().let { actions ->
                // The only action is "Exit navigation" (localized); prefer an English match when there are several.
                actions.firstOrNull { it.title?.contains("exit", ignoreCase = true) == true } ?: actions.singleOrNull()
            }?.actionIntent,
        )
    }

    /** Ends Google Maps navigation by pressing its notification's "Exit navigation" button. */
    fun exitNavigation(): Boolean {
        val action = _guidance.value?.exitAction ?: return false
        return runCatching { action.send() }.onFailure { com.afdalah.headunit.Log.w("Exit navigation failed", it) }.isSuccess
    }

    private fun shouldShow(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == context.packageName) return false
        if (sbn.isOngoing || !sbn.isClearable) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category in setOf(
                Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE,
                Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS,
            )) return false
        if (sbn.packageName in config.config.value.notifications.blockedPackages) return false
        return true
    }

    private fun lastMessage(n: Notification): CharSequence? {
        @Suppress("DEPRECATION")
        val messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = messages.lastOrNull() as? android.os.Bundle ?: return null
        return last.getCharSequence("text")
    }

    private fun loadIcon(sbn: StatusBarNotification): ImageBitmap? = runCatching {
        val d = sbn.notification.getLargeIcon()?.loadDrawable(context)
            ?: context.packageManager.getApplicationIcon(sbn.packageName)
        d.toBitmap(160, 160).asImageBitmap()
    }.getOrNull()

    private fun appLabel(pkg: String) = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
