package com.afdalah.headunit.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens.
 *
 * HIG/CarPlay: short, functional springs; nothing decorative while driving; honor Reduce Motion.
 * Spring: damping 0.85, stiffness 500 -> settles in ~0.24 s (ζω ≈ 19/s, 1% band ≈ 4.6/ζω).
 *
 * Two switches gate motion:
 *  - reduceMotion: system animator scale is 0 (One UI "Remove animations") -> everything snaps.
 *  - decorative:   false while the vehicle moves -> only feedback motion (press, banner) runs.
 */
object HuMotion {
    const val DAMPING = 0.85f
    const val STIFFNESS = 500f
    const val PRESS_SCALE = 0.95f
    const val FADE_MS = 180

    fun <T> spring(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = DAMPING, stiffness = STIFFNESS)

    fun <T> fade(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(FADE_MS)

    /** Decorative transitions (screen changes, card entrances): off while moving or reduce-motion. */
    fun <T> decorative(enabled: Boolean, reduceMotion: Boolean): AnimationSpec<T> =
        if (!enabled || reduceMotion) snap() else spring(dampingRatio = DAMPING, stiffness = STIFFNESS)
}
