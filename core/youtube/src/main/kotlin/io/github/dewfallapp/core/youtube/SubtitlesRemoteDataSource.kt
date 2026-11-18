package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.SubtitleLine
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
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
     * Only the tracks that a person wrote or that YouTube generated from the video's own speech:
     * never machine translations, which include the auto-generated tracks that YouTube makes from a
     * video's AI-dubbed audio.
     */
    suspend fun subtitleTracks(videoId: VideoId): YouTubeResult<List<SubtitleTrack>>

    /**
     * The lines of [track], sorted by when they appear. Call it soon after [subtitleTracks],
     * because the track's link stops working after some time.
     *
     * Here a failure with [YouTubeError.UNAVAILABLE] means that the track's link no longer works,
     * such as after it expired. List the tracks again with [subtitleTracks], which also shows
     * whether the video is still there, then read the new track's lines.
     */
    suspend fun subtitleLines(track: SubtitleTrack): YouTubeResult<List<SubtitleLine>>
}
