package com.afdalah.headunit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Continuous-corner ("squircle") rounded rectangle, approximating Apple's
 * `RoundedRectangle(style: .continuous)`.
 *
 * Like iOS, the curve starts 1.528 × radius from the corner (where a circular arc would start at
 * 1 × radius) and follows a superellipse, so curvature ramps up smoothly instead of jumping.
 */
@Immutable
class ContinuousRoundedShape(
    private val radius: Dp? = null,
    /** Alternative to [radius]: radius as a fraction of the shorter side (app icons use 0.2237). */
    private val fraction: Float? = null,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radius?.let { with(density) { it.toPx() } } ?: ((fraction ?: 0f) * size.minDimension)
        val extent = min(r * SMOOTHING, size.minDimension / 2f)
        if (extent <= 0f) return Outline.Rectangle(size.toRect())
        val path = Path()
        val w = size.width
        val h = size.height
        path.moveTo(extent, 0f)
        path.lineTo(w - extent, 0f)
        corner(path, w - extent, extent, extent, 270.0)
        path.lineTo(w, h - extent)
        corner(path, w - extent, h - extent, extent, 0.0)
        path.lineTo(extent, h)
        corner(path, extent, h - extent, extent, 90.0)
        path.lineTo(0f, extent)
        corner(path, extent, extent, extent, 180.0)
        path.close()
        return Outline.Generic(path)
    }

    /** Quarter superellipse around (cx, cy), starting at [startDeg] (screen coords, y down). */
    private fun corner(path: Path, cx: Float, cy: Float, e: Float, startDeg: Double) {
        for (i in 1..STEPS) {
            val t = (startDeg + 90.0 * i / STEPS) * PI / 180.0
            val c = cos(t)
            val s = sin(t)
            val x = cx + e * sign(c).toFloat() * abs(c).pow(2.0 / EXPONENT).toFloat()
            val y = cy + e * sign(s).toFloat() * abs(s).pow(2.0 / EXPONENT).toFloat()
            path.lineTo(x, y)
        }
    }

    private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)

    override fun equals(other: Any?) = other is ContinuousRoundedShape && other.radius == radius && other.fraction == fraction
    override fun hashCode() = 31 * radius.hashCode() + fraction.hashCode()

    private companion object {
        const val SMOOTHING = 1.528f
        const val EXPONENT = 4.6
        const val STEPS = 16
    }
}

/** Corner set for a [ThemeStyle]. */
@Immutable
data class HuShapeSet(
    val card: Shape,
    val panel: Shape,
    val button: Shape,
    val banner: Shape,
    val artwork: Shape,
) {
    companion object {
        /** CarPlay: large, consistent continuous corners. */
        val CarPlay = HuShapeSet(
            card = ContinuousRoundedShape(32.dp), panel = ContinuousRoundedShape(28.dp),
            button = ContinuousRoundedShape(22.dp), banner = ContinuousRoundedShape(28.dp),
            artwork = ContinuousRoundedShape(18.dp),
        )
        /** Liquid Glass: rounder, softer. */
        val Glass = HuShapeSet(
            card = ContinuousRoundedShape(40.dp), panel = ContinuousRoundedShape(34.dp),
            button = ContinuousRoundedShape(28.dp), banner = ContinuousRoundedShape(36.dp),
            artwork = ContinuousRoundedShape(22.dp),
        )
        /** PS5: tighter, crisper corners. */
        val Ps5 = HuShapeSet(
            card = RoundedCornerShape(16.dp), panel = RoundedCornerShape(14.dp),
            button = RoundedCornerShape(12.dp), banner = RoundedCornerShape(16.dp),
            artwork = RoundedCornerShape(10.dp),
        )

        fun of(style: ThemeStyle) = when (style) {
            ThemeStyle.CARPLAY -> CarPlay
            ThemeStyle.GLASS -> Glass
            ThemeStyle.PS5 -> Ps5
        }
    }
}

/** Shape tokens; card/panel/button/banner/artwork follow the current [ThemeStyle]. */
object HuShapes {
    val card: Shape @Composable @ReadOnlyComposable get() = LocalHuShapes.current.card
    val panel: Shape @Composable @ReadOnlyComposable get() = LocalHuShapes.current.panel
    val button: Shape @Composable @ReadOnlyComposable get() = LocalHuShapes.current.button
    val banner: Shape @Composable @ReadOnlyComposable get() = LocalHuShapes.current.banner
    val artwork: Shape @Composable @ReadOnlyComposable get() = LocalHuShapes.current.artwork
    /** iOS app-icon mask: radius = 22.37% of the icon size. */
    val appIcon: Shape = ContinuousRoundedShape(fraction = 0.2237f)
    val pill: Shape = RoundedCornerShape(percent = 50)
}

/** Spacing + sizing tokens (dp). Touch targets never go below [minTarget]. */
object HuDimens {
    val minTarget = 76.dp
    val primaryButton = 96.dp
    val transportButton = 104.dp
    val sidebarWidth = 132.dp
    val gutter = 20.dp
    val cardPadding = 28.dp
    val targetSpacing = 16.dp
    val dockIcon = 88.dp
    val libraryIcon = 104.dp
    val iconSmall = 32.dp
    val iconMedium = 40.dp
    val iconLarge = 52.dp
}
