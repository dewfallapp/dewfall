package io.github.dewfallapp.feature.youtubedebug.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.designsystem.theme.DewfallTheme

/**
 * Shows the YouTube layer's debug screen. Only debug builds contain it, with a launcher entry of
 * its own, so it needs no navigation inside the app. See
 * docs/architecture.md#the-youtube-layers-debug-screen.
 */
@AndroidEntryPoint
internal class YouTubeDebugActivity : ComponentActivity() {
    // Hilt's @AndroidEntryPoint makes viewModels() build @HiltViewModel classes.
    private val viewModel: YouTubeDebugViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 and later draw apps edge to edge. This makes older versions do the same.
        enableEdgeToEdge()
        setContent { DewfallTheme { YouTubeDebugRoute(viewModel) } }
    }
}
