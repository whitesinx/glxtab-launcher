package com.afdalah.headunit.ui.dashboard

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.data.Destination
import com.afdalah.headunit.notifications.NavGuidance
import com.afdalah.headunit.system.SystemActions
import com.afdalah.headunit.ui.HeadUnitActions
import com.afdalah.headunit.ui.components.GlassPanel
import com.afdalah.headunit.ui.components.HuIcon
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.components.huClickable
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import kotlin.math.roundToInt

/**
 * Live map (OpenStreetMap via osmdroid; Google's map can't be embedded without Play Services).
 * It follows the GPS position and isn't draggable: tapping it opens Google Maps, which does the routing.
 * While Google Maps navigates, its next turn and ETA are mirrored on top.
 */
@Composable
fun MapCard(moving: Boolean, actions: HeadUnitActions, modifier: Modifier) {
    val context = LocalContext.current
    val app = context.app
    val location by app.driving.location.collectAsStateWithLifecycle()
    val guidance by app.notificationHub.guidance.collectAsStateWithLifecycle()
    val config by app.configRepository.config.collectAsStateWithLifecycle()
    val c = HuTheme.colors
    val dark = c.isDark

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            isHorizontalMapRepetitionEnabled = false
            controller.setZoom(16.0)
            // Jakarta until the first fix arrives.
            controller.setCenter(GeoPoint(-6.2, 106.816))
        }
    }
    // While Google Maps navigates it is shown as a window placed exactly over this card (screen px).
    val view = LocalView.current
    var cardBounds by remember { mutableStateOf<Rect?>(null) }
    val navigating = guidance != null
    val mapsInCard = config.navigation.mapsInCard
    val showMapsInCard: () -> Boolean = {
        val b = cardBounds
        b != null && SystemActions.openMapsInBounds(context, b)
    }
    LaunchedEffect(navigating, cardBounds, mapsInCard) {
        if (navigating && mapsInCard) showMapsInCard()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    map.onResume()
                    // Back on the dashboard (e.g. after HOME): put Maps back into the card.
                    if (app.notificationHub.guidance.value != null && app.configRepository.config.value.navigation.mapsInCard) showMapsInCard()
                }
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    LaunchedEffect(dark) { map.overlayManager.tilesOverlay.setColorFilter(if (dark) darkTiles else null) }
    LaunchedEffect(moving) { map.controller.zoomTo(if (moving) 16.5 else 16.0) }
    LaunchedEffect(location) {
        location?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
    }

    val openMaps: () -> Unit = {
        val g = guidance
        if (g != null && mapsInCard && showMapsInCard()) {
            Unit
        } else if (g?.contentIntent != null) {
            runCatching { g.contentIntent.send() }
        } else if (!SystemActions.openMaps(context)) {
            actions.showHint("Install Google Maps")
        }
    }

    val measured = modifier.onGloballyPositioned { coords ->
        val r = coords.boundsInWindow()
        val origin = IntArray(2).also { view.getLocationOnScreen(it) }
        val next = Rect(
            origin[0] + r.left.roundToInt(), origin[1] + r.top.roundToInt(),
            origin[0] + r.right.roundToInt(), origin[1] + r.bottom.roundToInt(),
        )
        if (next != cardBounds && next.width() > 0) cardBounds = next
    }
    if (navigating && mapsInCard) {
        // Google Maps' own window fills this slot. Draw only the page background (no panel, border or map),
        // so nothing of ours shows around it; a tap here brings Maps back if it was moved or closed.
        Box(
            measured.clip(HuShapes.card).background(if (c.isDark) GoogleMapsNavy else GoogleMapsDay)
                .huClickable(label = "Show Google Maps") { showMapsInCard() },
        )
        return
    }
    GlassPanel(measured) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        // Swallows touches (no panning) and opens Google Maps.
        Box(Modifier.fillMaxSize().huClickable(label = "Open Google Maps", onClick = openMaps))

        // Position puck: the map is always centered on the car.
        val loc = location
        Box(Modifier.align(Alignment.Center)) {
            if (loc != null && loc.hasBearing() && (loc.speed > 1f)) {
                Box(
                    Modifier.size(52.dp).clip(HuShapes.pill).background(Color.White).padding(4.dp)
                        .clip(HuShapes.pill).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    // Lucide "navigation" points up-right (45°).
                    HuIcon(R.drawable.ic_navigation, null, size = 26.dp, tint = Color.White, modifier = Modifier.rotate(loc.bearing - 45f))
                }
            } else {
                Box(Modifier.size(30.dp).clip(HuShapes.pill).background(Color.White).padding(5.dp).clip(HuShapes.pill).background(c.accent))
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            val g = guidance
            if (g != null) GuidanceChip(g, openMaps) else MapChip(R.drawable.ic_search, "Where to?", onClick = openMaps)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HuDimens.targetSpacing)) {
                val nav = config.navigation
                listOf(R.drawable.ic_house to nav.home, R.drawable.ic_briefcase to nav.work)
                    .filter { it.second.isConfigured }
                    .forEach { (icon, dest) -> DestinationChip(icon, dest, nav.preferredApp, actions) }
            }
        }
    }
}

