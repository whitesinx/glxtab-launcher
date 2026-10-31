package com.afdalah.headunit.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.afdalah.headunit.Log
import com.afdalah.headunit.mode.ModeManager

/**
 * Optional: flips Settings.Global.STAY_ON_WHILE_PLUGGED_IN to 3 (AC + USB) while a head-unit
 * mode is active and restores the previous value in Tablet mode, so the setting never leaks
 * into normal tablet use. Requires `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`
 * (./gradlew grantSecureSettings). Without it the app falls back to FLAG_KEEP_SCREEN_ON.
 */
class StayOnController(private val context: Context, private val modeManager: ModeManager) {

    fun canWrite(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun apply(headUnitActive: Boolean) {
        if (!canWrite()) return
        val cr = context.contentResolver
        val current = Settings.Global.getInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0)
        val manage = modeManager.settings.value.manageStayOn
        runCatching {
            if (headUnitActive && manage) {
                if (current != STAY_ON_AC_USB) {
                    modeManager.rememberSavedStayOn(current)
                    Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, STAY_ON_AC_USB)
                    Log.i("stay_on_while_plugged_in $current -> $STAY_ON_AC_USB")
                }
            } else if (modeManager.savedStayOn >= 0) {
                Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, modeManager.savedStayOn)
                Log.i("stay_on_while_plugged_in restored to ${modeManager.savedStayOn}")
                modeManager.rememberSavedStayOn(-1)
            }
        }.onFailure { Log.w("Could not change stay_on_while_plugged_in", it) }
    }

    private companion object {
        const val STAY_ON_AC_USB = 3
    }
}
