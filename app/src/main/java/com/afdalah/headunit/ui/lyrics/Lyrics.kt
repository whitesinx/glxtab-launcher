package com.afdalah.headunit.ui.lyrics

import android.os.Build
import android.graphics.Rect
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.afdalah.headunit.notifications.NavGuidance
import com.afdalah.headunit.ui.components.OverlayWindow
import com.afdalah.headunit.ui.components.canDrawOverlays
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.lyrics.Lrc
import com.afdalah.headunit.lyrics.LyricLine
import com.afdalah.headunit.lyrics.Lyrics
import com.afdalah.headunit.media.NowPlaying
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.Screen
import com.afdalah.headunit.ui.components.CircleButton
import com.afdalah.headunit.ui.components.GlassPanel
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuMotion
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme
import com.afdalah.headunit.ui.theme.Inter
import kotlinx.coroutines.delay

/** Highlight a line slightly before it is sung. */
private const val LEAD_MS = 250L

/** Extrapolated playback position, ticking while playing. */
@Composable
private fun rememberPosition(np: NowPlaying?, periodMs: Long): State<Long> = produceState(np?.positionNow() ?: 0L, np) {
    while (true) {
        value = np?.positionNow() ?: 0L
        if (np?.isPlaying != true) break
        delay(periodMs)
    }
}

// ---------------------------------------------------------------- Dashboard card

/** Under Now Playing: the current line with its neighbours. Tap for karaoke mode. */
@Composable
fun LyricsCard(actions: HeadUnitActions, modifier: Modifier) {
    val app = LocalContext.current.app
    val lyrics by app.lyrics.lyrics.collectAsStateWithLifecycle()
    val np by app.media.nowPlaying.collectAsStateWithLifecycle()
    val c = HuTheme.colors
    GlassPanel(modifier.huClickable(label = "Lyrics, open karaoke mode") { actions.openScreen(Screen.KARAOKE) }) {
        Column(Modifier.fillMaxSize().padding(HuDimens.cardPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HuIcon(R.drawable.ic_mic_vocal, null, size = HuDimens.iconSmall, tint = c.accent)
                HuText("Lyrics", HuTheme.type.headline, color = c.labelSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                HuIcon(R.drawable.ic_maximize_2, null, size = HuDimens.iconSmall, tint = c.labelSecondary)
            }
            when (val l = lyrics) {
                is Lyrics.Ready -> {
                    val pos by rememberPosition(np, 200L)
                    val idx = if (l.synced) Lrc.indexAt(l.lines, pos + LEAD_MS) else 0
                    val from = idx.coerceAtLeast(0)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        l.lines.drop(from).filter { it.text.isNotBlank() }.take(3).forEachIndexed { i, line ->
                            val current = i == 0 && idx >= 0
                            HuText(
                                line.text,
                                if (current) HuTheme.type.title2 else HuTheme.type.title3,
                                color = if (current) c.labelPrimary else c.labelSecondary.copy(alpha = 0.55f),
                                maxLines = if (current) 2 else 1,
                            )
                        }
                    }
                }
                else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    HuText(statusText(l, np), HuTheme.type.title3, color = c.labelSecondary)
                }
            }
        }
    }
}

private fun statusText(l: Lyrics, np: NowPlaying?) = when (l) {
    Lyrics.Loading -> "Finding lyrics…"
    Lyrics.NotFound -> "No lyrics for this song"
    Lyrics.Instrumental -> "Instrumental"
    Lyrics.Unavailable -> "Lyrics service is busy right now"
    else -> if (np == null) "Play something to see lyrics" else "Lyrics unavailable"
}

// ---------------------------------------------------------------- Karaoke mode

/**
 * Full-screen lyrics (the sidebar stays). Current line large, auto-scrolling.
 * While Google Maps sits in the map card, karaoke is drawn in an overlay window so it covers Maps too;
 * only the next turn stays visible, as a chip in the top-right corner.
 */
@Composable
fun KaraokeScreen(actions: HeadUnitActions) {
    val context = LocalContext.current
    val app = context.app
    val guidance by app.notificationHub.guidance.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    val view = LocalView.current
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val overMaps = guidance != null && config.navigation.mapsInCard && canDrawOverlays(context)

    Box(
        Modifier.fillMaxSize().onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            val origin = IntArray(2).also { view.getLocationOnScreen(it) }
            val next = Rect(
                origin[0] + r.left.roundToInt(), origin[1] + r.top.roundToInt(),
                origin[0] + r.right.roundToInt(), origin[1] + r.bottom.roundToInt(),
            )
            if (next != bounds && next.width() > 0) bounds = next
        },
    ) {
        val b = bounds
        if (overMaps && b != null) {
            OverlayWindow(b) { KaraokeContent(actions, opaque = true) }
        } else {
            KaraokeContent(actions, opaque = false)
        }
    }
}

