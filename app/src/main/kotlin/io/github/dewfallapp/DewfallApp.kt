package io.github.dewfallapp

import androidx.compose.runtime.Composable
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme
import io.github.dewfallapp.feature.youtubesearch.ui.YouTubeSearch

/**
 * The root of the app's UI. It draws everything inside Dewfall's theme. The app opens on Search
 * YouTube, until Phase 5 makes the inbox the home screen.
 */
@Composable
internal fun DewfallApp() {
    DewfallTheme { YouTubeSearch() }
}
