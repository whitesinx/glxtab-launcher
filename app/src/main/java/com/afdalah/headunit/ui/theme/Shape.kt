package com.afdalah.headunit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
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

/** Shape tokens. CarPlay uses large, consistent continuous corners. */
object HuShapes {
    val card: Shape = ContinuousRoundedShape(32.dp)
    val panel: Shape = ContinuousRoundedShape(28.dp)
    val button: Shape = ContinuousRoundedShape(22.dp)
    val banner: Shape = ContinuousRoundedShape(28.dp)
    /** iOS app-icon mask: radius = 22.37% of the icon size. */
    val appIcon: Shape = ContinuousRoundedShape(fraction = 0.2237f)
    val artwork: Shape = ContinuousRoundedShape(18.dp)
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
