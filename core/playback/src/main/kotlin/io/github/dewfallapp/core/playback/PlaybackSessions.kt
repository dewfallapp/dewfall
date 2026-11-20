package io.github.dewfallapp.core.playback

import androidx.media3.session.MediaSession
import io.github.dewfallapp.core.model.PlaybackFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What PlaybackService shares with PlaybackConnection inside the process: its media session, whose
 * token the connection's controller connects with, the current playback failure, which only the
 * service sees in full, and whether the app can be seen, which decides whether the player shows the
 * picture.
 */
@Singleton
internal class PlaybackSessions @Inject constructor() {
    private val session = MutableStateFlow<MediaSession?>(null)
    private val failureState = MutableStateFlow<PlaybackFailure?>(null)
    private val visibleState = MutableStateFlow(true)

    /** The service's session while it runs, or null. */
    val current: StateFlow<MediaSession?> = session.asStateFlow()

    /** Why the current playback failed, or null while it hasn't. */
    val failure: StateFlow<PlaybackFailure?> = failureState.asStateFlow()

    /**
     * Whether one of the app's screens can be seen. It starts true, because the service starts only
     * while a screen shows.
     */
    val appVisible: StateFlow<Boolean> = visibleState.asStateFlow()

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
        visibleState.value = visible
    }
}
