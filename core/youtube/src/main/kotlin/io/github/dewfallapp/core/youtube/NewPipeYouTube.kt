package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeChannelUploads
import io.github.dewfallapp.core.youtube.newpipe.NewPipeDownloader
import io.github.dewfallapp.core.youtube.newpipe.NewPipeSearch
import io.github.dewfallapp.core.youtube.newpipe.NewPipeStreamLinks
import io.github.dewfallapp.core.youtube.newpipe.NewPipeSubtitles
import io.github.dewfallapp.core.youtube.newpipe.NewPipeVideoDetails
import io.github.dewfallapp.core.youtube.newpipe.youTubeHttpClient
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The YouTube layer, built on NewPipeExtractor: search, video details, a channel's latest uploads,
 * stream links and subtitles. See docs/architecture.md#the-youtube-layer.
 *
 * Create one per process, such as a singleton in the app's dependency graph, because
 * NewPipeExtractor's setup is global. It sends its requests with its own HTTP client, only to
 * YouTube, logs through [logger], and runs its blocking work on [ioDispatcher].
 */
class NewPipeYouTube private constructor(calls: NewPipeCalls, logger: Logger) :
    SearchRemoteDataSource by NewPipeSearch(calls, logger),
    VideoDetailsRemoteDataSource by NewPipeVideoDetails(calls, logger),
    ChannelUploadsRemoteDataSource by NewPipeChannelUploads(calls, logger),
    StreamLinksRemoteDataSource by NewPipeStreamLinks(calls, logger),
    SubtitlesRemoteDataSource by NewPipeSubtitles(calls, logger) {
    constructor(
        logger: Logger,
        ioDispatcher: CoroutineDispatcher,
    ) : this(NewPipeCalls(NewPipeDownloader(youTubeHttpClient()), logger, ioDispatcher), logger)
}
