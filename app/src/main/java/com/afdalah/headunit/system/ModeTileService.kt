package com.afdalah.headunit.system

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.afdalah.headunit.app
import com.afdalah.headunit.mode.ActivationSource
import com.afdalah.headunit.mode.DriveModeActivity
import com.afdalah.headunit.mode.ModeSwitchActivity
import com.afdalah.headunit.mode.TabletModeActivity

/**
 * Quick Settings toggle: on = head unit (Drive, Night Drive after sunset), off = Tablet.
 * Goes through the mode activities so it lands on the right home, like a routine would.
 */
class ModeTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val headUnit = app.modeManager.state.value.mode.isHeadUnit
        val target = if (headUnit) TabletModeActivity::class.java else DriveModeActivity::class.java
        @Suppress("DEPRECATION")
        startActivityAndCollapse(
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ModeSwitchActivity.EXTRA_SOURCE, ActivationSource.QUICK_TILE.name),
        )
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val mode = app.modeManager.state.value.mode
        tile.state = if (mode.isHeadUnit) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (mode.isHeadUnit) mode.label else "Tablet"
        tile.updateTile()
    }
}
