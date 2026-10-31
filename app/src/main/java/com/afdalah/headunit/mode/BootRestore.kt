package com.afdalah.headunit.mode

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.SystemClock
import com.afdalah.headunit.Log
import com.afdalah.headunit.app

/**
 * Boot rule: restore the last head-unit mode only if the tablet is plugged in AND connected to
 * the car's Bluetooth. Bluetooth usually connects some seconds after boot, so the decision stays
 * open for [WINDOW_MS] and is re-checked when an ACL connection arrives.
 */
object BootRestore {
    private const val WINDOW_MS = 5 * 60_000L

    fun tryRestore(context: Context, trigger: String, carConnected: Boolean? = null) {
        val app = context.app
        val mm = app.modeManager
        if (mm.pendingBootRestore != mm.currentBootCount) return
        if (SystemClock.elapsedRealtime() > WINDOW_MS) {
            Log.i("Boot restore window expired; staying in Tablet mode")
            mm.clearBootRestore()
            return
        }
        val carName = app.configRepository.config.value.carBluetoothName
        val plugged = isPluggedIn(context)
        val car = carConnected ?: isCarConnected(context, carName)
        Log.d("Boot restore check ($trigger): plugged=$plugged car=$car")
        if (!plugged || !car) return
        val target = mm.state.value.lastHeadUnitMode
        if (mm.request(target, ActivationSource.BOOT) == ModeManager.Result.APPLIED) {
            mm.clearBootRestore()
            HomeForwarder.toHeadUnit(context)
        }
    }

    /** "Plugged in", not "charging": Protect battery stops charging at 85% while still plugged. */
    fun isPluggedIn(context: Context): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    @SuppressLint("MissingPermission")
    fun isCarConnected(context: Context, carName: String): Boolean {
        if (!isConfigured(carName) || !hasBluetoothPermission(context)) return false
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        return adapter.bondedDevices.orEmpty().any { it.name.equals(carName, ignoreCase = true) && it.isConnectedCompat() }
    }

    fun isConfigured(carName: String) = carName.isNotBlank() && !carName.startsWith("[")

    fun hasBluetoothPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** BluetoothDevice.isConnected() is hidden but greylisted; fall back to "unknown = false". */
    private fun BluetoothDevice.isConnectedCompat(): Boolean =
        runCatching { javaClass.getMethod("isConnected").invoke(this) as Boolean }.getOrDefault(false)
}

/** BOOT_COMPLETED: Application.onCreate already reset to Tablet; just evaluate the restore rule. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BootRestore.tryRestore(context, "boot")
    }
}

/** Car Bluetooth connected -> head unit straight away; disconnected -> goodbye screen and lock ([CarSession]). */
class BluetoothAclReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val carName = context.app.configRepository.config.value.carBluetoothName
        if (!BootRestore.hasBluetoothPermission(context)) return
        if (!device?.name.equals(carName, ignoreCase = true)) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                BootRestore.tryRestore(context, "acl", carConnected = true)
                context.app.carSession.onCarConnected()
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> context.app.carSession.onCarDisconnected()
        }
    }
}
