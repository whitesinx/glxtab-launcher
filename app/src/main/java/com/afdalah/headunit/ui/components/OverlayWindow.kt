package com.afdalah.headunit.ui.components

import android.graphics.PixelFormat
import android.graphics.Rect
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

fun canDrawOverlays(context: android.content.Context) = Settings.canDrawOverlays(context)

/**
 * Draws [content] in a "display over other apps" window at [bounds] (screen px), above every app window,
 * including Google Maps' pop-up window in the map card. Not focusable, so BACK and HOME still reach the activity.
 * Hidden while the activity is stopped. Inherits this composition's theme (CompositionLocals).
 */
@Composable
fun OverlayWindow(bounds: Rect, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val host = LocalView.current
    val parent = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val wm = context.getSystemService(WindowManager::class.java)

    val params = WindowManager.LayoutParams(
        bounds.width(), bounds.height(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    val view = remember { ComposeView(context) }
    DisposableEffect(Unit) {
        view.setViewTreeLifecycleOwner(host.findViewTreeLifecycleOwner())
        view.setViewTreeSavedStateRegistryOwner(host.findViewTreeSavedStateRegistryOwner())
        view.setParentCompositionContext(parent)
        view.setContent { currentContent() }
        val added = runCatching { wm.addView(view, params) }.isSuccess
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> view.visibility = View.VISIBLE
                Lifecycle.Event.ON_STOP -> view.visibility = View.GONE
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (added) runCatching { wm.removeViewImmediate(view) }
        }
    }
    // Follow layout changes.
    LaunchedEffect(bounds) {
        if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
    }
}
