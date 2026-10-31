package com.afdalah.headunit.ui.sidebar

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.sensors.NetworkType
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.Screen
import com.afdalah.headunit.ui.components.AppIcon
import com.afdalah.headunit.ui.components.CircleButton
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme
import com.afdalah.headunit.system.SystemActions
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** CarPlay-style persistent left sidebar: status, quick app switcher, home, voice. */
@Composable
fun Sidebar(mode: Mode, moving: Boolean, speedKmh: Float?, actions: HeadUnitActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val c = HuTheme.colors
    val status by app.status.state.collectAsStateWithLifecycle()
    val settings by app.modeManager.settings.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()

    Column(
        modifier
            .width(HuDimens.sidebarWidth)
            .background(c.glassFill)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Clock()
        ModePill(mode, onTap = actions.toggleDebug, onLongPress = actions.openModeMenu)
        ThemeToggle(mode, dark = settings.darkTheme) { app.modeManager.setDarkTheme(it) }

        // Status: signal, Bluetooth, heat, speed.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val net = status.network
            when (net.type) {
                NetworkType.WIFI -> HuIcon(R.drawable.ic_wifi, "Wi-Fi, ${net.wifiLevel} of 4 bars", size = HuDimens.iconSmall,
                    tint = if (net.wifiLevel <= 1) c.labelSecondary else c.labelPrimary)
                NetworkType.CELLULAR -> HuIcon(signalIcon(net.cellLevel), "LTE, ${net.cellLevel} of 4 bars", size = HuDimens.iconSmall)
                NetworkType.NONE -> HuIcon(R.drawable.ic_wifi_off, "No network", size = HuDimens.iconSmall, tint = c.labelSecondary)
            }
            val bt = status.bluetooth
            HuIcon(
                if (bt.connected.isNotEmpty()) R.drawable.ic_bluetooth_connected else if (bt.enabled) R.drawable.ic_bluetooth else R.drawable.ic_bluetooth_off,
                if (bt.connected.isNotEmpty()) "Bluetooth connected to ${bt.connected.joinToString()}" else "Bluetooth not connected",
                size = HuDimens.iconSmall,
                tint = if (bt.connected.isNotEmpty()) c.accent else c.labelSecondary,
            )
        }
        if (status.battery.overheating) {
            Row(
                Modifier.clip(HuShapes.pill).background(c.orange.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HuIcon(R.drawable.ic_thermometer, "Battery hot", size = 26.dp, tint = c.orange)
                HuText("${status.battery.tempC.roundToInt()}°", HuTheme.type.caption, color = c.orange, maxLines = 1)
            }
        }
        if (speedKmh != null) {
            HuText(
                "${speedKmh.roundToInt()} km/h",
                HuTheme.type.callout,
                color = if (moving) c.labelPrimary else c.labelSecondary,
                maxLines = 1,
                modifier = Modifier.semantics { contentDescription = "Speed ${speedKmh.roundToInt()} kilometers per hour" },
            )
        }

        // Quick app switcher: recently used dock apps, as many as fit.
        val installed = config.dock.map { it.packageName }.filter { app.apps.isInstalled(it) }
        val recents = (settings.recentPackages.filter { app.apps.isInstalled(it) } + installed).distinct()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val count = ((maxHeight.value + 14) / (HuDimens.minTarget.value + 14)).toInt().coerceIn(0, 3)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                recents.take(count).forEach { pkg ->
                    AppIcon(
                        pkg,
                        size = HuDimens.minTarget,
                        modifier = Modifier
                            .huClickable(label = "Open ${appLabel(context, pkg)}") { actions.launchApp(pkg) }
                            .semantics { contentDescription = appLabel(context, pkg) },
                    )
                }
            }
        }

        // Home + voice: excluded from the system back gesture so the left edge doesn't eat taps.
        Column(
            Modifier.systemGestureExclusion(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircleButton(R.drawable.ic_house, "Dashboard", size = 88.dp) { actions.openScreen(Screen.DASHBOARD) }
            CircleButton(
                R.drawable.ic_mic, "Voice assistant",
                size = HuDimens.primaryButton,
                background = c.accent, tint = c.onAccent,
            ) { if (!SystemActions.voiceAssistant(context)) actions.showHint("No voice assistant installed") }
        }
    }
}

/** Dark/light switch. Night Drive and Standby are always dark, so it is dimmed there. */
@Composable
private fun ThemeToggle(mode: Mode, dark: Boolean, onChange: (Boolean) -> Unit) {
    val alwaysDark = mode == Mode.NIGHT || mode == Mode.STANDBY
    CircleButton(
        icon = if (dark || alwaysDark) R.drawable.ic_moon else R.drawable.ic_sun,
        contentDescription = when {
            alwaysDark -> "Dark mode, always on in ${mode.label}"
            dark -> "Dark mode on. Switch to light mode"
            else -> "Light mode on. Switch to dark mode"
        },
        size = HuDimens.minTarget,
        enabled = !alwaysDark,
    ) { onChange(!dark) }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    HuText(SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)), HuTheme.type.title3, maxLines = 1, textAlign = TextAlign.Center)
}

/** Subtle mode pill. 5 taps = debug overlay (debug builds), long-press = mode menu (parked only). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModePill(mode: Mode, onTap: () -> Unit, onLongPress: () -> Unit) {
    val c = HuTheme.colors
    val view = LocalView.current
    val taps = remember { mutableListOf<Long>() }
    Box(
        Modifier
            .defaultMinSize(minWidth = 96.dp, minHeight = 44.dp)
            .clip(HuShapes.pill)
            .background(c.fill)
            .border(1.dp, c.glassBorder, HuShapes.pill)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onLongPress()
                },
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    val t = System.currentTimeMillis()
                    taps.removeAll { t - it > 2_000 }
                    taps += t
                    if (taps.size >= 5) {
                        taps.clear()
                        onTap()
                    }
                },
            )
            .semantics {
                contentDescription = "Mode: ${mode.label}"
                onLongClick(label = "Change mode") { onLongPress(); true }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        HuText(if (mode == Mode.NIGHT) "Night" else mode.label, HuTheme.type.caption, color = c.labelSecondary, maxLines = 1)
    }
}

private fun signalIcon(level: Int) = when (level) {
    0 -> R.drawable.ic_signal_zero
    1 -> R.drawable.ic_signal_low
    2 -> R.drawable.ic_signal_medium
    3 -> R.drawable.ic_signal_high
    else -> R.drawable.ic_signal
}

private fun appLabel(context: android.content.Context, pkg: String) = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)
