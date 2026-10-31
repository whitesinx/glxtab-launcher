package com.afdalah.headunit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.app
import com.afdalah.headunit.debug.DebugHooks
import com.afdalah.headunit.mode.ActivationSource
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.ui.components.OverlayWindow
import com.afdalah.headunit.ui.components.canDrawOverlays
import com.afdalah.headunit.ui.dashboard.Dashboard
import com.afdalah.headunit.ui.library.AppLibraryScreen
import com.afdalah.headunit.ui.lyrics.KaraokeScreen
import com.afdalah.headunit.ui.overlays.CallOverlay
import com.afdalah.headunit.ui.overlays.HeadUnitDisabledScreen
import com.afdalah.headunit.ui.overlays.HintBanner
import com.afdalah.headunit.ui.overlays.ModeMenu
import com.afdalah.headunit.ui.overlays.NotificationBannerHost
import com.afdalah.headunit.ui.overlays.StandbyScreen
import com.afdalah.headunit.ui.overlays.FarewellSplash
import com.afdalah.headunit.ui.overlays.WelcomeSplash
import com.afdalah.headunit.ui.settings.SettingsScreen
import com.afdalah.headunit.ui.sidebar.Sidebar
import com.afdalah.headunit.ui.theme.HuMotion
import com.afdalah.headunit.ui.theme.HuTheme
import com.afdalah.headunit.ui.theme.resolveColors

enum class Screen { DASHBOARD, LIBRARY, SETTINGS, KARAOKE }

/** Actions the screens need, so they stay free of app/singleton lookups. */
class HeadUnitActions(
    val openScreen: (Screen) -> Unit,
    val launchApp: (String) -> Unit,
    val showHint: (String) -> Unit,
    val openModeMenu: () -> Unit,
    val toggleDebug: () -> Unit,
)

