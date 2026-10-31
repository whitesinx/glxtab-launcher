package com.afdalah.headunit.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.data.Destination
import com.afdalah.headunit.media.NowPlaying
import com.afdalah.headunit.system.SystemActions
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.Screen
import com.afdalah.headunit.ui.components.AppIcon
import com.afdalah.headunit.ui.components.CircleButton
import com.afdalah.headunit.ui.components.GlassPanel
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.TileButton
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.lyrics.LyricsCard
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** (Now Playing over Lyrics) | live map, trip bar underneath. */
@Composable
fun Dashboard(moving: Boolean, speedKmh: Float?, actions: HeadUnitActions) {
    Column(
        Modifier.fillMaxSize().padding(HuDimens.gutter),
        verticalArrangement = Arrangement.spacedBy(HuDimens.gutter),
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HuDimens.gutter)) {
            Column(Modifier.weight(0.92f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(HuDimens.gutter)) {
                NowPlayingCard(moving, actions, Modifier.weight(1f).fillMaxWidth())
                LyricsCard(actions, Modifier.weight(1f).fillMaxWidth())
            }
            MapCard(moving, actions, Modifier.weight(1.25f).fillMaxHeight())
        }
        TripBar(speedKmh, moving, actions)
    }
}

@Composable
internal fun CardHeader(icon: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HuIcon(icon, null, size = HuDimens.iconSmall, tint = HuTheme.colors.accent)
        HuText(title, HuTheme.type.headline, color = HuTheme.colors.labelSecondary, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Now Playing

@Composable
private fun NowPlayingCard(moving: Boolean, actions: HeadUnitActions, modifier: Modifier) {
    val context = LocalContext.current
    val app = context.app
    val np by app.media.nowPlaying.collectAsStateWithLifecycle()
    val hasAccess by app.notificationHub.listenerConnected.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    GlassPanel(modifier) {
        val current = np
        if (current == null) {
            Column(
                Modifier.fillMaxSize().padding(HuDimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing, Alignment.CenterVertically),
            ) {
                CardHeader(R.drawable.ic_music, "Now Playing")
                HuText(if (hasAccess) "Nothing playing" else "Media controls need notification access", HuTheme.type.title3)
                if (!hasAccess && !moving) {
                    TileButton(R.drawable.ic_settings, "Grant access", accent = true, modifier = Modifier.height(HuDimens.primaryButton)) {
                        SystemActions.openNotificationAccess(context)
                    }
                } else {
                    val music = config.dock.firstOrNull { it.label == "Spotify" || it.packageName.contains("music") }
                    if (music != null && app.apps.isInstalled(music.packageName)) {
                        TileButton(R.drawable.ic_play, "Open ${music.label}", modifier = Modifier.height(HuDimens.primaryButton)) {
                            actions.launchApp(music.packageName)
                        }
                    }
                }
            }
        } else {
            NowPlayingContent(current, onOpen = { app.media.openApp() }, onPrev = app.media::previous, onPlayPause = app.media::playPause, onNext = app.media::next)
        }
    }
}

@Composable
private fun NowPlayingContent(np: NowPlaying, onOpen: () -> Unit, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val c = HuTheme.colors
    Column(Modifier.fillMaxSize().padding(HuDimens.cardPadding), verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            Modifier.fillMaxWidth().weight(1f).huClickable(label = "Open ${np.appLabel}", onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).clip(HuShapes.artwork).background(c.fill),
                contentAlignment = Alignment.Center,
            ) {
                if (np.art != null) {
                    Image(np.art, contentDescription = "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    HuIcon(R.drawable.ic_music, null, size = HuDimens.iconLarge, tint = c.labelSecondary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HuText(np.title.ifBlank { np.appLabel }, HuTheme.type.title3)
                if (np.artist.isNotBlank()) HuText(np.artist, HuTheme.type.body, color = c.labelSecondary, maxLines = 1)
                HuText(np.appLabel, HuTheme.type.caption, color = c.accent, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CircleButton(R.drawable.ic_skip_back, "Previous track", size = 88.dp, enabled = np.canSkipPrevious, onClick = onPrev)
            CircleButton(
                if (np.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (np.isPlaying) "Pause" else "Play",
                size = HuDimens.transportButton,
                background = c.accent, tint = c.onAccent,
                onClick = onPlayPause,
            )
            CircleButton(R.drawable.ic_skip_forward, "Next track", size = 88.dp, enabled = np.canSkipNext, onClick = onNext)
        }
    }
}

// ---------------------------------------------------------------- Trip bar

/** Replaces the app dock: speed, battery/heat, ETA or sun, Ask Gemini, App Library, Settings. */
@Composable
private fun TripBar(speedKmh: Float?, moving: Boolean, actions: HeadUnitActions) {
    val context = LocalContext.current
    val app = context.app
    val status by app.status.state.collectAsStateWithLifecycle()
    val guidance by app.notificationHub.guidance.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    val c = HuTheme.colors
    GlassPanel(Modifier.fillMaxWidth().height(116.dp), shape = HuShapes.panel) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = speedKmh?.let { "Speed ${it.roundToInt()} kilometers per hour" } ?: "Speed unavailable"
                },
            ) {
                HuText(speedKmh?.roundToInt()?.toString() ?: "—", HuTheme.type.largeTitle, maxLines = 1)
                HuText("km/h", HuTheme.type.headline, color = c.labelSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
            }
            BarDivider()
            val b = status.battery
            if (b.overheating) {
                BarStat(R.drawable.ic_thermometer, "${b.tempC.roundToInt()}°C", "Tablet is hot", tint = c.orange)
            } else {
                BarStat(
                    if (b.plugged) R.drawable.ic_battery_charging else R.drawable.ic_battery_medium,
                    "${b.level}%",
                    if (b.plugged) "Charging" else "Battery",
                    tint = if (b.plugged) c.green else c.labelPrimary,
                )
            }
            BarDivider()
            val g = guidance
            if (g != null && g.eta.isNotBlank()) {
                BarStat(R.drawable.ic_clock_3, g.eta, "Arrival", modifier = Modifier.weight(1f))
            } else {
                val sun = app.autoMode.nextSunTransition()
                if (sun != null) {
                    BarStat(
                        if (sun.first) R.drawable.ic_sun else R.drawable.ic_moon,
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sun.second)),
                        if (sun.first) "Sunrise" else "Sunset",
                    )
                }
            }
            if (config.obd.enabled) {
                BarDivider()
                BarStat(R.drawable.ic_gauge, "—", "RPM (phase 2)")
            }
            if (g == null || g.eta.isBlank()) Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .height(84.dp)
                    .clip(HuShapes.pill)
                    .background(c.accent)
                    .huClickable(label = "Ask Gemini") {
                        if (!SystemActions.voiceAssistant(context)) actions.showHint("Gemini isn't installed")
                    }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HuIcon(R.drawable.ic_sparkles, null, size = HuDimens.iconMedium, tint = c.onAccent)
                HuText("Ask Gemini", HuTheme.type.headline, color = c.onAccent, maxLines = 1)
            }
            // Always present (locked while moving) so targets never shift position.
            CircleButton(
                if (moving) R.drawable.ic_lock else R.drawable.ic_layout_grid, "App Library", size = 84.dp,
            ) { actions.openScreen(Screen.LIBRARY) }
            CircleButton(
                if (moving) R.drawable.ic_lock else R.drawable.ic_settings, "Settings", size = 84.dp,
            ) { actions.openScreen(Screen.SETTINGS) }
        }
    }
}

@Composable
private fun BarStat(icon: Int, value: String, label: String, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = HuTheme.colors.labelPrimary) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HuIcon(icon, null, size = HuDimens.iconMedium, tint = tint)
        Column {
            HuText(value, HuTheme.type.title3, color = tint, maxLines = 1)
            HuText(label, HuTheme.type.caption, color = HuTheme.colors.labelSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun BarDivider() {
    Box(Modifier.width(1.dp).height(56.dp).background(HuTheme.colors.separator))
}
