package com.afdalah.headunit.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.afdalah.headunit.R
import com.afdalah.headunit.data.WelcomeConfig
import com.afdalah.headunit.ui.components.HuText
import com.afdalah.headunit.ui.theme.HuMotion
import com.afdalah.headunit.ui.theme.HuTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Car-start splash: logo springs in, greeting fades in, then the whole thing fades to the dashboard. Tap skips. */
@Composable
fun WelcomeSplash(config: WelcomeConfig, onDone: () -> Unit) {
    val reduceMotion = HuTheme.reduceMotion
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(config.durationMs.coerceIn(800, 6_000).toLong())
        alpha.animateTo(0f, HuMotion.fade(reduceMotion))
        onDone()
    }
    BrandScreen(config.logo, config.title, Modifier.graphicsLayer { this.alpha = alpha.value }, onTap = onDone)
}

/** Car-off splash: same logo, goodbye line; stays until [com.afdalah.headunit.mode.CarSession] locks the screen. */
@Composable
fun FarewellSplash(config: WelcomeConfig) {
    BrandScreen(config.logo, config.farewellTitle, Modifier, onTap = {})
}

/** Black screen with the Honda H mark over the red HONDA wordmark and one line of text. */
@Composable
private fun BrandScreen(logo: String, title: String, modifier: Modifier, onTap: () -> Unit) {
    val reduceMotion = HuTheme.reduceMotion
    val logoIn = remember { Animatable(0f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoIn.animateTo(1f, HuMotion.spring(reduceMotion)) }
        launch { delay(if (reduceMotion) 0 else 300); textIn.animateTo(1f, HuMotion.fade(reduceMotion)) }
    }
    // Always black, like a car's start-up screen, whatever the theme.
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (logo == "honda") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(36.dp),
                    modifier = Modifier.graphicsLayer {
                        this.alpha = logoIn.value
                        val s = 0.88f + 0.12f * logoIn.value
                        scaleX = s; scaleY = s
                    },
                ) {
                    Image(painterResource(R.drawable.ic_honda), null, Modifier.size(width = 260.dp, height = 184.dp))
                    Image(painterResource(R.drawable.ic_honda_wordmark), null, Modifier.size(width = 260.dp, height = 33.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            HuText(
                title,
                HuTheme.type.title1,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    this.alpha = textIn.value
                    translationY = (1f - textIn.value) * 20.dp.toPx()
                },
                maxLines = 1,
            )
        }
    }
}
