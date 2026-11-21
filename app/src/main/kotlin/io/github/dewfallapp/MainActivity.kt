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
 * player when the app can be seen and when it stops, so the player shows no picture in the
 * background and a video that hasn't played waits, whichever screen played it. See
 * docs/architecture.md#the-player.
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
        playback.appStarted()
    }

    override fun onStop() {
        super.onStop()
        // A configuration change brings the activity back at once, so the connection does nothing.
        playback.appStopped(changingConfigurations = isChangingConfigurations)
    }
}
