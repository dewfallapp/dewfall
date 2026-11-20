@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import android.content.Intent
import androidx.annotation.OptIn
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.liveVariantsWithin
import io.github.dewfallapp.core.model.pictureLimit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The media session service that plays videos: one ExoPlayer, which no screen owns, so later
 * changes can play in the background and in a mini player. It posts no notification and never runs
 * in the foreground until #43. PlaybackConnection starts it and connects to its session. See
 * docs/architecture.md#the-player.
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

    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
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
                .build()
        player.addListener(PlayerEvents())
        session =
            MediaSession.Builder(this, SessionPlayer(player)).setCallback(SessionCallback()).build()
        addSession(session)
        sessions.publish(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    /** Android never restarts the service after ending the process, because it would be empty. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    /** No notification until #43, so the service never runs in the foreground. */
    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ): ListenableFuture<Void?> = Futures.immediateVoidFuture()

    override fun onDestroy() {
        sessions.publish(null)
        session.release()
        player.release()
        sessions.clearFailure()
        items.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun report(failure: PlaybackFailure, errorCode: Int?) {
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
        }

        override fun onTracksChanged(tracks: Tracks) {
            limitLiveVariants(tracks)
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

    private inner class SessionCallback : MediaSession.Callback {
        /** Only the app's own controllers may connect. */
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> =
            Futures.immediateFuture(
                if (controller.packageName == packageName) {
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
                } else {
                    MediaSession.ConnectionResult.reject()
                }
            )

        /** Headset and Bluetooth buttons do nothing until #43. */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean = true

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
