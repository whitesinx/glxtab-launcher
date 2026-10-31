package com.afdalah.headunit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.afdalah.headunit.R

/** Inter (variable, OFL) as the open substitute for SF Pro. */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight) = Font(
    R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
)

/**
 * SF-style hierarchy scaled for arm's-length driving. HIG CarPlay mapping:
 *
 *  HIG style      iOS pt  -> here (sp)   use
 *  largeTitle     34      -> 48          speed, clock in standby
 *  title1         28      -> 40          now-playing title on full screen
 *  title2         22      -> 34          card primary value
 *  title3         20      -> 28          card titles, track title
 *  headline       17      -> 24          button labels, sender
 *  body           17      -> 22          secondary info
 *  callout        16      -> 20          metadata
 *  caption1       12      -> 18          smallest allowed text (status)
 *
 * Primary information is always 28–48sp. Tracking tightens as size grows, like SF's optical sizes.
 */
@Immutable
data class HuTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val caption: TextStyle,
)

private fun style(size: Int, weight: FontWeight, tracking: TextUnit) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking,
    lineHeight = (size * 1.2f).sp,
)

fun huTypography(boldText: Boolean = false): HuTypography {
    fun w(normal: FontWeight) = if (boldText) FontWeight(minOf(normal.weight + 100, 800)) else normal
    return HuTypography(
        largeTitle = style(48, w(FontWeight.Bold), (-0.02).em),
        title1 = style(40, w(FontWeight.Bold), (-0.02).em),
        title2 = style(34, w(FontWeight.SemiBold), (-0.015).em),
        title3 = style(28, w(FontWeight.SemiBold), (-0.01).em),
        headline = style(24, w(FontWeight.SemiBold), (-0.005).em),
        body = style(22, w(FontWeight.Normal), 0.em),
        callout = style(20, w(FontWeight.Medium), 0.em),
        caption = style(18, w(FontWeight.Medium), 0.005.em),
    )
}
