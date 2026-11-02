package com.afdalah.headunit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.toColorInt

/**
 * Color tokens, named after their Apple HIG / CarPlay equivalents.
 *
 *  HIG token                    -> here
 *  systemBackground             -> background
 *  (CarPlay card material)      -> glassFill / glassBorder / glassHighlight
 *  secondarySystemFill          -> fill
 *  label / secondaryLabel       -> labelPrimary / labelSecondary
 *  separator                    -> separator
 *  tintColor (app accent)       -> accent / onAccent
 *  systemRed/Green/Orange       -> red / green / orange
 *
 * Every label/background pair is chosen to pass WCAG AA (4.5:1 body text, 3:1 large text & icons).
 */
@Immutable
data class HuColors(
    val isDark: Boolean,
    val style: ThemeStyle = ThemeStyle.CARPLAY,
    /** Night Drive palette: the wallpaper is drawn dimmer. */
    val night: Boolean = false,
    val background: Color,
    val backgroundTint: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val fill: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color,
    val green: Color,
    val orange: Color,
)

object CarPlayPalette {

    /** Default CarPlay look: true black, white labels (secondary ≈ 9:1 on black). */
    fun dark(accent: Color) = HuColors(
        isDark = true,
        background = Color(0xFF000000),
        backgroundTint = accent.copy(alpha = 0.10f),
        glassFill = Color.White.copy(alpha = 0.08f),
        glassBorder = Color.White.copy(alpha = 0.12f),
        glassHighlight = Color.White.copy(alpha = 0.06f),
        fill = Color.White.copy(alpha = 0.12f),
        labelPrimary = Color(0xFFFFFFFF),
        labelSecondary = Color(0xFFEBEBF5).copy(alpha = 0.72f),
        separator = Color.White.copy(alpha = 0.16f),
        accent = accent,
        onAccent = onColorFor(accent),
        red = Color(0xFFFF453A),
        green = Color(0xFF30D158),
        orange = Color(0xFFFF9F0A),
    )

    /** Light mode for daylight; secondary label darkened from HIG's 60% to 75% to stay above 4.5:1. */
    fun light(accent: Color) = HuColors(
        isDark = false,
        background = Color(0xFFF2F2F7),
        backgroundTint = accent.copy(alpha = 0.06f),
        glassFill = Color.White.copy(alpha = 0.78f),
        glassBorder = Color.Black.copy(alpha = 0.08f),
        glassHighlight = Color.White.copy(alpha = 0.6f),
        fill = Color(0xFF787880).copy(alpha = 0.16f),
        labelPrimary = Color(0xFF000000),
        labelSecondary = Color(0xFF3C3C43).copy(alpha = 0.78f),
        separator = Color(0xFF3C3C43).copy(alpha = 0.24f),
        accent = accent,
        onAccent = onColorFor(accent),
        red = Color(0xFFD70015),
        green = Color(0xFF248A3D),
        orange = Color(0xFFC93400),
    )

    /** Night Drive: dimmer, warmer labels, lower-luminance glass to cut cabin glare. */
    fun night(accent: Color) = HuColors(
        isDark = true,
        night = true,
        background = Color(0xFF000000),
        backgroundTint = accent.copy(alpha = 0.05f),
        glassFill = Color(0xFFFFE8CC).copy(alpha = 0.05f),
        glassBorder = Color(0xFFFFE8CC).copy(alpha = 0.08f),
        glassHighlight = Color.Transparent,
        fill = Color(0xFFFFE8CC).copy(alpha = 0.08f),
        labelPrimary = Color(0xFFEDE3D6),
        labelSecondary = Color(0xFFBFB2A3),
        separator = Color(0xFFFFE8CC).copy(alpha = 0.12f),
        accent = accent,
        onAccent = onColorFor(accent),
        red = Color(0xFFE5483E),
        green = Color(0xFF2DBF52),
        orange = Color(0xFFFF9F0A),
    )

