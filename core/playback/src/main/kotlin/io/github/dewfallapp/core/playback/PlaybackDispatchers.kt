package io.github.dewfallapp.core.playback

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatchers that playback runs on, injected so tests can replace them: [main] for the player
 * and the session, which live on the main thread, and [decoders] for the decoder queries, which can
 * take a while the first time.
 */
class PlaybackDispatchers(
    internal val main: CoroutineDispatcher,
    internal val decoders: CoroutineDispatcher,
)
