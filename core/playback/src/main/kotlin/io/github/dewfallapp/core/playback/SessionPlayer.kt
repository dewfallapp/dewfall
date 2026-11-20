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
 * session's error message, items and metadata into Android's media session, which other apps and
 * Bluetooth devices can read. So this shows the session each item as its playback ID only, with no
 * link, the current item's title and channel name from [items] and no other metadata, and an error
 * with the same code, [errorMessage] and no cause. It offers no skip commands. See
 * docs/architecture.md#the-player.
 */
internal class SessionPlayer(
    player: Player,
    private val items: PlaybackItems,
    private val errorMessage: String,
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val cleaned = cleanedError(state.playerError?.errorCode, errorMessage)
        val description = player.currentMediaItem?.mediaId?.let(items::description)
        return state
            .buildUpon()
            .setAvailableCommands(
                state.availableCommands.buildUpon().removeAll(*SKIP_COMMANDS.toIntArray()).build(),
            )
            .setPlayerError(cleaned?.let { PlaybackException(it.message, null, it.errorCode) })
            .setPlaylist(
                IdOnlyTimeline(state.timeline),
                state.currentTracks,
                description?.let(::metadataOf) ?: MediaMetadata.EMPTY,
            )
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .build()
    }

    /** Builds the state again, after a playback got its title and channel name. */
    fun descriptionsChanged() {
        invalidateState()
    }
}

/** The metadata that the session and the notification show: only the title and the channel. */
private fun metadataOf(description: PlaybackDescription): MediaMetadata =
    MediaMetadata.Builder().setTitle(description.title).setArtist(description.channelName).build()

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
