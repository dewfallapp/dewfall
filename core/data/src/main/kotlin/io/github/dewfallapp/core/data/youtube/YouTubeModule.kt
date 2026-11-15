package io.github.dewfallapp.core.data.youtube

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.NewPipeYouTube
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

/**
 * Provides the YouTube layer: one [NewPipeYouTube] for the whole app, and each of its interfaces
 * from that one instance. See docs/architecture.md#the-youtube-layer.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeModule {
    @Binds fun bindSearch(youTube: NewPipeYouTube): SearchRemoteDataSource

    @Binds fun bindVideoDetails(youTube: NewPipeYouTube): VideoDetailsRemoteDataSource

    @Binds fun bindChannelUploads(youTube: NewPipeYouTube): ChannelUploadsRemoteDataSource

    @Binds fun bindStreamLinks(youTube: NewPipeYouTube): StreamLinksRemoteDataSource

    companion object {
        @Provides
        @Singleton
        fun provideNewPipeYouTube(logger: Logger): NewPipeYouTube =
            NewPipeYouTube(logger, Dispatchers.IO)
    }
}
