package io.github.dewfallapp.core.playback

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import kotlinx.coroutines.flow.StateFlow

/**
 * How screens reach the player in PlaybackService. Screens read the playback state through
 * [player], with media3-ui-compose's state holders or as Flows, never in screen state, and send
 * play, pause and seek straight to it. See docs/architecture.md#the-player.
 */
interface PlaybackConnection {
    /** The player while the connection to the service's session is up, or null. */
    val player: StateFlow<Player?>

    /** Why the current playback failed, or null while it hasn't. */
    val failure: StateFlow<PlaybackFailure?>

    /**
     * Plays [videoId] under a new playback ID, which it returns, from [startPositionMs], or from
     * the start or the live edge for C.TIME_UNSET, and starts playing if [play].
     */
    fun prepare(videoId: VideoId, play: Boolean, startPositionMs: Long = C.TIME_UNSET): String

    /** Whether [playbackId] still has its video, which it doesn't after the session was gone. */
    fun isKnown(playbackId: String): Boolean

    /**
     * Pauses [playbackId], if it is the player's item, or keeps it from playing if it still waits
     * for the connection, so leaving the screen never leaves it playing.
     */
    fun pause(playbackId: String)

    /**
     * Stops playback and clears the player's items, if the player's current item is still
     * [playbackId], as [stopsPlayback] decides. A video opened since keeps playing. The ID's video
     * is dropped either way.
     */
    fun stop(playbackId: String)

    /**
     * Gives [playbackId] the video's [title] and [channelName], which the media notification and
     * Android's media session show while that playback lasts. Nothing happens once the ID has no
     * video.
     */
    fun describe(playbackId: String, title: String, channelName: String)

    /**
     * Tells the player whether one of the app's screens can be seen. While none can, the player
     * shows no picture, so it decodes none, and fetches none that comes in its own file.
     */
    fun setAppVisible(visible: Boolean)
}

/**
 * Opens the app from the media notification. `:app` binds it, so this module doesn't name the app's
 * activity.
 */
fun interface SessionActivity {
    /** The immutable PendingIntent that brings the app's task to the front, as it was. */
    fun pendingIntent(context: Context): PendingIntent
}

/** What pausing a playback ID acts on. */
internal data class PauseDecision(
    /** The start that waits for the connection is that ID's, so it won't play. */
    val waitingStart: Boolean,
    /** The player's item is that ID's, so the player pauses. */
    val player: Boolean,
)

/**
 * What pausing [playbackId] acts on, with [waitingId] the ID of a start that still waits for the
 * connection, and [currentItemId] the player's item. It never pauses another screen's video.
 */
internal fun pauseDecision(
    waitingId: String?,
    currentItemId: String?,
    playbackId: String,
): PauseDecision =
    PauseDecision(waitingStart = waitingId == playbackId, player = currentItemId == playbackId)

/**
 * Whether stopping [playbackId] stops the player, whose current item is [currentItemId]: only when
 * it is still that item. A screen's ViewModel is cleared after its exit animation, so a video
 * opened meanwhile can already be playing.
 */
fun stopsPlayback(currentItemId: String?, playbackId: String): Boolean = currentItemId == playbackId
