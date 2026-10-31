package com.afdalah.headunit.calls

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.afdalah.headunit.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CallState {
    data object Idle : CallState
    data class Ringing(val caller: String) : CallState
    data class Active(val caller: String, val sinceMillis: Long) : CallState
}

/**
 * Incoming-call state for the large call screen. The state comes from telephony
 * (READ_PHONE_STATE); the caller name comes from the dialer's call notification.
 * Accept/decline use TelecomManager (ANSWER_PHONE_CALLS), falling back to the
 * notification's own Answer/Decline actions.
 */
class CallMonitor(private val context: Context) {

    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private var callNotification: StatusBarNotification? = null
    private var callerName: String = "Unknown caller"
    private var registered = false

    /** TelephonyCallback on API 31+, PhoneStateListener on Android 11. */
    private var listener: Any? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CallCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = onTelephonyState(state)
    }

    private fun has(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (registered || !has(Manifest.permission.READ_PHONE_STATE)) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listener = CallCallback().also { telephony.registerTelephonyCallback(context.mainExecutor, it) }
            } else {
                @Suppress("DEPRECATION")
                listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) = onTelephonyState(state)
                }.also { telephony.listen(it, PhoneStateListener.LISTEN_CALL_STATE) }
            }
            registered = true
        }.onFailure { Log.w("Call state unavailable", it) }
    }

    fun stop() {
        if (registered) runCatching { unregister(telephony, listener) }
        listener = null
        registered = false
    }

    private fun onTelephonyState(state: Int) {
        _state.value = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.Ringing(callerName)
            TelephonyManager.CALL_STATE_OFFHOOK -> (_state.value as? CallState.Active)
                ?: CallState.Active(callerName, System.currentTimeMillis())
            else -> {
                callerName = "Unknown caller"
                CallState.Idle
            }
        }
    }

    fun onCallNotification(sbn: StatusBarNotification) {
        callNotification = sbn
        sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }?.let { name ->
            callerName = name
            _state.value = when (val s = _state.value) {
                is CallState.Ringing -> s.copy(caller = name)
                is CallState.Active -> s.copy(caller = name)
                CallState.Idle -> if (!registered && sbn.notification.fullScreenIntent != null) CallState.Ringing(name) else s
            }
        }
    }

    fun onCallNotificationRemoved(sbn: StatusBarNotification) {
        if (callNotification?.key == sbn.key) callNotification = null
        if (!registered) _state.value = CallState.Idle
    }

    @SuppressLint("MissingPermission")
    fun accept() {
        if (has(Manifest.permission.ANSWER_PHONE_CALLS) && runCatching { telecom.acceptRingingCall() }.isSuccess) return
        fireAction("answer", "accept")
    }

    @SuppressLint("MissingPermission")
    fun decline() {
        @Suppress("DEPRECATION")
        if (has(Manifest.permission.ANSWER_PHONE_CALLS) && runCatching { telecom.endCall() }.getOrDefault(false)) return
        fireAction("decline", "reject", "dismiss")
    }

    fun hangUp() = decline().also { if (!registered) _state.value = CallState.Idle }

    private fun fireAction(vararg keywords: String) {
        val actions = callNotification?.notification?.actions ?: return
        actions.firstOrNull { a -> keywords.any { a.title?.toString()?.contains(it, ignoreCase = true) == true } }
            ?.actionIntent?.let { runCatching { it.send() } }
    }
}

/** Unregisters either a TelephonyCallback (API 31+) or a legacy PhoneStateListener. */
internal fun unregister(telephony: TelephonyManager, listener: Any?) {
    when {
        listener == null -> Unit
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && listener is TelephonyCallback -> telephony.unregisterTelephonyCallback(listener)
        listener is PhoneStateListener -> @Suppress("DEPRECATION") telephony.listen(listener, PhoneStateListener.LISTEN_NONE)
    }
}
