package com.afdalah.headunit.mode

import android.content.Context
import android.provider.Settings
import com.afdalah.headunit.Log
import com.afdalah.headunit.data.SettingsStore
import com.afdalah.headunit.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for the active mode.
 *
 * Rules enforced here:
 *  - Tablet is the default on install and after every reboot (boot-restore is decided by [BootRestore]).
 *  - "Disable head unit" blocks every head-unit mode until it is switched off again.
 *  - Auto switching ([requestAuto]) only moves between head-unit modes; it can never leave or enter Tablet.
 */
class ModeManager(
    private val context: Context,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    enum class Result { APPLIED, UNCHANGED, BLOCKED_DISABLED, DEFERRED }

    private val _state = MutableStateFlow(ModeState())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    /** Car disconnected: the goodbye screen is up; Tablet follows once the screen is locked ([CarSession]). */
    private val _farewell = MutableStateFlow(false)
    val farewell: StateFlow<Boolean> = _farewell.asStateFlow()

    fun setFarewell(active: Boolean) { _farewell.value = active }

    /** True after Tablet -> head unit, until the welcome splash has been shown. */
    private val _welcomePending = MutableStateFlow(false)
    val welcomePending: StateFlow<Boolean> = _welcomePending.asStateFlow()

    val currentBootCount: Int by lazy {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    }

    /** Boot count for which a head-unit mode should still be restored (see [BootRestore]). */
    @Volatile var pendingBootRestore: Int = -1
        private set

    var savedStayOn: Int = -1
        private set

    /** Synchronous so the very first HOME intent already knows whether to pass through. */
    fun init() {
        val p = store.loadBlocking()
        _settings.value = p.settings
        savedStayOn = p.savedStayOn
        pendingBootRestore = p.pendingBootRestore
        scope.launch { store.settings.collect { _settings.value = it } }

        if (p.bootCount != currentBootCount) {
            // First process start since a reboot (or a fresh install): always Tablet.
            val restore = p.bootCount != -1 && p.mode.mode.isHeadUnit
            _state.value = ModeState(Mode.TABLET, ActivationSource.BOOT, System.currentTimeMillis(), p.mode.lastHeadUnitMode)
            pendingBootRestore = if (restore) currentBootCount else -1
            Log.i("Boot detected (count=$currentBootCount); starting in Tablet mode, restore candidate=$restore")
            scope.launch {
                store.saveMode(_state.value)
                store.setBootCount(currentBootCount)
                store.setPendingBootRestore(pendingBootRestore)
            }
        } else {
            _state.value = p.mode
            Log.i("Restored ${p.mode.mode} after process restart")
        }
    }

    fun request(target: Mode, source: ActivationSource): Result {
        val current = _state.value
        if (target.isHeadUnit && _settings.value.headUnitDisabled) {
            Log.i("Ignoring $target from $source: head unit is disabled")
            return Result.BLOCKED_DISABLED
        }
        // The disconnect routine's "Tablet" would cut the goodbye screen short; CarSession switches after locking.
        if (_farewell.value && target == Mode.TABLET && source != ActivationSource.BLUETOOTH) {
            Log.i("Deferring Tablet from $source: goodbye screen in progress")
            return Result.DEFERRED
        }
        if (current.mode == target) return Result.UNCHANGED
        val next = ModeState(
            mode = target,
            source = source,
            changedAtMillis = System.currentTimeMillis(),
            lastHeadUnitMode = if (target.isHeadUnit && target != Mode.STANDBY) target else current.lastHeadUnitMode,
        )
        _state.value = next
        if (current.mode == Mode.TABLET && target.isHeadUnit && target != Mode.STANDBY) _welcomePending.value = true
        if (!target.isHeadUnit) _welcomePending.value = false
        if (target.isHeadUnit || source != ActivationSource.BOOT) clearBootRestore()
        Log.i("Mode ${current.mode} -> $target ($source)")
        scope.launch { store.saveMode(next) }
        return Result.APPLIED
    }

    /** Used by the GPS-speed / sunset / power automation. Never touches Tablet mode. */
    fun requestAuto(target: Mode): Result {
        if (!_state.value.mode.isHeadUnit || !target.isHeadUnit) return Result.UNCHANGED
        return request(target, ActivationSource.AUTO)
    }

    fun consumeWelcome() { _welcomePending.value = false }

    fun clearBootRestore() {
        if (pendingBootRestore == -1) return
        pendingBootRestore = -1
        scope.launch { store.setPendingBootRestore(-1) }
    }

    fun setHeadUnitDisabled(disabled: Boolean) {
        _settings.update { it.copy(headUnitDisabled = disabled) }
        scope.launch { store.setHeadUnitDisabled(disabled) }
        if (disabled) request(Mode.TABLET, ActivationSource.MANUAL)
    }

    fun setHighContrast(v: Boolean) {
        _settings.update { it.copy(highContrast = v) }
        scope.launch { store.setHighContrast(v) }
    }

    fun setManageStayOn(v: Boolean) {
        _settings.update { it.copy(manageStayOn = v) }
        scope.launch { store.setManageStayOn(v) }
    }

    fun setDarkTheme(dark: Boolean) {
        _settings.update { it.copy(darkTheme = dark) }
        scope.launch { store.setDarkTheme(dark) }
    }

    fun setAccentOverride(hex: String?) {
        _settings.update { it.copy(accentOverride = hex) }
        scope.launch { store.setAccentOverride(hex) }
    }

    fun rememberSavedStayOn(value: Int) {
        savedStayOn = value
        scope.launch { store.setSavedStayOn(value) }
    }

    fun pushRecent(pkg: String) {
        _settings.update { s -> s.copy(recentPackages = (listOf(pkg) + s.recentPackages.filter { it != pkg }).take(6)) }
        scope.launch { store.pushRecent(pkg) }
    }
}
