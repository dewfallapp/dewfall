@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ForwardingTimeline

/**
 * The player that the media session sees, around the service's ExoPlayer. Media3 copies the
 * session's error message and items into Android's media session, which other apps can read. So
 * this shows the session only the playback ID of each item, with no link and no metadata, and an
 * error with the same code, a constant message and no cause. See docs/architecture.md#the-player.
 */
internal class SessionPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val cleaned = cleanedError(state.playerError?.errorCode)
        return state
            .buildUpon()
            .setPlayerError(cleaned?.let { PlaybackException(it.message, null, it.errorCode) })
            .setPlaylist(IdOnlyTimeline(state.timeline), state.currentTracks, MediaMetadata.EMPTY)
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .build()
    }
}

/** [timeline] with each window's item reduced to its playback ID, and no manifest. */
private class IdOnlyTimeline(timeline: Timeline) : ForwardingTimeline(timeline) {
    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long,
    ): Window {
        super.getWindow(windowIndex, window, defaultPositionProjectionUs)
        window.mediaItem = MediaItem.Builder().setMediaId(window.mediaItem.mediaId).build()
        window.manifest = null
        return window
    }
}
