package io.github.dewfallapp

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.navigation.DewfallNavigation

/**
 * The root of the app's UI. It draws everything inside Dewfall's theme. The app opens on the inbox,
 * and its navigation opens the other screens on top.
 */
@Composable
internal fun DewfallApp() {
    DewfallTheme { DewfallNavigation() }
}
