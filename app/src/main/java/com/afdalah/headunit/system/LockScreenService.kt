package com.afdalah.headunit.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.afdalah.headunit.Log
import kotlinx.coroutines.delay

/**
 * Accessibility service used for global actions: lock the screen like the power button does
 * (GLOBAL_ACTION_LOCK_SCREEN, so fingerprint/face unlock keep working), HOME and BACK, plus tapping
 * "Close all" in Recents when the head unit turns off. That button is the only screen content it looks for.
 */
class LockScreenService : AccessibilityService() {

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: LockScreenService? = null

        val isEnabled: Boolean get() = instance != null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /** System HOME: One UI minimizes pop-up windows (e.g. a video beside the sidebar) on HOME. */
        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        /**
         * Opens Recents and taps One UI's "Close all", which ends every app in the list (the only way left to a
         * normal app: ActivityManager refuses to end other apps' processes). With no recent apps it just goes HOME.
         */
        suspend fun closeAllRecents(): Boolean {
            val s = instance ?: return false
            if (!s.performGlobalAction(GLOBAL_ACTION_RECENTS)) return false
            repeat(15) {
                delay(200)
                val button = s.rootInActiveWindow?.let { findCloseAll(it) }
                if (button != null) {
                    val clicked = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i("Recents: Close all ${if (clicked) "tapped" else "not clickable"}")
                    if (clicked) return true
                }
            }
            Log.w("Recents: no Close all button (no recent apps?)")
            s.performGlobalAction(GLOBAL_ACTION_HOME)
            return false
        }

        private fun findCloseAll(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
            root.findAccessibilityNodeInfosByText("Close all")
                .firstNotNullOfOrNull { n -> generateSequence(n) { it.parent }.firstOrNull { it.isClickable } }

        /** BACK to the focused window (used to back out of Google Maps so its pop-up closes). */
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
    }
}
