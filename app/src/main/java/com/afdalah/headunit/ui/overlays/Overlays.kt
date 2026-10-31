package com.afdalah.headunit.ui.overlays

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.calls.CallState
import com.afdalah.headunit.mode.Mode
import com.afdalah.headunit.ui.components.CircleButton
import com.afdalah.headunit.ui.components.GlassPanel
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.TileButton
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuMotion
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme
import com.afdalah.headunit.ui.theme.Inter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Notification banner

/** Brief, large banner: icon, sender, short text. Tap opens the app only when parked. */
@Composable
fun NotificationBannerHost(moving: Boolean) {
    val hub = LocalContext.current.app.notificationHub
    val banner by hub.banner.collectAsStateWithLifecycle()
    val reduce = HuTheme.reduceMotion
    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = banner != null,
            enter = if (reduce) fadeIn(HuMotion.fade(true)) else slideInVertically(HuMotion.spring(false)) { -it } + fadeIn(HuMotion.fade(false)),
            exit = if (reduce) fadeOut(HuMotion.fade(true)) else slideOutVertically(HuMotion.spring(false)) { -it } + fadeOut(HuMotion.fade(false)),
        ) {
            val b = banner ?: return@AnimatedVisibility
            GlassPanel(
                Modifier
                    .widthIn(min = 560.dp, max = 760.dp)
                    .background(HuTheme.colors.background.copy(alpha = 0.85f), HuShapes.banner)
                    .huClickable(label = if (moving) "Dismiss" else "Open ${b.appLabel}") {
                        if (!moving) runCatching { b.contentIntent?.send() }
                        hub.dismiss()
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = HuShapes.banner,
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(72.dp).clip(HuShapes.appIcon).background(HuTheme.colors.fill)) {
                        b.icon?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Column(Modifier.weight(1f, fill = false)) {
                        HuText(b.sender, HuTheme.type.headline, maxLines = 1)
                        if (b.text.isNotBlank()) HuText(b.text, HuTheme.type.body, color = HuTheme.colors.labelSecondary)
                        HuText(b.appLabel, HuTheme.type.caption, color = HuTheme.colors.accent, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Short system hint ("Available when parked", errors). */
@Composable
fun HintBanner(text: String?, onDone: () -> Unit) {
    LaunchedEffect(text) {
        if (text != null) {
            delay(3_000)
            onDone()
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(text != null, enter = fadeIn(HuMotion.fade(HuTheme.reduceMotion)), exit = fadeOut(HuMotion.fade(HuTheme.reduceMotion))) {
            Row(
                Modifier
                    .clip(HuShapes.pill)
                    .background(HuTheme.colors.background.copy(alpha = 0.92f))
                    .background(HuTheme.colors.fill)
                    .semantics { liveRegion = LiveRegionMode.Assertive }
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HuIcon(R.drawable.ic_lock, null, size = HuDimens.iconSmall)
                HuText(text.orEmpty(), HuTheme.type.headline, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Mode menu

/** Long-press on the mode pill (parked only): manual override, incl. exit to Tablet. */
@Composable
fun ModeMenu(current: Mode, onSelect: (Mode) -> Unit, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    Scrim(onDismiss) {
        GlassPanel(Modifier.widthIn(max = 900.dp).background(HuTheme.colors.background.copy(alpha = 0.9f), HuShapes.card)) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HuText("Mode", HuTheme.type.title1, modifier = Modifier.weight(1f))
                    CircleButton(R.drawable.ic_x, "Close", size = HuDimens.minTarget, onClick = onDismiss)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    listOf(
                        Triple(Mode.DRIVE, R.drawable.ic_car, "Drive"),
                        Triple(Mode.NIGHT, R.drawable.ic_moon, "Night"),
                        Triple(Mode.PARKED, R.drawable.ic_circle_parking, "Parked"),
                        Triple(Mode.STANDBY, R.drawable.ic_power, "Standby"),
                    ).forEach { (mode, icon, label) ->
                        TileButton(icon, label, accent = mode == current, modifier = Modifier.weight(1f).height(128.dp)) { onSelect(mode) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    TileButton(R.drawable.ic_settings, "Settings", modifier = Modifier.weight(1f)) { onOpenSettings() }
                    TileButton(R.drawable.ic_tablet, "Exit to Tablet mode", modifier = Modifier.weight(2f)) { onSelect(Mode.TABLET) }
                }
            }
        }
    }
}

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        // Swallow taps on the panel itself.
        Box(Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) { content() }
    }
}

// ---------------------------------------------------------------- Calls

@Composable
fun CallOverlay() {
    val calls = LocalContext.current.app.calls
    val state by calls.state.collectAsStateWithLifecycle()
    val c = HuTheme.colors
    when (val s = state) {
        is CallState.Ringing -> Box(
            Modifier.fillMaxSize().background(c.background.copy(alpha = 0.96f)).semantics { liveRegion = LiveRegionMode.Assertive },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HuIcon(R.drawable.ic_phone_incoming, null, size = HuDimens.iconLarge, tint = c.green)
                HuText("Incoming call", HuTheme.type.headline, color = c.labelSecondary)
                HuText(s.caller, HuTheme.type.largeTitle, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 900.dp))
                Spacer(Modifier.height(40.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(160.dp)) {
                    CallButton(R.drawable.ic_phone_off, "Decline", c.red, calls::decline)
                    CallButton(R.drawable.ic_phone, "Accept", c.green, calls::accept)
                }
            }
        }
        is CallState.Active -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
            val secs = ((now - s.sinceMillis) / 1000).coerceAtLeast(0)
            GlassPanel(Modifier.background(c.background.copy(alpha = 0.9f), HuShapes.pill), shape = HuShapes.pill) {
                Row(Modifier.padding(start = 32.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    HuIcon(R.drawable.ic_phone, null, tint = c.green)
                    HuText(s.caller, HuTheme.type.headline, maxLines = 1, modifier = Modifier.widthIn(max = 420.dp))
                    HuText("%d:%02d".format(secs / 60, secs % 60), HuTheme.type.headline, color = c.labelSecondary, maxLines = 1)
                    CircleButton(R.drawable.ic_phone_off, "End call", size = HuDimens.minTarget, background = c.red, tint = Color.White, onClick = calls::hangUp)
                }
            }
        }
        CallState.Idle -> Unit
    }
}

@Composable
private fun CallButton(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircleButton(icon, label, size = 128.dp, background = color, tint = Color.White, onClick = onClick)
        HuText(label, HuTheme.type.headline)
    }
}

// ---------------------------------------------------------------- Standby

/** Power lost while in the car: dim clock only. Tap reveals Resume / Tablet for a few seconds. */
@Composable
fun StandbyScreen(onResume: () -> Unit, onExitToTablet: () -> Unit) {
    val context = LocalContext.current
    val c = HuTheme.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controls by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(1_000L * (60 - (System.currentTimeMillis() / 1000) % 60)); now = System.currentTimeMillis() } }
    LaunchedEffect(controls) { if (controls) { delay(8_000); controls = false } }
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = true },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HuText(
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now)),
                TextStyle(fontFamily = Inter, fontWeight = FontWeight.Light, fontSize = 128.sp, letterSpacing = (-2).sp),
                color = c.labelSecondary, maxLines = 1,
            )
            HuText(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)), HuTheme.type.title3, color = c.labelSecondary.copy(alpha = 0.7f))
            if (controls) {
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    TileButton(R.drawable.ic_car, "Resume", accent = true, modifier = Modifier.widthIn(min = 220.dp), onClick = onResume)
                    TileButton(R.drawable.ic_tablet, "Tablet mode", modifier = Modifier.widthIn(min = 220.dp), onClick = onExitToTablet)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Disabled prompt

@Composable
fun HeadUnitDisabledScreen(onEnable: () -> Unit, onStayInTablet: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.widthIn(max = 760.dp)) {
            Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                HuText("Head unit is turned off", HuTheme.type.title1)
                HuText(
                    "You switched it off in Parked settings, so routines and deep links are ignored. Turn it back on to start Drive mode.",
                    HuTheme.type.body, color = HuTheme.colors.labelSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                    TileButton(R.drawable.ic_car, "Turn on & drive", accent = true, modifier = Modifier.weight(1f), onClick = onEnable)
                    TileButton(R.drawable.ic_tablet, "Stay in Tablet", modifier = Modifier.weight(1f), onClick = onStayInTablet)
                }
            }
        }
    }
}