    /** Sun-glare option: opaque panels, solid borders, secondary text promoted to primary. */
    fun highContrast(base: HuColors) = base.copy(
        glassFill = if (base.isDark) Color(0xFF1C1C1E) else Color.White,
        glassBorder = if (base.isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.6f),
        glassHighlight = Color.Transparent,
        fill = if (base.isDark) Color(0xFF3A3A3C) else Color(0xFFD1D1D6),
        labelSecondary = base.labelPrimary,
        separator = base.labelPrimary.copy(alpha = 0.5f),
    )

    /**
     * Apple "Liquid Glass": brighter, more transparent panels with a specular rim, over a
     * vivid wallpaper (see [HuBackground]) so the translucency has something to show.
     */
    fun glassDark(accent: Color) = dark(accent).copy(
        style = ThemeStyle.GLASS,
        background = Color(0xFF05060F),
        backgroundTint = accent.copy(alpha = 0.35f),
        glassFill = Color.White.copy(alpha = 0.10f),
        glassBorder = Color.White.copy(alpha = 0.22f),
        glassHighlight = Color.White.copy(alpha = 0.16f),
        fill = Color.White.copy(alpha = 0.16f),
        labelSecondary = Color(0xFFEBEBF5).copy(alpha = 0.78f),
    )

    fun glassLight(accent: Color) = light(accent).copy(
        style = ThemeStyle.GLASS,
        background = Color(0xFFE9ECF5),
        backgroundTint = accent.copy(alpha = 0.22f),
        glassFill = Color.White.copy(alpha = 0.52f),
        glassBorder = Color.White.copy(alpha = 0.85f),
        glassHighlight = Color.White.copy(alpha = 0.55f),
        fill = Color.White.copy(alpha = 0.55f),
    )

    /** Night Drive version of a styled palette: dimmer labels and glass; the wallpaper is drawn at lower intensity. */
    fun dimForNight(base: HuColors) = base.copy(
        night = true,
        labelPrimary = Color(0xFFE4E4EA),
        labelSecondary = base.labelSecondary.copy(alpha = base.labelSecondary.alpha * 0.85f),
        glassHighlight = base.glassHighlight.copy(alpha = base.glassHighlight.alpha * 0.5f),
        glassBorder = base.glassBorder.copy(alpha = base.glassBorder.alpha * 0.7f),
    )

    /** PS5 home screen: deep navy, cool-blue tinted cards, white type. Always dark. */
    fun ps5(accent: Color) = HuColors(
        isDark = true,
        style = ThemeStyle.PS5,
        background = Color(0xFF02040C),
        backgroundTint = accent.copy(alpha = 0.28f),
        glassFill = Color(0xFF16233F).copy(alpha = 0.62f),
        glassBorder = Color(0xFF9FB8E6).copy(alpha = 0.14f),
        glassHighlight = Color(0xFF6E8FD0).copy(alpha = 0.10f),
        fill = Color(0xFF2A3B60).copy(alpha = 0.70f),
        labelPrimary = Color(0xFFFFFFFF),
        labelSecondary = Color(0xFFC6D2EA),
        separator = Color(0xFF9FB8E6).copy(alpha = 0.18f),
        accent = accent,
        onAccent = onColorFor(accent),
        red = Color(0xFFFF4F5E),
        green = Color(0xFF35D07F),
        orange = Color(0xFFFFA733),
    )

    fun parse(hex: String?, fallback: Color): Color =
        runCatching { Color(hex!!.toColorInt()) }.getOrDefault(fallback)

    /** Black or white, whichever contrasts more with [c] (WCAG relative luminance). */
    fun onColorFor(c: Color): Color {
        val l = c.luminance()
        val withWhite = 1.05f / (l + 0.05f)
        val withBlack = (l + 0.05f) / 0.05f
        return if (withBlack > withWhite) Color.Black else Color.White
    }

    val AccentChoices = listOf(
        "#0A84FF" to "Blue",
        "#30D158" to "Green",
        "#FF9F0A" to "Orange",
        "#FF375F" to "Pink",
        "#BF5AF2" to "Purple",
        "#64D2FF" to "Teal",
    )

    /** PlayStation blue, used as the PS5 accent unless one was picked in Settings. */
    const val PS5_BLUE = "#0070D1"

}
