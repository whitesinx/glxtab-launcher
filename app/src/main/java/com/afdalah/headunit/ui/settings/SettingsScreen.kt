package com.afdalah.headunit.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.afdalah.headunit.BuildConfig
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.data.Destination
import com.afdalah.headunit.mode.ActivationSource
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.system.SystemActions
import com.afdalah.headunit.ui.CORE_PERMISSIONS
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.Screen
import com.afdalah.headunit.ui.components.AvailableWhenParked
import com.afdalah.headunit.ui.components.GlassPanel
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuSwitchRow
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.TileButton
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.library.ScreenHeader
import com.afdalah.headunit.ui.theme.CarPlayPalette
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme

/** Parked-only settings. Text input lives only here, and only while parked. */
@Composable
fun SettingsScreen(moving: Boolean, actions: HeadUnitActions) {
    Column(Modifier.fillMaxSize().padding(HuDimens.gutter)) {
        ScreenHeader("Settings") { actions.openScreen(Screen.DASHBOARD) }
        if (moving) {
            AvailableWhenParked("Settings can be changed once the car is stopped.", Modifier.fillMaxSize())
            return@Column
        }
        SettingsList(actions)
    }
}

@Composable
private fun SettingsList(actions: HeadUnitActions) {
    val context = LocalContext.current
    val app = context.app
    val mm = app.modeManager
    val settings by mm.settings.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    val configError by app.configRepository.error.collectAsStateWithLifecycle()
    val state by mm.state.collectAsStateWithLifecycle()
    val listener by app.notificationHub.listenerConnected.collectAsStateWithLifecycle()

    // Re-read permission state whenever we come back from a system settings screen.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.showHint(app.configRepository.import(uri).fold({ "Config imported" }, { "Import failed: ${it.message}" }))
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.showHint(app.configRepository.export(uri).fold({ "Config exported" }, { "Export failed: ${it.message}" }))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        app.driving.stop(); app.driving.start(); app.calls.start(); app.status.stop(); app.status.start()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = HuDimens.gutter),
        verticalArrangement = Arrangement.spacedBy(HuDimens.gutter),
    ) {
        item {
            Section("Head unit") {
                HuSwitchRow(
                    "Disable head unit",
                    "Forces Tablet mode. Routines and deep links are ignored until you turn this off.",
                    settings.headUnitDisabled,
                ) { mm.setHeadUnitDisabled(it) }
                ActionRow("Exit to Tablet mode", "Back to One UI Home now", R.drawable.ic_tablet) {
                    mm.request(Mode.TABLET, ActivationSource.MANUAL)
                }
            }
        }
        item {
            Section("Display") {
                HuSwitchRow("High contrast", "Opaque panels and bolder text for sun glare", settings.highContrast) { mm.setHighContrast(it) }
                val canWrite = app.stayOn.canWrite()
                HuSwitchRow(
                    "Stay awake while plugged in",
                    if (canWrite) "Sets stay_on_while_plugged_in only in head-unit modes, restored in Tablet mode"
                    else "Needs ./gradlew grantSecureSettings (screen already stays on while this app is visible)",
                    settings.manageStayOn && canWrite,
                ) {
                    mm.setManageStayOn(it)
                    app.stayOn.apply(state.mode.isHeadUnit)
                }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    HuText("Accent color", HuTheme.type.headline)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                        val current = settings.accentOverride ?: config.accentColor
                        CarPlayPalette.AccentChoices.forEach { (hex, name) ->
                            val color = CarPlayPalette.parse(hex, HuTheme.colors.accent)
                            val isSel = current.equals(hex, ignoreCase = true)
                            Box(
                                Modifier
                                    .size(HuDimens.minTarget)
                                    .clip(HuShapes.pill)
                                    .background(color)
                                    .border(if (isSel) 4.dp else 0.dp, HuTheme.colors.labelPrimary, HuShapes.pill)
                                    .huClickable(label = name) { mm.setAccentOverride(hex) }
                                    .semantics { contentDescription = "$name accent"; selected = isSel },
                                contentAlignment = Alignment.Center,
                            ) { if (isSel) HuIcon(R.drawable.ic_check, null, tint = CarPlayPalette.onColorFor(color)) }
                        }
                    }
                }
            }
        }
        item {
            Section("Navigation") {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    listOf("google_maps" to "Google Maps", "waze" to "Waze").forEach { (id, label) ->
                        TileButton(
                            R.drawable.ic_navigation, label,
                            accent = config.navigation.preferredApp == id,
                            modifier = Modifier.weight(1f),
                        ) { app.configRepository.update { it.copy(navigation = it.navigation.copy(preferredApp = id)) } }
                    }
                }
                TextSettingRow("Home address", config.navigation.home.address.takeUnless { it.startsWith("[") } ?: "") { v ->
                    app.configRepository.update { it.copy(navigation = it.navigation.copy(home = Destination(it.navigation.home.label, v))) }
                }
                TextSettingRow("Work address", config.navigation.work.address.takeUnless { it.startsWith("[") } ?: "") { v ->
                    app.configRepository.update { it.copy(navigation = it.navigation.copy(work = Destination(it.navigation.work.label, v))) }
                }
            }
        }
        item {
            Section("Car") {
                TextSettingRow("Car Bluetooth name", config.carBluetoothName.takeUnless { it.startsWith("[") } ?: "") { v ->
                    app.configRepository.update { it.copy(carBluetoothName = v) }
                }
                HuText(
                    "Used on boot: the last head-unit mode is restored only when plugged in and connected to this device.",
                    HuTheme.type.caption, color = HuTheme.colors.labelSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
        item {
            Section("Configuration") {
                HuText(app.configRepository.file.absolutePath, HuTheme.type.caption, color = HuTheme.colors.labelSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                configError?.let { HuText(it, HuTheme.type.caption, color = HuTheme.colors.red, modifier = Modifier.padding(horizontal = 24.dp)) }
                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    TileButton(R.drawable.ic_download, "Import", modifier = Modifier.weight(1f)) { importer.launch(arrayOf("application/json", "text/*")) }
                    TileButton(R.drawable.ic_upload, "Export", modifier = Modifier.weight(1f)) { exporter.launch("headunit-config.json") }
                    TileButton(R.drawable.ic_refresh_cw, "Reload", modifier = Modifier.weight(1f)) {
                        app.configRepository.load()
                        actions.showHint(app.configRepository.error.value ?: "Config reloaded")
                    }
                }
            }
        }
        item {
            key(refresh) {
                Section("Permissions & reliability") {
                    val missingRuntime = CORE_PERMISSIONS.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                    StatusRow("Location, phone, Bluetooth", missingRuntime.isEmpty(), "Grant") { permissions.launch(missingRuntime.toTypedArray()) }
                    StatusRow("Notification access (media, banners)", listener || SystemActions.isNotificationAccessGranted(context), "Open") {
                        SystemActions.openNotificationAccess(context)
                    }
                    StatusRow("Battery optimization off", SystemActions.isIgnoringBatteryOptimizations(context), "Allow") {
                        SystemActions.requestIgnoreBatteryOptimizations(context)
                    }
                    StatusRow("Default home app", com.afdalah.headunit.mode.HomeForwarder.isDefaultHome(context), "Open") {
                        SystemActions.openHomeSettings(context)
                    }
                }
            }
        }
        item {
            HuText(
                "Head Unit ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""} · ${state.mode.label} via ${state.source.name.lowercase()}",
                HuTheme.type.caption, color = HuTheme.colors.labelSecondary, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        HuText(title, HuTheme.type.callout, color = HuTheme.colors.labelSecondary, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
        GlassPanel(Modifier.fillMaxWidth(), shape = HuShapes.panel) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = HuDimens.minTarget).huClickable(label = title, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HuIcon(icon, null, tint = HuTheme.colors.accent)
        Column(Modifier.weight(1f)) {
            HuText(title, HuTheme.type.headline)
            HuText(subtitle, HuTheme.type.caption, color = HuTheme.colors.labelSecondary)
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, action: String, onClick: () -> Unit) {
    val c = HuTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = HuDimens.minTarget).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HuIcon(if (ok) R.drawable.ic_check else R.drawable.ic_triangle_alert, if (ok) "Done" else "Needs attention", tint = if (ok) c.green else c.orange)
        HuText(title, HuTheme.type.headline, modifier = Modifier.weight(1f))
        if (!ok) TileButton(R.drawable.ic_settings, action, modifier = Modifier.height(HuDimens.minTarget), onClick = onClick)
    }
}

/** Single-line text field; saves on Done or when focus leaves. */
@Composable
private fun TextSettingRow(title: String, value: String, onSave: (String) -> Unit) {
    val c = HuTheme.colors
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
        HuText(title, HuTheme.type.callout, color = c.labelSecondary)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = HuTheme.type.headline.copy(color = c.labelPrimary),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text != value) onSave(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .defaultMinSize(minHeight = HuDimens.minTarget)
                .clip(HuShapes.button)
                .background(c.fill)
                .onFocusChanged { if (!it.isFocused && text != value) onSave(text.trim()) }
                .semantics { contentDescription = title }
                .padding(horizontal = 20.dp, vertical = 22.dp),
        )
    }
}
