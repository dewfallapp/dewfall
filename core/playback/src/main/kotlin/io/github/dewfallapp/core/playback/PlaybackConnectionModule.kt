package io.github.dewfallapp.core.playback

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers

/** Provides [PlaybackDispatchers] and the [PlaybackConnection]. */
@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackConnectionModule {
    @Binds fun bindConnection(connection: MediaControllerConnection): PlaybackConnection

    companion object {
        @Provides
        fun providePlaybackDispatchers(): PlaybackDispatchers =
            PlaybackDispatchers(main = Dispatchers.Main.immediate, decoders = Dispatchers.Default)
    }
}
