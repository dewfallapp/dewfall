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
 * Replaces the YouTube layer in every emulator test with fakes that know only [TestVideo]'s two
 * videos and their channel, plus the filler videos that [TestVideo.LONG_QUERY] finds but nothing
 * can open, and the player's media clients with [AssetMedia]. Nothing builds NewPipeYouTube, so no
 * request reaches YouTube or googlevideo.com. The repositories in `:core:data` are the real ones.
 * While [FakeNetwork] has YouTube off, the details, the stream links and the channel answer
 * NETWORK. See docs/testing.md#emulator-tests.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [YouTubeModule::class])
object FakeYouTubeModule {
    @Provides
    fun provideSearch(): SearchRemoteDataSource = SearchRemoteDataSource { query ->
        when (query) {
            TestVideo.QUERY ->
                YouTubeResult.Success(
                    SearchResults(
                        channels = listOf(TestVideo.searchChannel),
                        videos = listOf(TestVideo.summary, TestVideo.otherSummary),
                    ),
                )
            TestVideo.LONG_QUERY ->
                YouTubeResult.Success(
                    SearchResults(channels = emptyList(), videos = TestVideo.longResults),
                )
            else ->
                YouTubeResult.Success(SearchResults(channels = emptyList(), videos = emptyList()))
        }
    }

    @Provides
    fun provideVideoDetails(network: FakeNetwork): VideoDetailsRemoteDataSource =
        VideoDetailsRemoteDataSource { id ->
            when {
                !network.youTubeReachable -> unreachable
                id == TestVideo.id -> YouTubeResult.Success(TestVideo.details)
                id == TestVideo.otherId -> YouTubeResult.Success(TestVideo.otherDetails)
                else -> unavailable
            }
        }

    @Provides
    fun provideStreamLinks(network: FakeNetwork, media: AssetMedia): StreamLinksRemoteDataSource =
        StreamLinksRemoteDataSource { id ->
            when {
                !network.youTubeReachable -> unreachable
                id == TestVideo.id || id == TestVideo.otherId -> {
                    YouTubeResult.Success(
                        TestVideo.streamLinks(
                            videoId = id,
                            videoLength = media.length(TestVideo.VIDEO_URL),
                            audioLength = media.length(TestVideo.AUDIO_URL),
                        ),
                    )
                }
                else -> unavailable
            }
        }

    /** The test channel, with its two videos. Every other channel is unavailable. */
    @Provides
    fun provideChannelUploads(network: FakeNetwork): ChannelUploadsRemoteDataSource =
        ChannelUploadsRemoteDataSource { id ->
            when {
                !network.youTubeReachable -> unreachable
                id == TestVideo.channelId -> YouTubeResult.Success(TestVideo.uploads)
                else -> unavailable
            }
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

    /** One for the whole test, so the test can turn it off and on. */
    @Provides @Singleton fun provideFakeNetwork(): FakeNetwork = FakeNetwork()

    /** One for the whole test, so the test can read its request counts. */
    @Provides
    @Singleton
    fun provideAssetMedia(network: FakeNetwork): AssetMedia =
        AssetMedia(InstrumentationRegistry.getInstrumentation().context.assets, network)

    @Provides
    @ProgressiveMediaCalls
    fun provideProgressiveMediaCalls(media: AssetMedia): Call.Factory = media.calls

    @Provides
    @Singleton
    @LiveMediaCalls
    fun provideLiveMediaCalls(media: AssetMedia): Call.Factory = media.calls

    private val unavailable = YouTubeResult.Failure(YouTubeError.UNAVAILABLE)

    private val unreachable = YouTubeResult.Failure(YouTubeError.NETWORK)
}
