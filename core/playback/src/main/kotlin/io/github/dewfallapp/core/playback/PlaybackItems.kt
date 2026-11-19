package io.github.dewfallapp.core.playback

import io.github.dewfallapp.core.model.VideoId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The videos behind the playback IDs that the media session names, so the session holds no video
 * ID. An entry lasts only while its item is the player's: it is dropped when another item takes its
 * place, or when the items are cleared. See docs/architecture.md#the-player.
 */
@Singleton
class PlaybackItems @Inject internal constructor() {
    private val videos = mutableMapOf<String, VideoId>()
    private var current: String? = null

    /** Keeps [videoId] under a new playback ID, a random UUID's text, and returns the ID. */
    @Synchronized
    internal fun add(videoId: VideoId): String {
        val playbackId = UUID.randomUUID().toString()
        videos[playbackId] = videoId
        return playbackId
    }

    /** The video behind [playbackId], or null when there is none. */
    @Synchronized internal fun videoId(playbackId: String): VideoId? = videos[playbackId]

    /** Whether [playbackId] still has its video, which it doesn't after the session was gone. */
    @Synchronized fun contains(playbackId: String): Boolean = playbackId in videos

    /**
     * The player's current item is now [playbackId], or none. The previous current item's entry is
     * dropped when another item took its place, but not when the same item came back with new
     * links. Entries for items not set yet stay.
     */
    @Synchronized
    internal fun currentChanged(playbackId: String?) {
        val previous = current
        if (previous != null && previous != playbackId) videos.remove(previous)
        current = playbackId
    }

    /**
     * Drops [playbackId]'s entry, when its playback can't start or its screen stops it, whether or
     * not its item ever became the player's.
     */
    @Synchronized
    internal fun remove(playbackId: String) {
        videos.remove(playbackId)
    }

    /** Drops every entry, when the service stops. */
    @Synchronized
    internal fun clear() {
        videos.clear()
        current = null
    }
}
