package com.afdalah.headunit.mode

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.afdalah.headunit.Log
import com.afdalah.headunit.MainActivity

/**
 * Routes HOME to the right place:
 *  - Tablet mode -> Samsung One UI Home, launched with no animation and kept out of Recents.
 *  - Head-unit modes -> our [MainActivity].
 */
object HomeForwarder {
    const val ONE_UI_HOME = "com.sec.android.app.launcher"

    private fun homeQuery() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun forwardFlags() = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_NO_ANIMATION or
        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

    /** Explicit intent for One UI Home's HOME activity, or null if it is not installed / disabled. */
    fun oneUiHomeIntent(context: Context): Intent? {
        val match = context.packageManager
            .queryIntentActivities(homeQuery(), PackageManager.MATCH_ALL)
            .firstOrNull { it.activityInfo.packageName == ONE_UI_HOME && it.activityInfo.enabled }
            ?: return null
        return homeQuery()
            .setComponent(ComponentName(match.activityInfo.packageName, match.activityInfo.name))
            .addFlags(forwardFlags())
    }

    /**
     * Tablet mode: hand over to One UI Home, or the system launcher chooser if it is missing.
     * [original] is the HOME intent we received: its extras are passed on, because One UI Home's
     * own settings (e.g. "Hide apps on Home screen") talk to the home screen through HOME + extras.
     */
    fun toTabletHome(activity: Activity, original: Intent? = null) {
        val intent = oneUiHomeIntent(activity)?.apply {
            original?.extras?.let { putExtras(it) }
            original?.sourceBounds?.let { sourceBounds = it }
        } ?: fallbackIntent(activity)
        runCatching { activity.startActivity(intent) }
            .onFailure {
                Log.w("Tablet home forward failed, opening Home settings", it)
                activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    private fun fallbackIntent(context: Context): Intent {
        val others = context.packageManager.queryIntentActivities(homeQuery(), 0)
            .filter { it.activityInfo.packageName != context.packageName }
        return when (others.size) {
            0 -> Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            1 -> homeQuery()
                .setClassName(others[0].activityInfo.packageName, others[0].activityInfo.name)
                .addFlags(forwardFlags())
            else -> Intent.createChooser(homeQuery(), "Choose tablet launcher")
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isDefaultHome(context: Context): Boolean =
        context.packageManager.resolveActivity(homeQuery(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName

    /**
     * Show the head-unit UI. When we are the default HOME app we go through the HOME intent so the
     * dashboard lives in the home task (one instance, never in Recents).
     */
    fun toHeadUnit(context: Context, extras: Intent.() -> Unit = {}) {
        val intent = if (isDefaultHome(context)) homeQuery().setPackage(context.packageName) else Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).extras()
        runCatching { context.startActivity(intent) }.onFailure { Log.w("Could not open head unit UI", it) }
    }
}
