package io.github.dewfallapp.core.data.youtube

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.dewfallapp.core.data.media.LiveMediaCalls
import io.github.dewfallapp.core.data.media.ProgressiveMediaCalls
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.NewPipeYouTube
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.SubtitlesRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import okhttp3.Call

/**
 * Provides the YouTube layer: one [NewPipeYouTube] for the whole app, each of its interfaces from
 * that one instance, and the clients of its media requests for the player. See
 * docs/architecture.md#the-youtube-layer.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface YouTubeModule {
    @Binds fun bindSearch(youTube: NewPipeYouTube): SearchRemoteDataSource

    @Binds fun bindVideoDetails(youTube: NewPipeYouTube): VideoDetailsRemoteDataSource

    @Binds fun bindChannelUploads(youTube: NewPipeYouTube): ChannelUploadsRemoteDataSource

    @Binds fun bindStreamLinks(youTube: NewPipeYouTube): StreamLinksRemoteDataSource

    @Binds fun bindSubtitles(youTube: NewPipeYouTube): SubtitlesRemoteDataSource

    companion object {
        @Provides
        @Singleton
        fun provideNewPipeYouTube(logger: Logger): NewPipeYouTube =
            NewPipeYouTube(logger, Dispatchers.IO)

        /**
         * A new client for one progressive format. The player's data source asks for one per media
         * source. Only `:core:data` sees the YouTube layer, so it hands out OkHttp's type.
         */
        @Provides
        @ProgressiveMediaCalls
        fun provideProgressiveMediaCalls(youTube: NewPipeYouTube): Call.Factory =
            youTube.mediaRequests.progressive()

        /** The client for live streams' HLS. */
        @Provides
        @Singleton
        @LiveMediaCalls
        fun provideLiveMediaCalls(youTube: NewPipeYouTube): Call.Factory =
            youTube.mediaRequests.live()
    }
}
