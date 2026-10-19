package com.brahim.whatscraft.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Keeps the status and navigation bar icons readable against the colours that
 * touch the edges of the screen.
 *
 * [top] is what sits under the status bar (usually the app background, or the
 * chat header while a conversation is open); [bottom] is what sits under the
 * navigation bar.
 */
@Composable
fun SystemBarIcons(top: Color, bottom: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    val darkOnTop = top.luminance() > 0.5f
    val darkOnBottom = bottom.luminance() > 0.5f

    DisposableEffect(window, view, darkOnTop, darkOnBottom) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousTop = controller.isAppearanceLightStatusBars
        val previousBottom = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = darkOnTop
        controller.isAppearanceLightNavigationBars = darkOnBottom
        onDispose {
            controller.isAppearanceLightStatusBars = previousTop
            controller.isAppearanceLightNavigationBars = previousBottom
        }
    }
}
