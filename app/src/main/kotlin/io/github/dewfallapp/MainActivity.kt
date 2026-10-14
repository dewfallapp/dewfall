package io.github.dewfallapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/** The app's only activity. It shows the Compose UI and is Hilt's entry point for it. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 and later draw apps edge to edge. This makes older versions do the same, with
        // transparent system bars whose icons follow the system's light or dark mode.
        enableEdgeToEdge()
        setContent { DewfallApp() }
    }
}
