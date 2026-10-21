package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Reads a video's subtitles: first the list of tracks, then the lines of the track that the caller
 * picks.
 *
 * Main-safe: call these from any thread, including the main thread. The implementation moves
 * blocking work to an injected dispatcher. Any problem with YouTube or the network comes back as a
 * [YouTubeResult.Failure], never as an exception. Cancelling the calling coroutine cancels the
 * request, and the function then throws CancellationException, the only exception it throws.
 */
interface SubtitlesRemoteDataSource {
    /**
     * The subtitle tracks of the video with [videoId], in YouTube's order. Empty if it has none.
     */
    suspend fun subtitleTracks(videoId: VideoId): YouTubeResult<List<SubtitleTrack>>

    /**
     * The lines of [track], in the order they appear. Call it soon after [subtitleTracks], because
     * the track's link may stop working after some time.
     */
    suspend fun subtitleLines(track: SubtitleTrack): YouTubeResult<List<SubtitleLine>>
}
