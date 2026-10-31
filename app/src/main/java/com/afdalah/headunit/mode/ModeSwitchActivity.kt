package com.afdalah.headunit.mode

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.afdalah.headunit.Log
import com.afdalah.headunit.MainActivity
import com.afdalah.headunit.app

/**
 * Invisible trampoline (Theme.NoDisplay): applies a mode, then returns to the matching home.
 * Exported so Samsung Modes and Routines ("Open an app"), Tasker and ADB can start it.
 */
abstract class ModeSwitchActivity : Activity() {

    /** null = read the mode from the deep link (launcher://mode/<id>). */
    protected abstract val targetMode: Mode?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun handle(intent: Intent) {
        val mode = targetMode ?: Mode.fromId(intent.data?.lastPathSegment ?: intent.data?.host)
        if (mode == null) {
            Log.w("Unknown mode request: ${intent.data}")
            return
        }
        val source = resolveSource(intent)
        val result = app.modeManager.request(mode, source)
        Log.d("${javaClass.simpleName}: $mode from $source -> $result (referrer=$referrer)")
        when {
            result == ModeManager.Result.BLOCKED_DISABLED && source in setOf(ActivationSource.MANUAL, ActivationSource.QUICK_TILE) ->
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_SHOW_DISABLED_PROMPT, true))
            result == ModeManager.Result.BLOCKED_DISABLED -> Unit
            result == ModeManager.Result.DEFERRED -> Unit
            mode == Mode.TABLET -> HomeForwarder.toTabletHome(this)
            else -> HomeForwarder.toHeadUnit(this)
        }
    }

    private fun resolveSource(intent: Intent): ActivationSource {
        intent.getStringExtra(EXTRA_SOURCE)?.let { s ->
            ActivationSource.entries.firstOrNull { it.name.equals(s, true) }?.let { return it }
        }
        if (intent.data != null) return ActivationSource.DEEP_LINK
        if (intent.component?.className?.endsWith(".HeadUnitLauncher") == true) return ActivationSource.MANUAL
        // Tapped from One UI Home's app drawer -> manual; anything else (Routines, Tasker…) -> routine.
        val ref = referrer?.host
        return if (ref == HomeForwarder.ONE_UI_HOME || ref == packageName) ActivationSource.MANUAL else ActivationSource.ROUTINE
    }

    companion object {
        const val EXTRA_SOURCE = "source"
    }
}

class TabletModeActivity : ModeSwitchActivity() { override val targetMode = Mode.TABLET }
class DriveModeActivity : ModeSwitchActivity() { override val targetMode = Mode.DRIVE }
class NightModeActivity : ModeSwitchActivity() { override val targetMode = Mode.NIGHT }
class ParkedModeActivity : ModeSwitchActivity() { override val targetMode = Mode.PARKED }
class StandbyModeActivity : ModeSwitchActivity() { override val targetMode = Mode.STANDBY }

/** launcher://mode/{tablet|drive|night|parked|standby} */
class DeepLinkActivity : ModeSwitchActivity() { override val targetMode: Mode? = null }
