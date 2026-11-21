package io.github.dewfallapp.core.playback

import androidx.media3.session.MediaSession
import io.github.dewfallapp.core.model.PlaybackFailure
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the player shows the picture: only while the app can be seen, [appVisible], and at least
 * one video screen holds the picture, [holds]. A minimized video has no holder.
 */
internal data class PictureState(val appVisible: Boolean = true, val holds: Int = 0) {
    val showsPicture: Boolean
        get() = appVisible && holds > 0
}

/**
 * What PlaybackService shares with PlaybackConnection inside the process: its media session, whose
 * token the connection's controller connects with, the current playback failure, which only the
 * service sees in full, and whether the app can be seen and a video screen holds the picture, which
 * decide whether the player shows it.
 */
@Singleton
internal class PlaybackSessions @Inject constructor() {
    private val session = MutableStateFlow<MediaSession?>(null)
    private val failureState = MutableStateFlow<PlaybackFailure?>(null)
    private val pictureState = MutableStateFlow(PictureState())

    /** The service's session while it runs, or null. */
    val current: StateFlow<MediaSession?> = session.asStateFlow()

    /** Why the current playback failed, or null while it hasn't. */
    val failure: StateFlow<PlaybackFailure?> = failureState.asStateFlow()

    /**
     * Whether one of the app's screens can be seen, which starts true, because the service starts
     * only while a screen shows, and how many video screens hold the picture.
     */
    val picture: StateFlow<PictureState> = pictureState.asStateFlow()

    fun publish(session: MediaSession?) {
        this.session.value = session
    }

    fun fail(failure: PlaybackFailure) {
        failureState.value = failure
    }

    fun clearFailure() {
        failureState.value = null
    }

    fun setAppVisible(visible: Boolean) {
        pictureState.update { it.copy(appVisible = visible) }
    }

    /** Holds the picture until the returned hold is released, once. */
    fun holdPicture(): PictureHold {
        pictureState.update { it.copy(holds = it.holds + 1) }
        val released = AtomicBoolean(false)
        return PictureHold {
            if (released.compareAndSet(false, true)) {
                pictureState.update { it.copy(holds = it.holds - 1) }
            }
        }
    }
}
