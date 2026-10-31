package com.afdalah.headunit.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.afdalah.headunit.BuildConfig
import com.afdalah.headunit.Log
import com.afdalah.headunit.data.ConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DrivingState(
    /** km/h, null when there is no fresh GPS fix. */
    val speedKmh: Float? = null,
    /** Distraction rules apply while true. */
    val isMoving: Boolean = false,
    val simulated: Boolean = false,
)

/**
 * GPS speed via the platform LocationManager (no Play Services).
 * Moving = speed above the threshold; stopped only after the speed stays at or below it for
 * `stoppedDebounceSec`. With no fix (tunnel, garage) the last state is held for up to 2 minutes.
 */
class DrivingStateTracker(
    private val context: Context,
    private val config: ConfigRepository,
    private val scope: CoroutineScope,
) {
    private val lm = context.getSystemService(LocationManager::class.java)

    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state.asStateFlow()

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    /** Written only by the debug-build speed simulator; ignored in release (BuildConfig.DEBUG is const). */
    val simulatedSpeed = MutableStateFlow<Float?>(null)

    private var ticker: Job? = null
    private var rawSpeedKmh: Float? = null
    private var lastGpsFixAt = 0L
    private var atOrBelowSince: Long? = null

    private val listener = LocationListener { onLocation(it) }

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (ticker != null) return
        if (hasPermission()) {
            runCatching {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            }.onFailure { Log.w("GPS updates unavailable", it) }
            runCatching {
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 100f, listener, Looper.getMainLooper())
                }
            }
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null && _location.value == null) _location.value = last
        } else {
            Log.w("Location permission missing: speed unknown, distraction rules use last state")
        }
        ticker = scope.launch {
            while (isActive) {
                evaluate()
                delay(1_000)
            }
        }
        Log.d("DrivingStateTracker started")
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
        ticker?.cancel()
        ticker = null
        Log.d("DrivingStateTracker stopped")
    }

    private fun onLocation(loc: Location) {
        _location.value = loc
        if (loc.provider == LocationManager.GPS_PROVIDER) {
            rawSpeedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
            lastGpsFixAt = SystemClock.elapsedRealtime()
            evaluate()
        }
    }

    private fun evaluate() {
        val cfg = config.config.value.autoSwitch
        val now = SystemClock.elapsedRealtime()
        val sim = if (BuildConfig.DEBUG) simulatedSpeed.value else null
        val fixAge = now - lastGpsFixAt
        val speed = sim ?: rawSpeedKmh.takeIf { lastGpsFixAt > 0 && fixAge < 10_000 }
        val prev = _state.value

        val moving = when {
            speed == null -> prev.isMoving && fixAge < 120_000
            speed > cfg.movingSpeedKmh -> {
                atOrBelowSince = null
                true
            }
            prev.isMoving -> {
                val since = atOrBelowSince ?: now.also { atOrBelowSince = it }
                now - since < cfg.stoppedDebounceSec * 1_000L
            }
            else -> false
        }
        if (!moving) atOrBelowSince = null

        val next = DrivingState(speed, moving, sim != null)
        if (next != prev) {
            if (next.isMoving != prev.isMoving) Log.i("Vehicle ${if (moving) "moving" else "stopped"} (speed=$speed)")
            _state.value = next
        }
    }
}
