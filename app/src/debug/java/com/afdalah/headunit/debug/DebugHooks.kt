package com.afdalah.headunit.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afdalah.headunit.HeadUnitApp
import com.afdalah.headunit.Log
import com.afdalah.headunit.app
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Debug build: overlay + speed simulation. The release source set has a no-op twin. */
object DebugHooks {
    const val ENABLED = true

    fun install(app: HeadUnitApp) {
        Log.d("Debug hooks installed (overlay: 5 taps on the mode pill; ./gradlew simulateSpeed -Pkmh=50)")
    }

    @Composable
    fun Overlay(visible: Boolean) {
        if (!visible) return
        val app = LocalContext.current.app
        val mode by app.modeManager.state.collectAsStateWithLifecycle()
        val driving by app.driving.state.collectAsStateWithLifecycle()
        val status by app.status.state.collectAsStateWithLifecycle()
        val night by app.autoMode.isNight.collectAsStateWithLifecycle()
        val fps = rememberFps()
        var mem by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                val rt = Runtime.getRuntime()
                val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                mem = "heap %d/%d MB · native %d MB · PSS %d MB".format(
                    (rt.totalMemory() - rt.freeMemory()) shr 20, rt.maxMemory() shr 20,
                    Debug.getNativeHeapAllocatedSize() shr 20, info.totalPss shr 10,
                )
                delay(2_000)
            }
        }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(mode.changedAtMillis))
        val lines = listOf(
            "mode      ${mode.mode} (last HU: ${mode.lastHeadUnitMode})",
            "source    ${mode.source} at $time",
            "speed     ${driving.speedKmh?.let { "%.1f km/h".format(it) } ?: "no fix"}${if (driving.simulated) " [SIMULATED]" else ""}",
            "moving    ${driving.isMoving}   night ${night}",
            "battery   ${status.battery.tempC}°C  ${status.battery.level}%  plugged=${status.battery.plugged}",
            "network   ${status.network.type} wifi=${status.network.wifiLevel} cell=${status.network.cellLevel}",
            "bt        ${status.bluetooth.connected.ifEmpty { setOf("—") }.joinToString()}",
            "fps       $fps",
            "memory    $mem",
        )
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            Column(
                Modifier.width(560.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                lines.forEach {
                    BasicText(it, style = TextStyle(color = Color(0xFF30D158), fontFamily = FontFamily.Monospace, fontSize = 16.sp))
                }
            }
        }
    }

    /** Frames actually rendered per second (FrameMetrics), not just vsync ticks. */
    @Composable
    private fun rememberFps(): Int {
        val view = LocalView.current
        var fps by remember { mutableIntStateOf(0) }
        DisposableEffect(view) {
            val window: Window? = (view.context as? Activity)?.window
            var frames = 0
            var windowStart = System.nanoTime()
            val listener = Window.OnFrameMetricsAvailableListener { _, _, _ ->
                frames++
                val now = System.nanoTime()
                if (now - windowStart >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    windowStart = now
                }
            }
            window?.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            onDispose { runCatching { window?.removeOnFrameMetricsAvailableListener(listener) } }
        }
        return fps
    }
}

/** adb shell am broadcast -a com.afdalah.headunit.debug.SIMULATE_SPEED -p com.afdalah.headunit --ef kmh 50 */
class SimulateSpeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kmh = intent.getFloatExtra("kmh", -1f)
        context.app.driving.simulatedSpeed.value = kmh.takeIf { it >= 0f }
        Log.i(if (kmh >= 0) "Simulated speed: $kmh km/h" else "Speed simulation off, using GPS")
    }
}

/** Stands in for the car's Bluetooth connecting/disconnecting (real ACL broadcasts are protected). */
class SimulateCarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connected = intent.getBooleanExtra("connected", true)
        Log.i("Simulated car Bluetooth ${if (connected) "connected" else "disconnected"}")
        if (connected) context.app.carSession.onCarConnected() else context.app.carSession.onCarDisconnected()
    }
}

/** adb shell am broadcast -a com.afdalah.headunit.debug.SET_THEME -p com.afdalah.headunit --es style glass|ps5|carplay */
class SetThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val style = intent.getStringExtra("style") ?: return
        context.app.modeManager.setThemeStyle(style)
        Log.i("Theme style: $style")
    }
}
