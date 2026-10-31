package com.afdalah.headunit.debug

import androidx.compose.runtime.Composable
import com.afdalah.headunit.HeadUnitApp

/** Release build: no overlay, no speed simulation, nothing to strip. */
object DebugHooks {
    const val ENABLED = false
    fun install(app: HeadUnitApp) = Unit

    @Composable
    fun Overlay(visible: Boolean) = Unit
}
