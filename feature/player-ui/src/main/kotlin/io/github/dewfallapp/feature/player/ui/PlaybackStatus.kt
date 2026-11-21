@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.feature.player.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import androidx.media3.common.util.UnstableApi

/**
 * What the video area and fullscreen read from the player beyond their buttons and times: whether
 * it is loading, whether the item is live, whether it has ended, and whether it is playing. It
 * updates on the player's events, never in screen state. The mini player and fullscreen live in
 * this module too, so it stays here, and :core:playback has no Compose.
 */
@Stable
internal class PlaybackStatus {
    /** The player waits for data. */
    var loading by mutableStateOf(false)
        private set

    /** The item is a live stream. */
    var live by mutableStateOf(false)
        private set

    /** The item played to its end. */
    var ended by mutableStateOf(false)
        private set

    /**
     * The player is playing: play is on, it has data, and nothing holds it back. It is false while
     * it waits for data, has ended or failed.
     */
    var playing by mutableStateOf(false)
        private set

    fun update(player: Player) {
        loading = player.playbackState == Player.STATE_BUFFERING
        live = player.isCurrentMediaItemLive
        ended = player.playbackState == Player.STATE_ENDED
        playing = player.isPlaying
    }
}

/** The [PlaybackStatus] of [player], which follows its events while it is shown. */
@Composable
internal fun rememberPlaybackStatus(player: Player): PlaybackStatus {
    val status = remember(player) { PlaybackStatus().apply { update(player) } }
    LaunchedEffect(player) { player.listen { status.update(this) } }
    return status
}