@Composable
fun HeadUnitRoot(
    showDisabledPrompt: Boolean,
    homeSignal: Int,
    onDismissDisabledPrompt: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val modeState by app.modeManager.state.collectAsStateWithLifecycle()
    val settings by app.modeManager.settings.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    val driving by app.driving.state.collectAsStateWithLifecycle()
    val welcomePending by app.modeManager.welcomePending.collectAsStateWithLifecycle()
    val farewell by app.modeManager.farewell.collectAsStateWithLifecycle()

    val mode = modeState.mode
    val profile = app.configRepository.profileFor(mode)
    val colors = resolveColors(profile, config.accentColor, settings.accentOverride, settings.darkTheme, settings.highContrast)
    val moving = driving.isMoving

    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
    var menuOpen by remember { mutableStateOf(false) }
    var debugVisible by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    // HOME pressed while already in the head unit -> back to the dashboard.
    LaunchedEffect(homeSignal) { screen = Screen.DASHBOARD; menuOpen = false }

    // A greeting that can't be shown now (moving, or turned off) is dropped, not deferred.
    LaunchedEffect(welcomePending, moving, config.welcome.enabled) {
        if (welcomePending && (moving || !config.welcome.enabled)) app.modeManager.consumeWelcome()
    }

    // Distraction rule: no keyboard while moving.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(moving) { if (moving) { keyboard?.hide(); menuOpen = false } }

    // Always consume BACK: on the dashboard it must not finish the HOME activity.
    BackHandler(enabled = mode.isHeadUnit) {
        if (menuOpen) menuOpen = false else screen = Screen.DASHBOARD
    }

    RequestCorePermissionsOnce(enabled = mode.isHeadUnit && !moving)

    val actions = remember {
        HeadUnitActions(
            openScreen = { screen = it },
            launchApp = { pkg ->
                if (app.driving.state.value.isMoving && app.apps.isVideo(pkg)) {
                    hint = "Video apps are available when parked"
                } else if (app.apps.launch(pkg)) {
                    app.modeManager.pushRecent(pkg)
                } else {
                    hint = "App not installed"
                }
            },
            showHint = { hint = it },
            openModeMenu = {
                if (app.driving.state.value.isMoving) hint = "Mode menu is available when parked" else menuOpen = true
            },
            toggleDebug = { if (DebugHooks.ENABLED) debugVisible = !debugVisible },
        )
    }

    HuTheme(colors = colors, isMoving = moving, highContrast = settings.highContrast) {
        val c = HuTheme.colors
        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .background(Brush.radialGradient(listOf(c.backgroundTint, c.background.copy(alpha = 0f)))),
        ) {
            when {
                mode == Mode.TABLET && showDisabledPrompt -> HeadUnitDisabledScreen(
                    onEnable = {
                        // Switch mode before dismissing, otherwise the activity passes through to One UI Home.
                        app.modeManager.setHeadUnitDisabled(false)
                        app.modeManager.request(Mode.DRIVE, ActivationSource.MANUAL)
                        onDismissDisabledPrompt()
                    },
                    onStayInTablet = onDismissDisabledPrompt,
                )
                mode == Mode.STANDBY -> StandbyScreen(
                    onResume = { app.modeManager.request(modeState.lastHeadUnitMode, ActivationSource.MANUAL) },
                    onExitToTablet = { app.modeManager.request(Mode.TABLET, ActivationSource.MANUAL) },
                )
                else -> Row(Modifier.fillMaxSize()) {
                    Sidebar(mode = mode, moving = moving, speedKmh = driving.speedKmh, actions = actions, modifier = Modifier.fillMaxHeight())
                    val snapScreens = HuTheme.reduceMotion || moving
                    AnimatedContent(
                        targetState = screen,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        transitionSpec = {
                            fadeIn(HuMotion.fade(snapScreens)) togetherWith fadeOut(HuMotion.fade(snapScreens))
                        },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.DASHBOARD -> Dashboard(moving = moving, speedKmh = driving.speedKmh, actions = actions)
                            Screen.LIBRARY -> AppLibraryScreen(moving = moving, actions = actions)
                            Screen.SETTINGS -> SettingsScreen(moving = moving, actions = actions)
                            Screen.KARAOKE -> KaraokeScreen(actions = actions)
                        }
                    }
                }
            }

            if (mode.isHeadUnit && mode != Mode.STANDBY) NotificationBannerHost(moving = moving)
            HintBanner(hint) { hint = null }
            if (menuOpen && !moving) ModeMenu(current = mode, onSelect = { target ->
                menuOpen = false
                app.modeManager.request(target, ActivationSource.MANUAL)
            }, onDismiss = { menuOpen = false }, onOpenSettings = {
                menuOpen = false
                screen = Screen.SETTINGS
            })
            // Car-start greeting; never shown while moving (it would hide the dashboard).
            if (welcomePending && config.welcome.enabled && !moving && mode.isHeadUnit && mode != Mode.STANDBY) {
                FullScreenLayer { WelcomeSplash(config.welcome, onDone = app.modeManager::consumeWelcome) }
            }
            CallOverlay()
            if (farewell) FullScreenLayer { FarewellSplash(config.welcome) }
            DebugHooks.Overlay(visible = debugVisible)
        }
    }
}

/** Full-screen overlay window, so the splash also covers Google Maps' pop-up in the map card. */
@Composable
private fun FullScreenLayer(content: @Composable () -> Unit) {
    val context = LocalContext.current
    if (canDrawOverlays(context)) {
        val screen = remember { Rect(context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds) }
        OverlayWindow(screen, content)
    } else {
        content()
    }
}

/** Asks once per process (only when parked) for location, phone, Bluetooth and notification permissions. */
@Composable
private fun RequestCorePermissionsOnce(enabled: Boolean) {
    val context = LocalContext.current
    val app = context.app
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Restart the pieces that silently skipped work without permission.
        app.driving.stop()
        app.driving.start()
        app.calls.start()
        app.status.stop()
        app.status.start()
    }
    LaunchedEffect(enabled) {
        if (!enabled || permissionsAsked) return@LaunchedEffect
        permissionsAsked = true
        val missing = CORE_PERMISSIONS.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
}

private var permissionsAsked = false

val CORE_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.POST_NOTIFICATIONS,
)
