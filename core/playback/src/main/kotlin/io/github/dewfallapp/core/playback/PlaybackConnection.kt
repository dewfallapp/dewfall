package io.github.dewfallapp.core.playback

import android.app.PendingIntent
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import kotlinx.coroutines.flow.StateFlow

/**
 * How screens and the mini player reach the player in PlaybackService. They read the playback state
 * through [player], with media3-ui-compose's state holders or as Flows, never in screen state, and
 * send play, pause and seek straight to it. Playback belongs to the app, not to a screen: closing
 * the video screen leaves it to the mini player. See docs/architecture.md#the-player.
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

    /**
     * The video of [playbackId], while the ID has one, which it doesn't after the session was gone.
     * The mini player opens its screen with it.
     */
    fun videoOf(playbackId: String): VideoId?

    /**
     * Stops playback and clears the player's items, if the player's current item is still
     * [playbackId], as [stopsPlayback] decides. A video opened since keeps playing. The ID's video
     * is dropped either way. The mini player's close button calls it.
     */
    fun stop(playbackId: String)

    /**
     * The screen of [playbackId] closed, such as when the user minimized it. Its playback goes on
     * while it is the player's item or the start that waits for the connection, and the mini player
     * shows it. Otherwise the ID's video is dropped, as [releaseDrops] decides.
     */
    fun release(playbackId: String)

    /**
     * Gives [playbackId] the video's [title] and [channelName], which the media notification and
     * Android's media session show while that playback lasts. Nothing happens once the ID has no
     * video.
     */
    fun describe(playbackId: String, title: String, channelName: String)

    /**
     * Shows the picture while the app can be seen, until the returned hold is released. A video
     * screen holds it while it exists. While nothing holds it, as while a video is minimized, or
     * while the app can't be seen, the player decodes no picture, and fetches none that comes in
     * its own file.
     */
    fun holdPicture(): PictureHold

    /** One of the app's screens can be seen again. MainActivity calls it in onStart. */
    fun appStarted()

    /**
     * The app's screens can no longer be seen, unless the activity is only
     * [changingConfigurations]. MainActivity calls it in onStop. A playback that hasn't played yet
     * then waits, paused, as [leaveDecision] decides, because its foreground service would start
     * from the background.
     */
    fun appStopped(changingConfigurations: Boolean)
}

/** A hold on the picture, from [PlaybackConnection.holdPicture]. */
fun interface PictureHold {
    /** Lets go of the picture. A second call does nothing. */
    fun release()
}

/**
 * Opens the app from the media notification. `:app` binds it, so this module doesn't name the app's
 * activity.
 */
fun interface SessionActivity {
    /** The immutable PendingIntent that brings the app's task to the front, as it was. */
    fun pendingIntent(context: Context): PendingIntent
}

/** What the app becoming hidden does. */
internal data class LeaveDecision(
    /** The app counts as hidden, so the player shows no picture. */
    val hidesApp: Boolean,
    /** The start that waits for the connection won't play. */
    val stopsWaitingStart: Boolean,
    /** The player pauses its current item. */
    val pausesPlayer: Boolean,
)

/**
 * What the app's screens stopping does, unless the activity is only [changingConfigurations], with
 * [waitingId] the ID of a start that still waits for the connection, [currentItemId] the player's
 * item, and [playedId] the ID that last played. A configuration change does nothing. Otherwise a
 * video that has played keeps playing in the background, and one that hasn't, waiting or the
 * player's, waits paused, because its foreground service would start from the background, which
 * Android refuses.
 */
internal fun leaveDecision(
    changingConfigurations: Boolean,
    waitingId: String?,
    currentItemId: String?,
    playedId: String?,
): LeaveDecision =
    if (changingConfigurations) {
        LeaveDecision(hidesApp = false, stopsWaitingStart = false, pausesPlayer = false)
    } else {
        LeaveDecision(
            hidesApp = true,
            stopsWaitingStart = waitingId != null,
            pausesPlayer = currentItemId != null && currentItemId != playedId,
        )
    }

/**
 * Whether releasing [playbackId] drops its video: only when it is neither the player's
 * [currentItemId] nor the start that waits for the connection, [waitingId]. Otherwise it goes on in
 * the mini player.
 */
internal fun releaseDrops(waitingId: String?, currentItemId: String?, playbackId: String): Boolean =
    playbackId != waitingId && playbackId != currentItemId

/**
 * Whether stopping [playbackId] stops the player, whose current item is [currentItemId]: only when
 * it is still that item, so a video opened meanwhile keeps playing.
 */
fun stopsPlayback(currentItemId: String?, playbackId: String): Boolean = currentItemId == playbackId
