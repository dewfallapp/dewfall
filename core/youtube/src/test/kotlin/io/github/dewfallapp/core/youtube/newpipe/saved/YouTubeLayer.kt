package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeChannelUploads
import io.github.dewfallapp.core.youtube.newpipe.NewPipeSearch
import io.github.dewfallapp.core.youtube.newpipe.NewPipeStreamLinks
import io.github.dewfallapp.core.youtube.newpipe.NewPipeVideoDetails

/**
 * The YouTube layer's four functions over [calls], built as NewPipeYouTube builds them.
 * NewPipeYouTube builds its own NewPipeCalls with the app's HTTP client, so the recorder and the
 * tests build this one over a client of their own.
 */
internal class YouTubeLayer(calls: NewPipeCalls, logger: Logger) :
    SearchRemoteDataSource by NewPipeSearch(calls, logger),
    VideoDetailsRemoteDataSource by NewPipeVideoDetails(calls, logger),
    ChannelUploadsRemoteDataSource by NewPipeChannelUploads(calls, logger),
    StreamLinksRemoteDataSource by NewPipeStreamLinks(calls, logger)
