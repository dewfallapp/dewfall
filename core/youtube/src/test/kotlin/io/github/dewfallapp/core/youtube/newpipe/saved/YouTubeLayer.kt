package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.SubtitlesRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import io.github.dewfallapp.core.youtube.newpipe.NewPipeCalls
import io.github.dewfallapp.core.youtube.newpipe.NewPipeChannelUploads
import io.github.dewfallapp.core.youtube.newpipe.NewPipeSearch
import io.github.dewfallapp.core.youtube.newpipe.NewPipeStreamLinks
import io.github.dewfallapp.core.youtube.newpipe.NewPipeSubtitles
import io.github.dewfallapp.core.youtube.newpipe.NewPipeVideoDetails

/**
 * The YouTube layer's five functions over [calls], built as NewPipeYouTube builds them.
 * NewPipeYouTube builds its own NewPipeCalls with the app's HTTP client, so the recorder and the
 * tests build this one over a client of their own.
 */
internal class YouTubeLayer(calls: NewPipeCalls, logger: Logger) :
    SearchRemoteDataSource by NewPipeSearch(calls, logger),
    VideoDetailsRemoteDataSource by NewPipeVideoDetails(calls, logger),
    ChannelUploadsRemoteDataSource by NewPipeChannelUploads(calls, logger),
    StreamLinksRemoteDataSource by NewPipeStreamLinks(calls, logger),
    SubtitlesRemoteDataSource by NewPipeSubtitles(calls, logger) {
    /**
     * The subtitle tracks of the video with [videoId], then the lines of the tracks that [pick]
     * picks from them, one request each, in the order it picks them. The first failure ends it.
     */
    suspend fun subtitlesOf(
        videoId: VideoId,
        pick: (List<SubtitleTrack>) -> List<SubtitleTrack>,
    ): YouTubeResult<VideoSubtitles> =
        when (val listed = subtitleTracks(videoId)) {
            is YouTubeResult.Failure -> listed
            is YouTubeResult.Success -> readLines(listed.value, pick(listed.value))
        }

    /** The lines of each track in [picked], some of [tracks], until the first failure. */
    private suspend fun readLines(
        tracks: List<SubtitleTrack>,
        picked: List<SubtitleTrack>,
    ): YouTubeResult<VideoSubtitles> {
        val read = mutableListOf<TrackLines>()
        for (track in picked) {
            when (val lines = subtitleLines(track)) {
                is YouTubeResult.Success -> read += TrackLines(track, lines.value)
                is YouTubeResult.Failure -> return lines
            }
        }
        return YouTubeResult.Success(VideoSubtitles(tracks, read))
    }
}

/** A video's subtitle tracks, and the lines of those that a saved set's call read. */
internal data class VideoSubtitles(val tracks: List<SubtitleTrack>, val read: List<TrackLines>)

/** The lines of one track. */
internal data class TrackLines(val track: SubtitleTrack, val lines: List<SubtitleLine>)
