package com.afdalah.headunit.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.afdalah.headunit.mode.ActivationSource
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.mode.ModeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "headunit")

/** Settings toggled from the in-app (Parked) settings screen. */
data class UserSettings(
    val headUnitDisabled: Boolean = false,
    val highContrast: Boolean = false,
    /** Toggle Settings.Global.STAY_ON_WHILE_PLUGGED_IN only while a head-unit mode is active. */
    val manageStayOn: Boolean = false,
    val accentOverride: String? = null,
    /** Sidebar sun/moon toggle. Dark by default; Night Drive and Standby are always dark. */
    val darkTheme: Boolean = true,
    /** Overall look: "carplay", "glass" or "ps5" (ui.theme.ThemeStyle ids). */
    val themeStyle: String = "carplay",
    val recentPackages: List<String> = emptyList(),
    /** Sidebar shortcut slots, in order. */
    val sidebarApps: List<String> = DEFAULT_SIDEBAR_APPS,
)

/** Everything persisted, read once synchronously at process start. */
data class PersistedState(
    val mode: ModeState,
    val bootCount: Int,
    val pendingBootRestore: Int,
    val savedStayOn: Int,
    val settings: UserSettings,
)

class SettingsStore(private val context: Context) {

    private object K {
        val mode = stringPreferencesKey("mode")
        val source = stringPreferencesKey("source")
        val changedAt = longPreferencesKey("changed_at")
        val lastHeadUnit = stringPreferencesKey("last_head_unit_mode")
        val bootCount = intPreferencesKey("boot_count")
        val pendingBootRestore = intPreferencesKey("pending_boot_restore")
        val savedStayOn = intPreferencesKey("saved_stay_on")
        val disabled = booleanPreferencesKey("head_unit_disabled")
        val highContrast = booleanPreferencesKey("high_contrast")
        val manageStayOn = booleanPreferencesKey("manage_stay_on")
        val accent = stringPreferencesKey("accent_override")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val themeStyle = stringPreferencesKey("theme_style")
        val recents = stringPreferencesKey("recent_packages")
        val sidebarApps = stringPreferencesKey("sidebar_apps")
    }

    val settings = context.dataStore.data.map { it.toSettings() }

    fun loadBlocking(): PersistedState = runBlocking {
        val p = context.dataStore.data.first()
        PersistedState(
            mode = ModeState(
                mode = Mode.fromId(p[K.mode]) ?: Mode.TABLET,
                source = runCatching { ActivationSource.valueOf(p[K.source] ?: "") }.getOrDefault(ActivationSource.DEFAULT),
                changedAtMillis = p[K.changedAt] ?: 0L,
                lastHeadUnitMode = Mode.fromId(p[K.lastHeadUnit]) ?: Mode.DRIVE,
            ),
            bootCount = p[K.bootCount] ?: -1,
            pendingBootRestore = p[K.pendingBootRestore] ?: -1,
            savedStayOn = p[K.savedStayOn] ?: -1,
            settings = p.toSettings(),
        )
    }

    suspend fun saveMode(state: ModeState) = edit {
        it[K.mode] = state.mode.id
        it[K.source] = state.source.name
        it[K.changedAt] = state.changedAtMillis
        it[K.lastHeadUnit] = state.lastHeadUnitMode.id
    }

    suspend fun setBootCount(count: Int) = edit { it[K.bootCount] = count }
    suspend fun setPendingBootRestore(bootCount: Int) = edit { it[K.pendingBootRestore] = bootCount }
    suspend fun setSavedStayOn(value: Int) = edit { it[K.savedStayOn] = value }
    suspend fun setHeadUnitDisabled(v: Boolean) = edit { it[K.disabled] = v }
    suspend fun setHighContrast(v: Boolean) = edit { it[K.highContrast] = v }
    suspend fun setManageStayOn(v: Boolean) = edit { it[K.manageStayOn] = v }
    suspend fun setDarkTheme(v: Boolean) = edit { it[K.darkTheme] = v }
    suspend fun setThemeStyle(id: String) = edit { it[K.themeStyle] = id }
    suspend fun setAccentOverride(hex: String?) = edit { if (hex == null) it.remove(K.accent) else it[K.accent] = hex }

    suspend fun pushRecent(pkg: String) = edit {
        val list = (it[K.recents]?.split(',')?.filter(String::isNotBlank) ?: emptyList())
        it[K.recents] = (listOf(pkg) + list.filter { p -> p != pkg }).take(6).joinToString(",")
    }

    suspend fun setSidebarApps(packages: List<String>) = edit { it[K.sidebarApps] = packages.joinToString(",") }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() = UserSettings(
        headUnitDisabled = this[K.disabled] ?: false,
        highContrast = this[K.highContrast] ?: false,
        manageStayOn = this[K.manageStayOn] ?: false,
        accentOverride = this[K.accent],
        darkTheme = this[K.darkTheme] ?: true,
        themeStyle = this[K.themeStyle] ?: "carplay",
        recentPackages = this[K.recents]?.split(',')?.filter(String::isNotBlank) ?: emptyList(),
        sidebarApps = this[K.sidebarApps]?.split(',')?.takeIf { it.size == DEFAULT_SIDEBAR_APPS.size } ?: DEFAULT_SIDEBAR_APPS,
    )
}
