package com.afdalah.headunit.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service used only for two global actions: lock the screen like the power button does
 * (GLOBAL_ACTION_LOCK_SCREEN, so fingerprint/face unlock keep working) and BACK. It reads no screen content.
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

        /** BACK to the focused window (used to back out of Google Maps so its pop-up closes). */
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
    }
}
