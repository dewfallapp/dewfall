@file:OptIn(UnstableApi::class)

package io.github.dewfallapp.core.playback

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.model.PlaybackFailure
import io.github.dewfallapp.core.model.VideoId
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The PlaybackConnection over a MediaController. It starts PlaybackService, connects one
 * MediaController for the application's context to the service's session, and keeps it while the
 * session lives, so a configuration change finds it as it was. When the session is gone, the next
 * [prepare] starts the service and connects again.
 */
@Singleton
internal class MediaControllerConnection
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val sessions: PlaybackSessions,
    private val items: PlaybackItems,
    private val log: PlaybackLog,
    dispatchers: PlaybackDispatchers,
) : PlaybackConnection {
    init {
        // Before any other Media3 call, because Media3's lines can name a host or carry a link.
        Log.setLogLevel(Log.LOG_LEVEL_OFF)
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private val controller = MutableStateFlow<MediaController?>(null)
    private var connecting: Job? = null
    private var pending: Pending? = null

    override val player: StateFlow<Player?> = controller.asStateFlow()

    override val failure: StateFlow<PlaybackFailure?>
        get() = sessions.failure

    override fun prepare(videoId: VideoId, play: Boolean, startPositionMs: Long): String {
        val playbackId = items.add(videoId)
        sessions.clearFailure()
        val start = Pending(playbackId, startPositionMs, play)
        val connected = controller.value
        if (connected != null) {
            start.runOn(connected)
        } else {
            // A newer start replaces one that still waits, and the older one's ID goes.
            pending?.let { items.remove(it.playbackId) }
            pending = start
            connect()
        }
        return playbackId
    }

    override fun isKnown(playbackId: String): Boolean = items.contains(playbackId)

    override fun pause(playbackId: String) {
        val connected = controller.value
        val decision =
            pauseDecision(pending?.playbackId, connected?.currentMediaItem?.mediaId, playbackId)
        if (decision.waitingStart) pending?.play = false
        if (decision.player) connected?.pause()
    }

    override fun stop(playbackId: String) {
        if (pending?.playbackId == playbackId) pending = null
        // The ID goes, even when its item never became the player's, such as a start that waited
        // for the connection, so no video ID stays after its screen closes.
        items.remove(playbackId)
        val connected = controller.value ?: return
        if (stopsPlayback(connected.currentMediaItem?.mediaId, playbackId)) {
            connected.stop()
            connected.clearMediaItems()
        }
    }

    override fun describe(playbackId: String, title: String, channelName: String) {
        items.describe(playbackId, PlaybackDescription(title, channelName))
    }

    override fun setAppVisible(visible: Boolean) {
        sessions.setAppVisible(visible)
    }

    private fun connect() {
        if (connecting != null) return
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (error: IllegalStateException) {
            // Android refuses to start a service for an app in the background.
            failToConnect(error)
            return
        }
        connecting = scope.launch {
            val session = sessions.current.filterNotNull().first()
            val future =
                MediaController.Builder(context, session.token)
                    .setListener(Disconnection())
                    .buildAsync()
            future.addListener({ connected(future) }, context.mainExecutor)
        }
    }

    private fun connected(future: ListenableFuture<MediaController>) {
        connecting = null
        val connected =
            try {
                future.get()
            } catch (error: ExecutionException) {
                failToConnect(error)
                null
            } catch (error: CancellationException) {
                // Nothing cancels the future today. If something does, it counts as a failure.
                failToConnect(error)
                null
            }
        controller.value = connected
        if (connected != null) pending?.runOn(connected)
        pending = null
    }

    private fun failToConnect(error: Exception) {
        pending?.let { items.remove(it.playbackId) }
        pending = null
        log.failed(PlaybackFailure.OTHER, errorCode = null, error)
        sessions.fail(PlaybackFailure.OTHER)
    }

    /**
     * A start of [playbackId] from [startPositionMs], which waits for the connection when there is
     * none yet. A pause before it connects turns [play] off, so it never plays with its screen
     * gone.
     */
    private class Pending(val playbackId: String, val startPositionMs: Long, var play: Boolean) {
        fun runOn(player: Player) {
            player.setMediaItem(MediaItem.Builder().setMediaId(playbackId).build(), startPositionMs)
            player.prepare()
            player.playWhenReady = play
        }
    }

    private inner class Disconnection : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@MediaControllerConnection.controller.value === controller) {
                this@MediaControllerConnection.controller.value = null
            }
            controller.release()
        }
    }
}
