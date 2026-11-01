package com.afdalah.headunit.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.afdalah.headunit.Log
import com.afdalah.headunit.data.Destination

/** Intents to other apps and system screens. */
object SystemActions {

    const val GOOGLE_MAPS = "com.google.android.apps.maps"
    const val WAZE = "com.waze"

    private fun Context.tryStart(intent: Intent): Boolean =
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w("Cannot start ${intent.action} ${intent.data}") }
            .isSuccess

    const val GEMINI = "com.google.android.apps.bard"

    /**
     * Gemini app (mic and Live buttons; it can change a Google Maps route), then Google voice search.
     * Not VOICE_COMMAND: on One UI that opens Bixby. Gemini has no public intent to start listening.
     */
    fun voiceAssistant(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GEMINI)?.let { context.tryStart(it) } == true ||
            context.tryStart(Intent("android.intent.action.VOICE_ASSIST")) ||
            context.tryStart(Intent(Intent.ACTION_WEB_SEARCH))

    /**
     * Opens Google Maps as a window inside [bounds] (screen px), so it sits exactly over the map card.
     * Launch bounds put the task in freeform mode on devices with freeform window management (One UI tablets).
     */
    fun openMapsInBounds(context: Context, bounds: Rect): Boolean = openInBounds(context, GOOGLE_MAPS, bounds)

    /**
     * Opens [pkg] as a pop-up window inside [bounds] (screen px). Used for Maps in the map card and for
     * video apps, which fill the area beside the sidebar the way karaoke does.
     */
    fun openInBounds(context: Context, pkg: String, bounds: Rect): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        // One UI pushes a pop-up window down by its drag-handle strip; ask for it that much higher so the window
        // lands exactly on the card (measured on the Tab S7: requested top 38 px -> placed at 65 px).
        val target = Rect(bounds).apply { offset(0, -POPUP_HANDLE_PX) }
        val options = ActivityOptions.makeBasic().setLaunchBounds(target)
        // Hidden on API 33; without it the platform still derives freeform from the launch bounds.
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(options, WINDOWING_MODE_FREEFORM)
        }
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
        }.onFailure { Log.w("Cannot open $pkg in bounds $bounds", it) }.isSuccess
    }

    private const val WINDOWING_MODE_FREEFORM = 5
    private const val POPUP_HANDLE_PX = 27

    /** Brings the head-unit dashboard to the front (as a HOME intent, so it resets to the dashboard). */
    fun showDashboard(context: Context): Boolean = context.tryStart(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setClassName(context, "com.afdalah.headunit.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
    )

    /** Lets Google Maps' process go once it is in the background; a visible app can't be closed this way. */
    fun closeMaps(context: Context) {
        context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(GOOGLE_MAPS)
        Log.i("Closed Google Maps")
    }

    /** Opens Google Maps (search screen when not navigating). */
    fun openMaps(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GOOGLE_MAPS)?.let { context.tryStart(it) } == true

    fun navigateTo(context: Context, dest: Destination, preferredApp: String): Boolean {
        val q = Uri.encode(dest.address)
        val waze = Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage(WAZE)
        val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(GOOGLE_MAPS)
        val ordered = if (preferredApp == "waze") listOf(waze, maps) else listOf(maps, waze)
        return ordered.any { context.tryStart(it) } ||
            context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
    }

    fun isNotificationAccessGranted(context: Context) =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun openNotificationAccess(context: Context) =
        context.tryStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun isIgnoringBatteryOptimizations(context: Context) =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) =
        context.tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))

    fun openAppSettings(context: Context) =
        context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

    fun openHomeSettings(context: Context) = context.tryStart(Intent(Settings.ACTION_HOME_SETTINGS))
}
