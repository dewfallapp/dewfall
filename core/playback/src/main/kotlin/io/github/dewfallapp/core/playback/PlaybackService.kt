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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** The notification channel of the media notification. Media3 names it "Now playing". */
private const val NOTIFICATION_CHANNEL_ID = "playback"

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

    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var sessionPlayer: SessionPlayer
    private val refreshLimit = RefreshLimit()
    private var currentPlaybackId: String? = null

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
        scope.launch { sessions.appVisible.collect(::showPicture) }
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
                        refresh(PlayerTarget(), resolution.item, point)
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
     * Turns the video track off while no screen of the app can be seen, so the player decodes no
     * picture, and fetches none that comes in its own file, and on again when one can. It builds on
     * the current parameters, so a live stream's variant override stays.
     */
    private fun showPicture(visible: Boolean) {
        val parameters = player.trackSelectionParameters
        val disabled = C.TRACK_TYPE_VIDEO in parameters.disabledTrackTypes
        if (disabled != visible) return
        player.trackSelectionParameters =
            parameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !visible).build()
    }

    private fun currentItemChanged() {
        val playbackId = player.currentMediaItem?.mediaId
        if (playbackId != currentPlaybackId) refreshLimit.reset()
        currentPlaybackId = playbackId
        items.currentChanged(playbackId)
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            currentItemChanged()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentItemChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) refreshLimit.reset()
            // A video that ended keeps play on. Media3 turns the notification's play button on
            // Android 10 to 12, and a headset's play and pause button on every version, into a
            // toggle, which would pause it, so the first press would do nothing. With play off, it
            // plays the video again from the start.
            if (playbackState == Player.STATE_ENDED) player.pause()
        }

        override fun onTracksChanged(tracks: Tracks) {
            limitLiveVariants(tracks)
        }

        /** A player that plays again, such as after play in the notification, has no failure. */
        override fun onPlayerErrorChanged(error: PlaybackException?) {
            if (error == null) sessions.clearFailure()
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
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val playbackId = mediaItems.singleOrNull()?.mediaId
            val metered = network.isMetered()
            val job = scope.launch {
                val resolution = resolveItem(items, resolver, playbackId, metered)
                result.set(itemsFor(resolution, startPositionMs))
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