@Composable
private fun GuidanceChip(g: NavGuidance, onClick: () -> Unit) {
    val c = HuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HuShapes.button)
            .background(c.background.copy(alpha = 0.9f))
            .border(1.dp, c.glassBorder, HuShapes.button)
            .huClickable(label = "Navigation: ${g.title} ${g.text}", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (g.maneuver != null) {
            Image(g.maneuver, null, Modifier.size(64.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(c.labelPrimary))
        } else {
            HuIcon(R.drawable.ic_navigation, null, size = 48.dp, tint = c.accent)
        }
        Column(Modifier.weight(1f)) {
            HuText(g.title, HuTheme.type.title2, maxLines = 1)
            if (g.text.isNotBlank()) HuText(g.text, HuTheme.type.body, color = c.labelSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun MapChip(icon: Int, label: String, onClick: () -> Unit) {
    val c = HuTheme.colors
    Row(
        Modifier
            .height(HuDimens.minTarget)
            .clip(HuShapes.pill)
            .background(c.background.copy(alpha = 0.88f))
            .border(1.dp, c.glassBorder, HuShapes.pill)
            .huClickable(label = label, onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HuIcon(icon, null, size = HuDimens.iconSmall, tint = c.accent)
        HuText(label, HuTheme.type.headline, maxLines = 1)
    }
}

@Composable
private fun DestinationChip(icon: Int, dest: Destination, preferredApp: String, actions: HeadUnitActions) {
    val context = LocalContext.current
    MapChip(icon, dest.label) {
        if (!SystemActions.navigateTo(context, dest, preferredApp)) actions.showHint("Install Google Maps or Waze")
    }
}

/** Base colours of Google Maps' navigation map (sampled on the device), so the slot blends into its window. */
private val GoogleMapsNavy = Color(0xFF264057)
private val GoogleMapsDay = Color(0xFFE9EDF1)

/**
 * OpenStreetMap tiles restyled for dark mode: invert, rotate the hue back (water stays blue, parks green),
 * desaturate to 40% and cool/dim toward the navy of CarPlay's night map.
 */
private val darkTiles = ColorMatrixColorFilter(
    ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    )).apply {
        postConcat(ColorMatrix(floatArrayOf(
            -0.574f, 1.430f, 0.144f, 0f, 0f,
            0.426f, 0.430f, 0.144f, 0f, 0f,
            0.426f, 1.430f, -0.856f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        postConcat(ColorMatrix(floatArrayOf(
            0.5276f, 0.4291f, 0.0433f, 0f, 0f,
            0.1276f, 0.8291f, 0.0433f, 0f, 0f,
            0.1276f, 0.4291f, 0.4433f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        postConcat(ColorMatrix().apply { setScale(0.80f, 0.85f, 0.97f, 1f) })
    },
)