@Composable
private fun KaraokeContent(actions: HeadUnitActions, opaque: Boolean) {
    val app = LocalContext.current.app
    val lyrics by app.lyrics.lyrics.collectAsStateWithLifecycle()
    val np by app.media.nowPlaying.collectAsStateWithLifecycle()
    val guidance by app.notificationHub.guidance.collectAsStateWithLifecycle()
    val c = HuTheme.colors
    val reduceMotion = HuTheme.reduceMotion

    Box(Modifier.fillMaxSize().then(if (opaque) Modifier.background(c.background) else Modifier).clip(HuShapes.card)) {
        // Blurred album art wash (blur needs API 31+, otherwise just a dim image).
        np?.art?.let {
            Image(
                it, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(80.dp) else Modifier)
                    .graphicsLayer { alpha = if (c.isDark) 0.45f else 0.3f },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0.35f), c.background.copy(alpha = 0.85f)))))

        Column(Modifier.fillMaxSize().padding(HuDimens.cardPadding)) {
            KaraokeHeader(np, guidance, onClose = { actions.openScreen(Screen.DASHBOARD) })
            when (val l = lyrics) {
                is Lyrics.Ready -> KaraokeLines(l.lines, l.synced, np, reduceMotion, Modifier.weight(1f))
                else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HuText(statusText(l, np), HuTheme.type.title1, color = c.labelSecondary)
                }
            }
            if (np != null) KaraokeTransport(np!!)
        }
    }
}

@Composable
private fun KaraokeHeader(np: NowPlaying?, guidance: NavGuidance?, onClose: () -> Unit) {
    val c = HuTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(84.dp).clip(HuShapes.artwork).background(c.fill), contentAlignment = Alignment.Center) {
            np?.art?.let { Image(it, "Album art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: HuIcon(R.drawable.ic_music, null, tint = c.labelSecondary)
        }
        Column(Modifier.weight(1f)) {
            HuText(np?.title?.ifBlank { null } ?: "Karaoke", HuTheme.type.title3, maxLines = 1)
            np?.artist?.takeIf { it.isNotBlank() }?.let { HuText(it, HuTheme.type.body, color = c.labelSecondary, maxLines = 1) }
        }
        if (guidance != null) TurnChip(guidance)
        CircleButton(R.drawable.ic_x, "Close karaoke", size = 84.dp, onClick = onClose)
    }
}

/** Next turn only (maneuver arrow, distance, street), from Google Maps' navigation notification. */
@Composable
private fun TurnChip(g: NavGuidance) {
    val c = HuTheme.colors
    Row(
        Modifier
            .widthIn(max = 460.dp)
            .clip(HuShapes.button)
            .background(c.background.copy(alpha = 0.85f))
            .border(1.dp, c.glassBorder, HuShapes.button)
            .semantics(mergeDescendants = true) { contentDescription = "Next turn: ${g.title} ${g.text}" }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (g.maneuver != null) {
            Image(g.maneuver, null, Modifier.size(52.dp), colorFilter = ColorFilter.tint(c.labelPrimary))
        } else {
            HuIcon(R.drawable.ic_navigation, null, size = 40.dp, tint = c.accent)
        }
        Column {
            HuText(g.title, HuTheme.type.title3, maxLines = 1)
            if (g.text.isNotBlank()) HuText(g.text, HuTheme.type.callout, color = c.labelSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun KaraokeTransport(np: NowPlaying) {
    val app = LocalContext.current.app
    val c = HuTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(R.drawable.ic_skip_back, "Previous track", size = 84.dp, enabled = np.canSkipPrevious, onClick = app.media::previous)
        CircleButton(
            if (np.isPlaying) R.drawable.ic_pause else R.drawable.ic_play, if (np.isPlaying) "Pause" else "Play",
            size = 96.dp, background = c.accent, tint = c.onAccent, onClick = app.media::playPause,
        )
        CircleButton(R.drawable.ic_skip_forward, "Next track", size = 84.dp, enabled = np.canSkipNext, onClick = app.media::next)
    }
}

@Composable
private fun KaraokeLines(lines: List<LyricLine>, synced: Boolean, np: NowPlaying?, reduceMotion: Boolean, modifier: Modifier) {
    val c = HuTheme.colors
    val pos by rememberPosition(np, 100L)
    val idx = if (synced) Lrc.indexAt(lines, pos + LEAD_MS) else -1
    val list = rememberLazyListState()
    LaunchedEffect(idx) {
        if (idx >= 0) { if (reduceMotion) list.scrollToItem(idx) else list.animateScrollToItem(idx) }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyColumn(
            state = list,
            // The scrolled-to line lands about a third of the way down.
            contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            userScrollEnabled = !synced,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { i, line ->
                val current = i == idx
                val past = synced && i < idx
                val color by animateColorAsState(
                    when {
                        current -> c.labelPrimary
                        past -> c.labelSecondary.copy(alpha = 0.35f)
                        synced -> c.labelSecondary.copy(alpha = 0.6f)
                        else -> c.labelPrimary
                    },
                    HuMotion.fade(reduceMotion), label = "lyricColor",
                )
                val scale by animateFloatAsState(if (current) 1f else 0.94f, HuMotion.spring(reduceMotion), label = "lyricScale")
                HuText(
                    line.text.ifBlank { "♪" },
                    TextStyle(
                        fontFamily = Inter,
                        fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = 48.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp,
                    ),
                    color = color,
                    modifier = Modifier.fillMaxWidth().graphicsLayer {
                        scaleX = scale; scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    },
                )
            }
        }
    }
}
