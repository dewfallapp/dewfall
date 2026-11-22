@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.liveVariantsWithin
import io.github.dewfallapp.core.model.pictureLimit
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The notification channel of the media notification. Media3 names it "Now playing". */
private const val NOTIFICATION_CHANNEL_ID = "playback"

/**
 * How often a playing video saves its position, besides every pause and stop, so a process that
 * ends without warning loses at most this much. See
 * docs/architecture.md#saving-where-a-video-was-left.
 */
private val SAVE_INTERVAL = 30.seconds

/**
 * The media session service that plays videos: one ExoPlayer, which no screen owns, so it plays on
 * in the background, and #44's mini player can reuse it. Media3 posts its media notification and
 * runs the service in the foreground while a video plays, and for 10 minutes after it pauses.
 * PlaybackConnection starts it and connects to its session. See docs/architecture.md#the-player.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject internal lateinit var log: PlaybackLog
    @Inject internal lateinit var sessions: PlaybackSessions
    @Inject internal lateinit var items: PlaybackItems
    @Inject internal lateinit var resolver: PlaybackResolver
    @Inject internal lateinit var sources: PlaybackSources
    @Inject internal lateinit var network: NetworkCheck
    @Inject internal lateinit var dispatchers: PlaybackDispatchers
    @Inject internal lateinit var sessionActivity: SessionActivity
    @Inject internal lateinit var progress: WatchProgress

    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var sessionPlayer: SessionPlayer
    private val refreshLimit = RefreshLimit()
    private var currentPlaybackId: String? = null

    /** Saves the position every [SAVE_INTERVAL] while the video plays. */
    private var ticking: Job? = null

    override fun onCreate() {
        // Before any other Media3 call, because Media3's lines can name a host or carry a link.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        val audio =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
        player =
            ExoPlayer.Builder(this)
                .setMediaSourceFactory(sources)
                // No playback data for Android's media metrics, which Google may collect.
                .setUsePlatformDiagnostics(false)
                .setAudioAttributes(audio, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                // Locks the CPU and Wi-Fi awake while it plays, so streaming goes on with the
                // screen off.
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()
        player.addListener(PlayerEvents())
        sessionPlayer = SessionPlayer(player, items, getString(R.string.playback_session_error))
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .build(),
        )
        setListener(ServiceListener())
        session =
            MediaSession.Builder(this, sessionPlayer)
                // A new ID in each process, so a notification's button that an ended process left
                // behind names no session. See docs/architecture.md#the-player.
                .setId(UUID.randomUUID().toString())
                .setSessionActivity(sessionActivity.pendingIntent(this))
                .setCallback(SessionCallback())
                .build()
        addSession(session)
        sessions.publish(session)
        scope.launch {
            sessions.picture.map { it.showsPicture }.distinctUntilChanged().collect(::showPicture)
        }
        scope.launch { items.described.collect { sessionPlayer.descriptionsChanged() } }
    }

    /**
     * Returns no session to any caller. Dewfall's controllers connect with the session's own token
     * and never ask. A media button from a notification that an ended process left behind asks, and
     * then Media3 stops the service safely, and the leftover notification goes, so nothing plays
     * and nothing crashes.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val isMediaButton =
            controllerInfo.connectionHints.getString(
                MediaSessionService.CONNECTION_HINT_KEY_CONTROLLER_INFO_TYPE,
            ) == Intent.ACTION_MEDIA_BUTTON
        val hasItem = player.currentMediaItem != null
        if (cancelsLeftoverNotification(isMediaButton, hasItem)) {
            getSystemService(NotificationManager::class.java)
                ?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        return null
    }

    /** Android never restarts the service after ending the process, because it would be empty. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    /** Removing the app from the recent apps stops playback, even while it plays. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        progress.saveOn(ProgressTrigger.SERVICE_ENDING, player)
        progress.itemChanged(null, null, live = false)
        clearListener()
        sessions.publish(null)
        session.release()
        player.release()
        sessions.clearFailure()
        items.clear()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Reports a failure, and turns play off. Media3's notification on Android 10 to 12 shows a play
     * button for a failed player but sends the play and pause toggle, which pauses a player whose
     * play is still on, so without this its first tap would do nothing.
     */
    private fun report(failure: PlaybackFailure, errorCode: Int?) {
        player.pause()
        log.failed(failure, errorCode)
        sessions.fail(failure)
    }

    /** Fetches new links for the current item after YouTube refused one, and goes on with them. */
    private fun refreshLinks(errorCode: Int) {
        val item = player.currentMediaItem
        val tag = item?.localConfiguration?.tag as? PlaybackTag
        val playbackId = item?.mediaId
        val videoId = playbackId?.let(items::videoId)
        if (tag == null || playbackId == null || videoId == null) {
            report(PlaybackFailure.YOUTUBE, errorCode)
            return
        }
        val point = RefreshPoint(player.currentPosition, tag.live, player.playWhenReady)
        scope.launch {
            when (val resolution = resolver.resolve(playbackId, videoId, tag.metered)) {
                is Resolution.Resolved -> {
                    if (player.currentMediaItem?.mediaId == playbackId) {
                        val playsNow = resumesAfterRefresh(point.wasPlaying, player.playWhenReady)
                        refresh(PlayerTarget(), resolution.item, point.copy(wasPlaying = playsNow))
                    }
                }
                is Resolution.Failed -> report(resolution.failure, errorCode)
            }
        }
    }

    /**
     * Limits a live stream's video variants to those within the picture limit, among which
     * ExoPlayer adapts.
     */
    private fun limitLiveVariants(tracks: Tracks) {
        val tag = player.currentMediaItem?.localConfiguration?.tag as? PlaybackTag
        val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        if (tag?.live != true || group == null) return
        val sizes =
            (0 until group.length).map { index ->
                val format = group.getTrackFormat(index)
                if (format.width > 0 && format.height > 0) format.width to format.height else null
            }
        val indices = liveVariantsWithin(sizes, pictureLimit(tag.metered))
        val override = TrackSelectionOverride(group.mediaTrackGroup, indices)
        val parameters = player.trackSelectionParameters
        if (parameters.overrides[group.mediaTrackGroup] != override) {
            player.trackSelectionParameters =
                parameters.buildUpon().setOverrideForType(override).build()
        }
    }

    /**
     * Turns the video track off while no video screen can be seen, because the app is in the
     * background or the video is minimized, so the player decodes no picture, and fetches none that
     * comes in its own file, and on again when one can. It builds on the current parameters, so a
     * live stream's variant override stays.
     */
    private fun showPicture(visible: Boolean) {
        val parameters = player.trackSelectionParameters
        val disabled = C.TRACK_TYPE_VIDEO in parameters.disabledTrackTypes
        if (disabled != visible) return
        player.trackSelectionParameters =
            parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !visible).build()
    }

    private fun currentItemChanged() {
        val item = player.currentMediaItem
        val playbackId = item?.mediaId
        if (playbackId != currentPlaybackId) refreshLimit.reset()
        currentPlaybackId = playbackId
        val live = (item?.localConfiguration?.tag as? PlaybackTag)?.live == true
        progress.itemChanged(playbackId, playbackId?.let(items::videoId), live)
        items.currentChanged(playbackId)
    }

    /**
     * Saves the position every [SAVE_INTERVAL] while play is on, [playing], even while the player
     * waits for data, and stops when play turns off.
     */
    private fun tick(playing: Boolean) {
        ticking?.cancel()
        ticking =
            if (playing) {
                scope.launch {
                    while (true) {
                        delay(SAVE_INTERVAL)
                        progress.saveOn(ProgressTrigger.TICK, player)
                    }
                }
            } else {
                null
            }
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            currentItemChanged()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentItemChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    refreshLimit.reset()
                    progress.look(player.currentPosition, player.duration)
                }
                Player.STATE_ENDED -> progress.ended()
                Player.STATE_IDLE -> progress.saveOn(ProgressTrigger.IDLE, player)
                Player.STATE_BUFFERING -> Unit
            }
            // A video that ended keeps play on. Media3 turns the notification's play button on
            // Android 10 to 12, and a headset's play and pause button on every version, into a
            // toggle, which would pause it, so the first press would do nothing. With play off, it
            // plays the video again from the start.
            if (playbackState == Player.STATE_ENDED) player.pause()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) progress.saveOn(ProgressTrigger.PLAY_OFF, player)
            // The timer follows play, not isPlaying, so buffering never restarts its 30 seconds.
            tick(playWhenReady)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) progress.movedOn()
        }

        /** A seek moves on from the end, and saves while paused, or only looks while playing. */
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (
                reason != Player.DISCONTINUITY_REASON_SEEK &&
                    reason != Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                return
            }
            progress.movedOn()
            if (savesOn(ProgressTrigger.POSITION_JUMP, player.moment())) {
                progress.save(player.currentPosition, player.duration)
            } else {
                progress.look(player.currentPosition, player.duration)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            limitLiveVariants(tracks)
        }

        /**
         * A player that plays again, such as after play in the notification, has no failure. An
         * empty player keeps it, as [clearsFailure] says.
         */
        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (clearsFailure(error == null, player.currentMediaItem != null)) {
                sessions.clearFailure()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val status = httpStatusOf(error)
            val refused = refusedInChain(error)
            if (!refused && refreshLimit.shouldRefresh(status)) {
                refreshLinks(error.errorCode)
            } else {
                report(failureOf(error.errorCode, status, refused), error.errorCode)
            }
        }
    }

    private inner class PlayerTarget : RefreshTarget<MediaItem> {
        override fun replaceItem(item: MediaItem) {
            player.replaceMediaItem(player.currentMediaItemIndex, item)
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        override fun seekToLiveEdge() {
            player.seekToDefaultPosition()
        }

        override fun prepare() {
            player.prepare()
        }

        override fun play() {
            player.play()
        }
    }

    /** Pauses a player whose foreground start Android refused, so it never plays unseen. */
    private inner class ServiceListener : MediaSessionService.Listener {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onForegroundServiceStartNotAllowedException() {
            player.pause()
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        private val playbackCommands =
            Player.Commands.Builder().addAll(*PLAYBACK_COMMANDS.toIntArray()).build()

        /**
         * The screens' own controller keeps the default commands. Media3's media notification
         * controller, whose commands the system UI, the notification's and media buttons and
         * Android's media session use, and trusted controllers get only the playback commands.
         * Other apps are refused. See docs/architecture.md#the-player.
         */
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            val kind =
                controllerKind(
                    isMediaNotificationController =
                        session.isMediaNotificationController(controller),
                    isOwnPackage = controller.packageName == packageName,
                    isLegacy =
                        controller.controllerVersion ==
                            MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
                    isTrusted = controller.isTrusted,
                )
            val result =
                when (kind) {
                    ControllerKind.OWN -> {
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                            .build()
                    }
                    ControllerKind.MEDIA_NOTIFICATION,
                    ControllerKind.TRUSTED -> {
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                            .setAvailablePlayerCommands(playbackCommands)
                            .setAvailableSessionCommands(SessionCommands.EMPTY)
                            .build()
                    }
                    ControllerKind.REJECTED -> MediaSession.ConnectionResult.reject()
                }
            return Futures.immediateFuture(result)
        }

        /**
         * Resolves the item's playback ID into the item that plays it. A video that can't play
         * leaves the player empty, and its failure goes to PlaybackConnection.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // The item that plays now saves its place before another one takes it.
            progress.saveOn(ProgressTrigger.REPLACED, player)
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val playbackId = mediaItems.singleOrNull()?.mediaId
            val videoId = playbackId?.let(items::videoId)
            val metered = network.isMetered()
            val job = scope.launch {
                // The saved position is read beside the links, so it adds no wait.
                val saved =
                    if (startPositionMs == C.TIME_UNSET && videoId != null) {
                        async { progress.storedPosition(videoId) }
                    } else {
                        null
                    }
                val resolution = resolveItem(items, resolver, playbackId, metered)
                val tag = (resolution as? Resolution.Resolved)?.item?.localConfiguration?.tag
                val live = (tag as? PlaybackTag)?.live == true
                val start = startPositionFor(startPositionMs, saved?.await(), live)
                // The item that played on while the links loaded saves its place again, just
                // before the new one takes it.
                progress.saveOn(ProgressTrigger.REPLACED, player)
                result.set(itemsFor(resolution, start))
            }
            result.addListener(
                { if (result.isCancelled) job.cancel() },
                MoreExecutors.directExecutor(),
            )
            return result
        }

        private fun itemsFor(
            resolution: Resolution,
            startPositionMs: Long,
        ): MediaSession.MediaItemsWithStartPosition =
            when (resolution) {
                is Resolution.Resolved -> {
                    MediaSession.MediaItemsWithStartPosition(
                        listOf(resolution.item),
                        0,
                        startPositionMs,
                    )
                }
                is Resolution.Failed -> {
                    report(resolution.failure, errorCode = null)
                    MediaSession.MediaItemsWithStartPosition(
                        emptyList(),
                        C.INDEX_UNSET,
                        C.TIME_UNSET,
                    )
                }
            }
    }
}

/** The player as [savesOn] reads it. */
private fun Player.moment(): PlayerMoment =
    PlayerMoment(
        playbackState = playbackState,
        playWhenReady = playWhenReady,
        hasError = playerError != null,
        hasItem = currentMediaItem != null,
    )

/**
 * Saves the current item's position if [trigger] saves, as [savesOn] decides. It reads [player]
 * now, inside the callback of the command that changed it, so a stop followed by clearing the items
 * still saves the position the stop kept.
 */
private fun WatchProgress.saveOn(trigger: ProgressTrigger, player: Player) {
    if (savesOn(trigger, player.moment())) save(player.currentPosition, player.duration)
}
