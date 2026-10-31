package com.afdalah.headunit.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import androidx.annotation.RequiresApi
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.afdalah.headunit.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BatteryStatus(val level: Int = 100, val tempC: Float = 25f, val plugged: Boolean = true, val charging: Boolean = false) {
    /** Above 45°C the sidebar shows a heat warning. */
    val overheating: Boolean get() = tempC >= HEAT_WARNING_C

    companion object { const val HEAT_WARNING_C = 45f }
}

enum class NetworkType { NONE, WIFI, CELLULAR }
data class NetworkStatus(val type: NetworkType = NetworkType.NONE, val wifiLevel: Int = 0, val cellLevel: Int = 0)
data class BluetoothStatus(val enabled: Boolean = false, val connected: Set<String> = emptySet())

data class StatusState(
    val battery: BatteryStatus = BatteryStatus(),
    val network: NetworkStatus = NetworkStatus(),
    val bluetooth: BluetoothStatus = BluetoothStatus(),
)

/** Battery, LTE/Wi-Fi signal and Bluetooth for the status sidebar. Active while a head-unit mode runs. */
class StatusMonitor(private val context: Context) {

    private val _state = MutableStateFlow(StatusState())
    val state: StateFlow<StatusState> = _state.asStateFlow()

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val btAdapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var started = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = onBattery(i)
    }

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshBluetooth()
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    @Suppress("DEPRECATION")
                    val device = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val name = if (hasBtPermission()) device?.name else null
                    val key = name ?: device?.address ?: return
                    _state.update { s ->
                        val set = if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED) s.bluetooth.connected + key else s.bluetooth.connected - key
                        s.copy(bluetooth = s.bluetooth.copy(enabled = btAdapter?.isEnabled == true, connected = set))
                    }
                }
            }
        }
    }

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                else -> NetworkType.NONE
            }
            val wifiLevel = if (type == NetworkType.WIFI) {
                val rssi = caps.signalStrength
                if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) 3
                else (wifi.calculateSignalLevel(rssi) * 4 / wifi.maxSignalLevel.coerceAtLeast(1)).coerceIn(0, 4)
            } else 0
            _state.update { it.copy(network = it.network.copy(type = type, wifiLevel = wifiLevel)) }
        }

        override fun onLost(network: Network) {
            _state.update { it.copy(network = it.network.copy(type = NetworkType.NONE)) }
        }
    }

    private fun onSignal(signalStrength: SignalStrength) {
        _state.update { it.copy(network = it.network.copy(cellLevel = signalStrength.level.coerceIn(0, 4))) }
    }

    /** TelephonyCallback on API 31+, PhoneStateListener on Android 11. */
    private var signalListener: Any? = null

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class SignalCallback : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignal(signalStrength)
    }

    fun start() {
        if (started) return
        started = true
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::onBattery)
        context.registerReceiver(btReceiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        runCatching { cm.registerDefaultNetworkCallback(netCallback) }
        runCatching {
            telephony.signalStrength?.let { s -> _state.update { it.copy(network = it.network.copy(cellLevel = s.level)) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                signalListener = SignalCallback().also { telephony.registerTelephonyCallback(context.mainExecutor, it) }
            } else {
                @Suppress("DEPRECATION")
                signalListener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignal(signalStrength)
                }.also { telephony.listen(it, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) }
            }
        }.onFailure { Log.w("No cellular signal updates", it) }
        refreshBluetooth()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { context.unregisterReceiver(btReceiver) }
        runCatching { cm.unregisterNetworkCallback(netCallback) }
        runCatching { com.afdalah.headunit.calls.unregister(telephony, signalListener) }
        signalListener = null
    }

    private fun onBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _state.update {
            it.copy(battery = BatteryStatus(
                level = if (level >= 0) level * 100 / scale.coerceAtLeast(1) else it.battery.level,
                tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 250) / 10f,
                plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            ))
        }
    }

    private fun hasBtPermission() =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun refreshBluetooth() {
        val adapter = btAdapter
        val enabled = adapter?.isEnabled == true
        val connected = if (enabled && hasBtPermission()) {
            adapter!!.bondedDevices.orEmpty().filter { d ->
                runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
            }.mapNotNull { it.name }.toSet()
        } else emptySet()
        _state.update { it.copy(bluetooth = BluetoothStatus(enabled, connected)) }
    }
}
