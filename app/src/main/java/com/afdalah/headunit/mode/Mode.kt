package com.afdalah.headunit.mode

enum class Mode(val id: String, val label: String) {
    TABLET("tablet", "Tablet"),
    DRIVE("drive", "Drive"),
    NIGHT("night", "Night Drive"),
    PARKED("parked", "Parked"),
    STANDBY("standby", "Standby");

    val isHeadUnit: Boolean get() = this != TABLET
    val isDriving: Boolean get() = this == DRIVE || this == NIGHT

    companion object {
        fun fromId(id: String?): Mode? = entries.firstOrNull { it.id == id?.trim()?.lowercase() }
    }
}

/** Where the current mode came from — shown in the debug overlay. */
enum class ActivationSource { DEFAULT, ROUTINE, DEEP_LINK, AUTO, BOOT, MANUAL, BLUETOOTH, QUICK_TILE }

data class ModeState(
    val mode: Mode = Mode.TABLET,
    val source: ActivationSource = ActivationSource.DEFAULT,
    val changedAtMillis: Long = 0L,
    /** Last head-unit mode other than Standby; restored on boot / when power returns. */
    val lastHeadUnitMode: Mode = Mode.DRIVE,
)
