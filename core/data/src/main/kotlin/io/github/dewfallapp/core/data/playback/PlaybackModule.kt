package io.github.dewfallapp.core.data.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the stream links repository that the player uses. */
@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackModule {
    @Binds fun bindStreamLinks(repository: LayerStreamLinksRepository): StreamLinksRepository
}
