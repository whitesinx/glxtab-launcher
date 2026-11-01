package com.afdalah.headunit.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User-editable configuration (config.json). Every field has a default so a partial
 * or older file still loads; unknown keys are ignored.
 */
@Serializable
data class HeadUnitConfig(
    val version: Int = 1,
    /** Exact Bluetooth name of the car. Used for the boot-restore rule. */
    val carBluetoothName: String = "[CAR_BLUETOOTH_NAME]",
    /** Global accent; a profile's accentColor overrides it. */
    val accentColor: String = "#0A84FF",
    val navigation: NavigationConfig = NavigationConfig(),
    val dock: List<DockApp> = DEFAULT_DOCK,
    val autoSwitch: AutoSwitchConfig = AutoSwitchConfig(),
    val notifications: NotificationConfig = NotificationConfig(),
    /** Apps treated as video: blocked while moving. */
    val videoPackages: List<String> = listOf(
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient",
        "tv.twitch.android.app",
    ),
    val profiles: Profiles = Profiles(),
    val welcome: WelcomeConfig = WelcomeConfig(),
    val obd: ObdConfig = ObdConfig(),
)

@Serializable
data class NavigationConfig(
    /** "google_maps" or "waze" — used for the Home/Work quick destinations. */
    val preferredApp: String = "google_maps",
    val home: Destination = Destination("Home", "[HOME_ADDRESS]"),
    val work: Destination = Destination("Work", "[WORK_ADDRESS]"),
    /** While Google Maps navigates, show it as a window over the dashboard's map card. */
    val mapsInCard: Boolean = true,
)

@Serializable
data class Destination(val label: String, val address: String) {
    val isConfigured: Boolean get() = address.isNotBlank() && !address.startsWith("[")
}

@Serializable
data class DockApp(val label: String, @SerialName("package") val packageName: String)

@Serializable
data class AutoSwitchConfig(
    /** Above this GPS speed the vehicle is "moving" (distraction rules on). */
    val movingSpeedKmh: Float = 8f,
    /** Moving -> stopped only after the speed stays below the threshold this long. */
    val stoppedDebounceSec: Int = 5,
    /** Drive -> Parked after being stopped this long. 0 disables the auto switch. */
    val parkAfterStoppedSec: Int = 120,
    /** Switch Drive <-> Night Drive at sunset/sunrise. */
    val sunBasedNight: Boolean = true,
    /** Head-unit modes only: enter Standby when external power is lost. */
    val standbyOnPowerLoss: Boolean = true,
)

@Serializable
data class NotificationConfig(
    val bannerSeconds: Int = 6,
    val maxTextChars: Int = 80,
    val blockedPackages: List<String> = emptyList(),
)

@Serializable
data class Profiles(
    val tablet: ModeProfile = ModeProfile(),
    val drive: ModeProfile = ModeProfile(theme = "auto", keepScreenOn = true, immersive = true),
    val night: ModeProfile = ModeProfile(
        theme = "dark", palette = "night", accentColor = "#FF9F0A",
        brightness = 0.35f, keepScreenOn = true, immersive = true,
    ),
    val parked: ModeProfile = ModeProfile(theme = "auto", keepScreenOn = true, immersive = true),
    val standby: ModeProfile = ModeProfile(
        theme = "dark", palette = "night", brightness = 0.02f,
        keepScreenOn = true, immersive = true, sleepAfterSec = 120,
    ),
)

@Serializable
data class ModeProfile(
    /** "auto" (follows the sidebar dark/light toggle, dark by default), "dark" or "light". */
    val theme: String = "auto",
    /** "standard" or "night" (dimmer, warmer). */
    val palette: String = "standard",
    val accentColor: String? = null,
    /** Window brightness 0..1, or null to follow the system. */
    val brightness: Float? = null,
    val keepScreenOn: Boolean = false,
    val immersive: Boolean = false,
    /** Standby only: release keep-screen-on after this many seconds so the device can sleep. */
    val sleepAfterSec: Int = 0,
)

@Serializable
data class WelcomeConfig(
    /** Splash shown when the head unit starts from Tablet (car connected, routine, deep link), and on disconnect. */
    val enabled: Boolean = true,
    /** "honda" (official H mark + wordmark) or "none". */
    val logo: String = "honda",
    val title: String = "Welcome, Habibi",
    /** Shown when the car disconnects, before the screen locks. */
    val farewellTitle: String = "Goodbye, Habibi",
    val durationMs: Int = 2_600,
)

@Serializable
data class ObdConfig(
    /** Phase 2: ELM327 over Bluetooth. The dashboard card slot exists already. */
    val enabled: Boolean = false,
    val adapterName: String = "OBDII",
)

/** Sidebar shortcuts until the user picks their own (long-press a sidebar icon while parked). */
val DEFAULT_SIDEBAR_APPS = listOf(
    "com.netflix.mediaclient",
    "com.google.android.youtube",
    "com.spotify.music",
)

val DEFAULT_DOCK = listOf(
    DockApp("Maps", "com.google.android.apps.maps"),
    DockApp("Spotify", "com.spotify.music"),
    DockApp("Phone", "com.samsung.android.dialer"),
    DockApp("Messages", "com.samsung.android.messaging"),
    DockApp("Podcasts", "au.com.shiftyjelly.pocketcasts"),
    DockApp("Camera", "com.sec.android.app.camera"),
)
