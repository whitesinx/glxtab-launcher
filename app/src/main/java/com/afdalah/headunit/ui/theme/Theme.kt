package com.afdalah.headunit.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.afdalah.headunit.data.ModeProfile

private val LocalHuColors = staticCompositionLocalOf { CarPlayPalette.dark(Color(0xFF0A84FF)) }
private val LocalHuType = staticCompositionLocalOf { huTypography() }
private val LocalReduceMotion = staticCompositionLocalOf { false }
private val LocalDecorativeMotion = staticCompositionLocalOf { true }
private val LocalHighContrast = staticCompositionLocalOf { false }
internal val LocalHuShapes = staticCompositionLocalOf { HuShapeSet.CarPlay }

/** Overall look, picked in Settings → Display → Theme. */
enum class ThemeStyle(val id: String, val label: String) {
    CARPLAY("carplay", "CarPlay"),
    GLASS("glass", "Apple Glass"),
    PS5("ps5", "PS5");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: CARPLAY
    }
}

object HuTheme {
    val colors: HuColors @Composable @ReadOnlyComposable get() = LocalHuColors.current
    val type: HuTypography @Composable @ReadOnlyComposable get() = LocalHuType.current
    val reduceMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
    /** False while the vehicle is moving: no decorative motion. */
    val decorativeMotion: Boolean @Composable @ReadOnlyComposable get() = LocalDecorativeMotion.current
    val highContrast: Boolean @Composable @ReadOnlyComposable get() = LocalHighContrast.current
}

/**
 * Resolves the palette for a mode profile: style × theme × palette (standard/night) × high contrast.
 * Profile theme "auto" follows the sidebar dark/light toggle; "dark"/"light" force it.
 * Night Drive keeps its dim warm palette in every style (glare matters more than looks); PS5 is always dark.
 */
fun resolveColors(
    profile: ModeProfile,
    globalAccent: String,
    accentOverride: String?,
    darkToggle: Boolean,
    highContrast: Boolean,
    style: ThemeStyle = ThemeStyle.CARPLAY,
): HuColors {
    val fallback = Color(0xFF0A84FF)
    val styleAccent = if (style == ThemeStyle.PS5) CarPlayPalette.PS5_BLUE else globalAccent
    val accent = CarPlayPalette.parse(profile.accentColor ?: accentOverride ?: styleAccent, fallback)
    val dark = when (profile.theme) {
        "light" -> false
        "dark" -> true
        else -> darkToggle
    }
    val base = when {
        profile.palette == "night" -> CarPlayPalette.night(accent).copy(style = style)
        style == ThemeStyle.PS5 -> CarPlayPalette.ps5(accent)
        style == ThemeStyle.GLASS -> if (dark) CarPlayPalette.glassDark(accent) else CarPlayPalette.glassLight(accent)
        dark -> CarPlayPalette.dark(accent)
        else -> CarPlayPalette.light(accent)
    }
    return if (highContrast) CarPlayPalette.highContrast(base) else base
}

@Composable
fun HuTheme(
    colors: HuColors,
    isMoving: Boolean,
    highContrast: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        val cr = context.contentResolver
        Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }
    val type = remember(highContrast) { huTypography(boldText = highContrast) }
    CompositionLocalProvider(
        LocalHuColors provides colors,
        LocalHuType provides type,
        LocalReduceMotion provides reduceMotion,
        LocalDecorativeMotion provides !isMoving,
        LocalHighContrast provides highContrast,
        LocalHuShapes provides HuShapeSet.of(colors.style),
        content = content,
    )
}
