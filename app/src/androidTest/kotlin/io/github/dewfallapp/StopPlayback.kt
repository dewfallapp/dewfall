package io.github.dewfallapp

import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dewfallapp.core.playback.PlaybackConnection
import io.github.dewfallapp.core.playback.PlaybackService

/** How long a test waits for PlaybackService to end after it stops it. */
private const val STOP_TIMEOUT_MS = 10_000L

/**
 * Ends PlaybackService at the end of a test. The emulator tests share one process, and Hilt makes
 * new objects for each test, but a service that still runs keeps the objects of the test that
 * started it. The next test's PlaybackConnection would then wait for a session that its own objects
 * never get. So this stops the player and the service, and waits until [playback] has lost its
 * player, which happens when the service releases its session in onDestroy. See
 * docs/testing.md#emulator-tests.
 */
internal fun AndroidComposeTestRule<*, *>.stopPlayback(playback: PlaybackConnection) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val connected = runOnUiThread {
        val player = playback.player.value
        player?.stop()
        player?.clearMediaItems()
        context.stopService(Intent(context, PlaybackService::class.java))
        player != null
    }
    if (connected) waitUntil(STOP_TIMEOUT_MS) { runOnUiThread { playback.player.value == null } }
}
