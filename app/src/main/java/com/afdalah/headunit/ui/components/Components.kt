package com.afdalah.headunit.ui.components

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afdalah.headunit.R
import com.afdalah.headunit.app
import com.afdalah.headunit.ui.theme.HuDimens
import com.afdalah.headunit.ui.theme.HuMotion
import com.afdalah.headunit.ui.theme.HuShapes
import com.afdalah.headunit.ui.theme.HuTheme

/**
 * Every tap: haptic tick (eyes are on the road) + a short spring press-scale.
 * Long-press gets the heavier LONG_PRESS haptic.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.huClickable(
    label: String? = null,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) HuMotion.PRESS_SCALE else 1f,
        HuMotion.spring(HuTheme.reduceMotion),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = label,
            onLongClick = onLongClick?.let {
                {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    it()
                }
            },
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        )
}

/** Max 2 lines per item, ellipsized. */
@Composable
fun HuText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = HuTheme.colors.labelPrimary,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines.coerceAtMost(2),
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun HuIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = HuDimens.iconMedium,
    tint: Color = HuTheme.colors.labelPrimary,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

/** CarPlay card material: translucent fill, hairline border, faint top highlight. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = HuShapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = HuTheme.colors
    val border = if (HuTheme.highContrast) 2.dp else 1.dp
    Box(
        modifier
            .clip(shape)
            .background(c.glassFill)
            .background(Brush.verticalGradient(listOf(c.glassHighlight, Color.Transparent)))
            .border(border, c.glassBorder, shape),
        content = content,
    )
}

/** Large rounded tile: icon over label. Used for nav shortcuts, quick actions, menus. */
@Composable
fun TileButton(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = HuTheme.colors
    val bg = if (accent) c.accent else c.fill
    val fg = if (accent) c.onAccent else c.labelPrimary
    Column(
        modifier
            .defaultMinSize(HuDimens.minTarget, HuDimens.minTarget)
            .clip(HuShapes.button)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.4f))
            .huClickable(label = label, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HuIcon(icon, null, tint = fg)
        HuText(label, HuTheme.type.headline, color = fg, maxLines = 1, textAlign = TextAlign.Center)
        if (sublabel != null) {
            HuText(sublabel, HuTheme.type.caption, color = if (accent) fg else c.labelSecondary, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

/** Circular icon button (transport controls, call accept/decline, voice). */
@Composable
fun CircleButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = HuDimens.primaryButton,
    background: Color = HuTheme.colors.fill,
    tint: Color = HuTheme.colors.labelPrimary,
    iconSize: Dp = size * 0.42f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(maxOf(size, HuDimens.minTarget))
            .clip(HuShapes.pill)
            .background(if (enabled) background else background.copy(alpha = background.alpha * 0.4f))
            .huClickable(label = contentDescription, enabled = enabled, onLongClick = onLongClick, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        HuIcon(icon, null, size = iconSize, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
    }
}

/** HIG-style toggle row: whole row is the 76dp+ target. */
@Composable
fun HuSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = HuTheme.colors
    val track by animateColorAsState(if (checked) c.green else c.fill, HuMotion.fade(HuTheme.reduceMotion), label = "track")
    val knob by animateDpAsState(if (checked) 30.dp else 0.dp, HuMotion.spring(HuTheme.reduceMotion), label = "knob")
    Row(
        modifier
            .defaultMinSize(minHeight = HuDimens.minTarget)
            .huClickable(label = title, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            HuText(title, HuTheme.type.headline)
            if (subtitle != null) HuText(subtitle, HuTheme.type.caption, color = c.labelSecondary)
        }
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(width = 72.dp, height = 42.dp).clip(HuShapes.pill).background(track).padding(3.dp)) {
            Box(Modifier.offset(x = knob).size(36.dp).clip(HuShapes.pill).background(Color.White))
        }
    }
}

/** App icon in the uniform continuous-corner mask. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = HuDimens.dockIcon) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName, px) { bitmap = context.app.apps.icon(packageName, px) }
    Box(modifier.size(size).clip(HuShapes.appIcon).background(HuTheme.colors.fill)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Shown in place of anything that is locked while the vehicle is moving. */
@Composable
fun AvailableWhenParked(feature: String, modifier: Modifier = Modifier) {
    val c = HuTheme.colors
    Column(
        modifier.semantics(mergeDescendants = true) {}.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(104.dp).clip(HuShapes.pill).background(c.fill), contentAlignment = Alignment.Center) {
            HuIcon(R.drawable.ic_lock, null, size = HuDimens.iconLarge)
        }
        Spacer(Modifier.height(4.dp))
        HuText("Available when parked", HuTheme.type.title2, textAlign = TextAlign.Center)
        HuText(feature, HuTheme.type.body, color = c.labelSecondary, textAlign = TextAlign.Center)
    }
}
