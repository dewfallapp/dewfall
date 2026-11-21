package io.github.dewfallapp.feature.player.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Whether touch exploration is on, as for TalkBack, following its changes while composed. A screen
 * reader's swipes between controls send the app no pointer events, so the controls stay while it is
 * on.
 */
@Composable
internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * The window while fullscreen shows. On entering, it turns a phone to landscape, either way round
 * as its rotation setting allows, keeps hidden system bars to a swipe from the edge, and gives the
 * bars light icons over the black window, again after every configuration change, because activity
 * calls edge to edge again then. The bars show while [barsShown], with the controls. Leaving turns
 * the screen back to portrait, the orientation that the manifest gives MainActivity and the design
 * brief keeps phones in, and restores the bars and their icons. A configuration change restores
 * nothing, because the new activity composes this again. Android ignores the orientation on large
 * screens, where fullscreen keeps the window's shape.
 */
// Portrait is the orientation that the manifest gives MainActivity, and the design brief keeps
// phones in it, as the manifest's LockedOrientationActivity suppression says. Android ignores it on
// large screens.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
internal fun FullscreenWindow(barsShown: Boolean) {
    val activity = LocalActivity.current ?: return
    val configuration = LocalConfiguration.current
    val bars =
        remember(activity) {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        }
    DisposableEffect(activity) {
        val lightStatusBars = bars.isAppearanceLightStatusBars
        val lightNavigationBars = bars.isAppearanceLightNavigationBars
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        onDispose {
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                bars.isAppearanceLightStatusBars = lightStatusBars
                bars.isAppearanceLightNavigationBars = lightNavigationBars
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    LaunchedEffect(bars, configuration) {
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
    }
    LaunchedEffect(bars, barsShown) {
        if (barsShown) {
            bars.show(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
