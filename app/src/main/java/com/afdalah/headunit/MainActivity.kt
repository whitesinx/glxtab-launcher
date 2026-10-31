package com.afdalah.headunit

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.afdalah.headunit.data.ModeProfile
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.mode.HomeForwarder
import com.afdalah.headunit.service.HeadUnitService
import com.afdalah.headunit.ui.HeadUnitRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The HOME activity and the only UI activity.
 *
 * It starts translucent (see Theme.HeadUnit.Home). In Tablet mode it never draws: it forwards
 * straight to One UI Home with no animation and finishes, so there is no visible flash. In a
 * head-unit mode it turns opaque, locks to landscape and hosts the Compose dashboard.
 */
class MainActivity : ComponentActivity() {

    private val showDisabledPrompt = mutableStateOf(false)
    private val homeSignal = mutableIntStateOf(0)
    private var standbySleepJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDisabledPrompt.value = intent.getBooleanExtra(EXTRA_SHOW_DISABLED_PROMPT, false)
        if (shouldPassThrough()) {
            passThrough()
            return
        }
        showHeadUnit()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_DISABLED_PROMPT, false)) showDisabledPrompt.value = true
        if (shouldPassThrough()) passThrough() else homeSignal.intValue++
    }

    private fun shouldPassThrough() = app.modeManager.state.value.mode == Mode.TABLET && !showDisabledPrompt.value

    private fun passThrough() {
        HomeForwarder.toTabletHome(this, intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun showHeadUnit() {
        setTranslucent(false)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            HeadUnitRoot(
                showDisabledPrompt = showDisabledPrompt.value,
                homeSignal = homeSignal.intValue,
                onDismissDisabledPrompt = {
                    showDisabledPrompt.value = false
                    if (shouldPassThrough()) passThrough()
                },
            )
        }

        val app = app
        lifecycleScope.launch {
            combine(app.modeManager.state, app.configRepository.config) { state, _ -> state.mode }
                .collect { mode ->
                    if (mode == Mode.TABLET && !showDisabledPrompt.value) {
                        // Leaving the head unit: hand over to One UI Home if we're on screen, else just go away.
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) passThrough() else finish()
                        return@collect
                    }
                    applyWindow(mode, app.configRepository.profileFor(mode))
                }
        }
    }

    /** Per-mode window behaviour: orientation, brightness, keep-screen-on, immersive bars. */
    private fun applyWindow(mode: Mode, profile: ModeProfile) {
        // Foreground start is always allowed; covers a blocked background start (e.g. after process death).
        if (mode.isHeadUnit) HeadUnitService.start(this)
        // Car connected while the screen is off: wake it and show the head unit straight away.
        setTurnScreenOn(mode.isHeadUnit)
        setShowWhenLocked(mode.isHeadUnit)
        requestedOrientation = if (mode.isHeadUnit) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        window.attributes = window.attributes.apply {
            screenBrightness = profile.brightness?.coerceIn(0.01f, 1f)
                ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        if (mode.isHeadUnit && profile.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val insets = WindowInsetsControllerCompat(window, window.decorView)
        if (mode.isHeadUnit && profile.immersive) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }

        // Standby: dim clock, then release keep-screen-on so the normal screen timeout can sleep the device.
        standbySleepJob?.cancel()
        if (mode == Mode.STANDBY && profile.sleepAfterSec > 0) {
            standbySleepJob = lifecycleScope.launch {
                delay(profile.sleepAfterSec * 1000L)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Log.i("Standby: released keep-screen-on, device may sleep")
            }
        }
    }

    companion object {
        const val EXTRA_SHOW_DISABLED_PROMPT = "show_disabled_prompt"
    }
}
