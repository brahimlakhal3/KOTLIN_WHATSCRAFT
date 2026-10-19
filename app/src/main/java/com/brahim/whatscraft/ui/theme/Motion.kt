package com.brahim.whatscraft.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Every animation in the app comes from here, so the whole product moves with
 * one rhythm: quick, weighted, never bouncy enough to feel silly.
 */
object Motion {

    // Material 3 easing curves.
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val ScreenMillis = 340
    const val QuickMillis = 180
    const val SlowMillis = 520

    /** Screen-to-screen transition: a soft push, no hard cuts. */
    fun <T> screenEnter(): FiniteAnimationSpec<T> = tween(ScreenMillis, easing = EmphasizedDecelerate)

    fun <T> screenExit(): FiniteAnimationSpec<T> = tween(QuickMillis + 60, easing = EmphasizedAccelerate)

    /** Message bubbles settle with a little weight. */
    val Bubble: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val Scale: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )
}
