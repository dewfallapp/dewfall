package io.github.dewfallapp.fake

import androidx.test.platform.app.InstrumentationRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.dewfallapp.core.data.media.LiveMediaCalls
import io.github.dewfallapp.core.data.media.ProgressiveMediaCalls
import io.github.dewfallapp.core.data.youtube.YouTubeModule
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.SubtitlesRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import javax.inject.Singleton
import okhttp3.Call

/**
 * Replaces the YouTube layer in every emulator test with fakes that know only [TestVideo], and the
 * player's media clients with [AssetMedia]. Nothing builds NewPipeYouTube, so no request reaches
 * YouTube or googlevideo.com. The repositories in `:core:data` are the real ones. See
 * docs/testing.md#emulator-tests.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [YouTubeModule::class])
object FakeYouTubeModule {
    @Provides
    fun provideSearch(): SearchRemoteDataSource = SearchRemoteDataSource { query ->
        val videos = if (query == TestVideo.QUERY) listOf(TestVideo.summary) else emptyList()
        YouTubeResult.Success(SearchResults(channels = emptyList(), videos = videos))
    }

    @Provides
    fun provideVideoDetails(): VideoDetailsRemoteDataSource = VideoDetailsRemoteDataSource { id ->
        if (id == TestVideo.id) YouTubeResult.Success(TestVideo.details) else unavailable
    }

    @Provides
    fun provideStreamLinks(media: AssetMedia): StreamLinksRemoteDataSource =
        StreamLinksRemoteDataSource { id ->
            if (id == TestVideo.id) {
                YouTubeResult.Success(
                    TestVideo.streamLinks(
                        videoLength = media.length(TestVideo.VIDEO_URL),
                        audioLength = media.length(TestVideo.AUDIO_URL),
                    ),
                )
            } else {
                unavailable
            }
        }

    /** Search then play never opens a channel. */
    @Provides
    fun provideChannelUploads(): ChannelUploadsRemoteDataSource = ChannelUploadsRemoteDataSource {
        unavailable
    }

    /** Search then play never reads subtitles. */
    @Provides
    fun provideSubtitles(): SubtitlesRemoteDataSource =
        object : SubtitlesRemoteDataSource {
            override suspend fun subtitleTracks(
                videoId: VideoId,
            ): YouTubeResult<List<SubtitleTrack>> = unavailable

            override suspend fun subtitleLines(
                track: SubtitleTrack,
            ): YouTubeResult<List<SubtitleLine>> = unavailable
        }

    /** One for the whole test, so the test can read its request counts. */
    @Provides
    @Singleton
    fun provideAssetMedia(): AssetMedia =
        AssetMedia(InstrumentationRegistry.getInstrumentation().context.assets)

    @Provides
    @ProgressiveMediaCalls
    fun provideProgressiveMediaCalls(media: AssetMedia): Call.Factory = media.calls

    @Provides
    @Singleton
    @LiveMediaCalls
    fun provideLiveMediaCalls(media: AssetMedia): Call.Factory = media.calls

    private val unavailable = YouTubeResult.Failure(YouTubeError.UNAVAILABLE)
}
