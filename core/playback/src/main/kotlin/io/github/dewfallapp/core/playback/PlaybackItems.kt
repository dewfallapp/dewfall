package io.github.dewfallapp.core.playback

import io.github.dewfallapp.core.model.VideoId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the media notification shows about a playback: the video's [title] and [channelName]. Both
 * are user data, so its text never shows them.
 */
internal class PlaybackDescription(val title: String, val channelName: String) {
    override fun equals(other: Any?): Boolean =
        other is PlaybackDescription && title == other.title && channelName == other.channelName

    override fun hashCode(): Int = 31 * title.hashCode() + channelName.hashCode()

    override fun toString(): String = "PlaybackDescription"
}

/**
 * The videos behind the playback IDs that the media session names, so the session holds no video
 * ID, and the title and channel name that the media notification shows. An entry lasts only while
 * its item is the player's: it is dropped when another item takes its place, or when the items are
 * cleared. See docs/architecture.md#the-player.
 */
@Singleton
class PlaybackItems @Inject internal constructor() {
    private class Entry(val videoId: VideoId, var description: PlaybackDescription? = null)

    private val entries = mutableMapOf<String, Entry>()
    private var current: String? = null
    private val descriptionChanges = MutableStateFlow(0)

    /** Counts the descriptions given so far, so the session's player knows to read them again. */
    internal val described: StateFlow<Int> = descriptionChanges.asStateFlow()

    /** Keeps [videoId] under a new playback ID, a random UUID's text, and returns the ID. */
    @Synchronized
    internal fun add(videoId: VideoId): String {
        val playbackId = UUID.randomUUID().toString()
        entries[playbackId] = Entry(videoId)
        return playbackId
    }

    /** The video behind [playbackId], or null when there is none. */
    @Synchronized internal fun videoId(playbackId: String): VideoId? = entries[playbackId]?.videoId

    /** Whether [playbackId] still has its video, which it doesn't after the session was gone. */
    @Synchronized fun contains(playbackId: String): Boolean = playbackId in entries

    /**
     * Keeps [description] for [playbackId], in place of any earlier one, if the ID still has its
     * video. It goes with the ID's entry.
     */
    internal fun describe(playbackId: String, description: PlaybackDescription) {
        synchronized(this) {
            val entry = entries[playbackId] ?: return
            entry.description = description
        }
        descriptionChanges.update { it + 1 }
    }

    /** What the notification shows for [playbackId], or null when there is nothing to show. */
    @Synchronized
    internal fun description(playbackId: String): PlaybackDescription? =
        entries[playbackId]?.description

    /**
     * The player's current item is now [playbackId], or none. The previous current item's entry is
     * dropped when another item took its place, but not when the same item came back with new
     * links. Entries for items not set yet stay.
     */
    @Synchronized
    internal fun currentChanged(playbackId: String?) {
        val previous = current
        if (previous != null && previous != playbackId) entries.remove(previous)
        current = playbackId
    }

    /**
     * Drops [playbackId]'s entry, when its playback can't start or its screen stops it, whether or
     * not its item ever became the player's.
     */
    @Synchronized
    internal fun remove(playbackId: String) {
        entries.remove(playbackId)
    }

    /** Drops every entry, when the service stops. */
    @Synchronized
    internal fun clear() {
        entries.clear()
        current = null
    }
}
