package io.github.dewfallapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.playback.PlaybackConnection
import javax.inject.Inject

/**
 * The app's only activity. It shows the Compose UI and is Hilt's entry point for it. It tells the
 * player whether the app can be seen, so the player shows no picture in the background, whichever
 * screen played the video. See docs/architecture.md#the-player.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject internal lateinit var playback: PlaybackConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 and later draw apps edge to edge. This makes older versions do the same, with
        // transparent system bars whose icons follow the system's light or dark mode.
        enableEdgeToEdge()
        setContent { DewfallApp() }
    }

    override fun onStart() {
        super.onStart()
        playback.setAppVisible(true)
    }

    override fun onStop() {
        super.onStop()
        // A configuration change brings the activity back at once, and keeps the picture.
        if (!isChangingConfigurations) playback.setAppVisible(false)
    }
}
