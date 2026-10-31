package com.afdalah.headunit.mode

import com.afdalah.headunit.data.ConfigRepository
import com.afdalah.headunit.sensors.DrivingStateTracker
import com.afdalah.headunit.sensors.StatusMonitor
import com.afdalah.headunit.sensors.SunCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Automation that runs only while a head-unit mode is active (it lives in HeadUnitService):
 *  - GPS speed: Parked/Standby -> Drive when moving, Drive -> Parked after being stopped a while.
 *  - Sun: Drive <-> Night Drive on sunset/sunrise (edge-triggered, so a manual choice sticks
 *    until the next sunset/sunrise).
 *  - Power: Standby when external power is lost, back to the last mode when it returns.
 * Every request goes through [ModeManager.requestAuto], which refuses to touch Tablet mode.
 */
class AutoModeController(
    private val modeManager: ModeManager,
    private val driving: DrivingStateTracker,
    private val status: StatusMonitor,
    private val config: ConfigRepository,
) {
    private val _isNight = MutableStateFlow(false)
    /** Also used by the theme for "auto" light/dark. */
    val isNight: StateFlow<Boolean> = _isNight.asStateFlow()

    private var parkJob: Job? = null
    private val jobs = mutableListOf<Job>()

    fun start(scope: CoroutineScope) {
        if (jobs.isNotEmpty()) return
        _isNight.value = computeNight()

        jobs += scope.launch {
            while (isActive) {
                val night = computeNight()
                if (night != _isNight.value) {
                    _isNight.value = night
                    onSunEdge(night)
                }
                delay(60_000)
            }
        }

        jobs += scope.launch {
            driving.state.map { it.isMoving }.distinctUntilChanged().collect { moving ->
                parkJob?.cancel()
                val mode = modeManager.state.value.mode
                if (moving) {
                    if (mode == Mode.PARKED || mode == Mode.STANDBY) modeManager.requestAuto(drivingVariant())
                } else if (mode.isDriving) {
                    val after = config.config.value.autoSwitch.parkAfterStoppedSec
                    if (after > 0) parkJob = scope.launch {
                        delay(after * 1000L)
                        if (!driving.state.value.isMoving && modeManager.state.value.mode.isDriving) {
                            modeManager.requestAuto(Mode.PARKED)
                        }
                    }
                }
            }
        }

        jobs += scope.launch {
            // drop(1): react to power *changes* only, never to the state we started in.
            status.state.map { it.battery.plugged }.distinctUntilChanged().drop(1).collect { plugged ->
                if (!config.config.value.autoSwitch.standbyOnPowerLoss) return@collect
                val state = modeManager.state.value
                if (!plugged && state.mode.isHeadUnit && state.mode != Mode.STANDBY && !driving.state.value.isMoving) {
                    modeManager.requestAuto(Mode.STANDBY)
                } else if (plugged && state.mode == Mode.STANDBY) {
                    val last = state.lastHeadUnitMode
                    modeManager.requestAuto(if (last.isDriving) drivingVariant() else last)
                }
            }
        }

        // A routine/boot/Bluetooth/tile "Drive" after sunset becomes Night Drive. Manual and deep-link choices are kept.
        jobs += scope.launch {
            modeManager.state.collect { st ->
                if (st.mode == Mode.DRIVE && _isNight.value && config.config.value.autoSwitch.sunBasedNight &&
                    st.source in setOf(ActivationSource.ROUTINE, ActivationSource.BOOT, ActivationSource.BLUETOOTH, ActivationSource.QUICK_TILE)
                ) modeManager.requestAuto(Mode.NIGHT)
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        parkJob?.cancel()
    }

    private fun onSunEdge(night: Boolean) {
        if (!config.config.value.autoSwitch.sunBasedNight) return
        when (modeManager.state.value.mode) {
            Mode.DRIVE -> if (night) modeManager.requestAuto(Mode.NIGHT)
            Mode.NIGHT -> if (!night) modeManager.requestAuto(Mode.DRIVE)
            else -> Unit
        }
    }

    private fun drivingVariant(): Mode =
        if (config.config.value.autoSwitch.sunBasedNight && _isNight.value) Mode.NIGHT else Mode.DRIVE

    private fun computeNight(): Boolean {
        val now = System.currentTimeMillis()
        val loc = driving.location.value
        return if (loc != null) SunCalculator.isNight(now, loc.latitude, loc.longitude)
        else SunCalculator.isNightByClock(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }

    fun nextSunTransition(): Pair<Boolean, Long>? {
        val loc = driving.location.value ?: return null
        return SunCalculator.nextTransition(System.currentTimeMillis(), loc.latitude, loc.longitude)
    }
}
