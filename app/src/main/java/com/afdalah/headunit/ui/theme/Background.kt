package com.afdalah.headunit.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Full-screen wallpaper behind everything, drawn per [ThemeStyle].
 * Static (no animation) so it costs nothing while driving.
 */
@Composable
fun HuBackground(modifier: Modifier = Modifier) {
    val c = HuTheme.colors
    Canvas(modifier.fillMaxSize().background(c.background)) {
        when {
            c.night || c.style == ThemeStyle.CARPLAY -> carPlay(c)
            c.style == ThemeStyle.GLASS -> glass(c)
            else -> ps5(c)
        }
    }
}

private fun DrawScope.carPlay(c: HuColors) {
    drawRect(Brush.radialGradient(listOf(c.backgroundTint, Color.Transparent), center = center, radius = size.maxDimension / 2f))
}

/** Soft, out-of-focus colour blobs: what Liquid Glass panels refract. */
private fun DrawScope.glass(c: HuColors) {
    val a = if (c.isDark) 1f else 0.7f
    blob(c.accent.copy(alpha = 0.55f * a), Offset(size.width * 0.18f, size.height * 0.15f), size.maxDimension * 0.55f)
    blob(Color(0xFFBF5AF2).copy(alpha = 0.45f * a), Offset(size.width * 0.85f, size.height * 0.25f), size.maxDimension * 0.5f)
    blob(Color(0xFF64D2FF).copy(alpha = 0.40f * a), Offset(size.width * 0.65f, size.height * 0.95f), size.maxDimension * 0.55f)
    blob(Color(0xFFFF375F).copy(alpha = 0.28f * a), Offset(size.width * 0.05f, size.height * 0.95f), size.maxDimension * 0.4f)
    // Slight vignette keeps edges calm and text legible.
    val edge = if (c.isDark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.25f)
    drawRect(Brush.radialGradient(listOf(Color.Transparent, edge), center = center, radius = size.maxDimension * 0.75f))
}

/** Deep navy fade with a cool glow from the top right and faint light ribbons, like the PS5 home screen. */
private fun DrawScope.ps5(c: HuColors) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1A3F), Color(0xFF050C22), Color(0xFF02040C))))
    blob(c.accent.copy(alpha = 0.38f), Offset(size.width * 0.92f, size.height * 0.02f), size.maxDimension * 0.6f)
    blob(Color(0xFF2B5CC8).copy(alpha = 0.22f), Offset(size.width * 0.1f, size.height * 1.05f), size.maxDimension * 0.45f)
    val ribbon = Color(0xFF8FB4FF)
    for (i in 0 until 3) {
        val y = size.height * (0.30f + i * 0.09f)
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, ribbon.copy(alpha = 0.10f - i * 0.025f), Color.Transparent)),
            start = Offset(0f, y + size.height * 0.25f),
            end = Offset(size.width, y - size.height * 0.15f),
            strokeWidth = size.height * (0.010f - i * 0.002f),
        )
    }
}

private fun DrawScope.blob(color: Color, at: Offset, radius: Float) {
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = at, radius = radius), radius = radius, center = at)
}
