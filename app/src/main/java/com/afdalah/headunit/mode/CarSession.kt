package com.afdalah.headunit.mode

import com.afdalah.headunit.HeadUnitApp
import com.afdalah.headunit.Log
import com.afdalah.headunit.system.LockScreenService
import com.afdalah.headunit.system.SystemActions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reacts to the car's Bluetooth directly (no routine needed, routines become a backup):
 *  - connected: Drive (Night after sunset), screen on, launcher in front with the welcome splash.
 *  - disconnected: end Google Maps navigation and close its window, goodbye splash with the Honda logo,
 *    lock the screen, then Tablet underneath, so the next unlock lands on a clean One UI Home.
 */
class CarSession(private val app: HeadUnitApp) {

    private var farewellJob: Job? = null

    fun onCarConnected() {
        val mm = app.modeManager
        farewellJob?.cancel()
        mm.setFarewell(false)
        if (mm.settings.value.headUnitDisabled) return
        if (!mm.state.value.mode.isHeadUnit) mm.request(Mode.DRIVE, ActivationSource.BLUETOOTH)
        Log.i("Car connected: opening the head unit")
        SystemActions.showDashboard(app)
    }

    /**
     * Google Maps' pop-up (in the map card) would otherwise float over One UI Home. Android lets no app close
     * another app's visible window, but BACK does: focus Maps, then back out of route preview and the app.
     * Runs under the goodbye screen (a non-focusable overlay), so none of this is visible.
     */
    private suspend fun closeMapsWindow() {
        if (!LockScreenService.isEnabled || !SystemActions.openMaps(app)) return
        delay(700)
        repeat(3) {
            LockScreenService.back()
            delay(450)
        }
    }

    fun onCarDisconnected() {
        val mm = app.modeManager
        if (!mm.state.value.mode.isHeadUnit || farewellJob?.isActive == true) return
        Log.i("Car disconnected: goodbye screen, then lock")
        farewellJob = app.scope.launch {
            mm.setFarewell(true)
            // End the trip in Google Maps too (its "Exit navigation" button), then close it once it's hidden.
            val started = System.currentTimeMillis()
            val wasNavigating = app.notificationHub.exitNavigation()
            if (wasNavigating) closeMapsWindow()
            SystemActions.showDashboard(app)
            val shown = app.configRepository.config.value.welcome.durationMs.coerceIn(1_500, 6_000).toLong() + 400
            delay((shown - (System.currentTimeMillis() - started)).coerceAtLeast(800))
            if (!LockScreenService.lock()) Log.w("Screen lock unavailable: enable Head Unit under Accessibility")
            // Let the screen go dark before the activity hands over to One UI Home.
            delay(800)
            mm.request(Mode.TABLET, ActivationSource.BLUETOOTH)
            mm.setFarewell(false)
            // Now in the background: let its process go too.
            if (wasNavigating) {
                delay(1_500)
                SystemActions.closeMaps(app)
            }
        }
    }
}
