package io.github.dewfallapp.feature.youtubedebug.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.model.SubtitleTrack
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.SubtitlesRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus
import io.github.dewfallapp.feature.youtubedebug.logic.DebugCall
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugReducer
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugState
import io.github.dewfallapp.feature.youtubedebug.logic.runningRequest
import java.util.EnumMap
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the debug screen's calls to the YouTube layer. Each section has at most one call running.
 * Run and Cancel stop the section's call first, and Run then starts the call that the reducer put
 * in the state, if the input was valid. Leaving the screen clears the ViewModel, which cancels
 * every call through viewModelScope.
 *
 * The subtitles section also reads one track's lines at a time. The ViewModel keeps the tracks of
 * the listing that the screen shows, links included, in memory only, never in the state, so the
 * screen never holds a link. It sets them in the same step as it dispatches the listing, so a track
 * that the user picks by its number is always the one on screen.
 *
 * It takes no dispatcher: the layer's functions are main-safe, and run their blocking work on the
 * dispatcher that the layer receives.
 */
@HiltViewModel
internal class YouTubeDebugViewModel
@Inject
constructor(
    private val searchSource: SearchRemoteDataSource,
    private val videoDetailsSource: VideoDetailsRemoteDataSource,
    private val channelUploadsSource: ChannelUploadsRemoteDataSource,
    private val streamLinksSource: StreamLinksRemoteDataSource,
    private val subtitlesSource: SubtitlesRemoteDataSource,
) :
    MviViewModel<YouTubeDebugState, YouTubeDebugIntent, Nothing>(
        YouTubeDebugState(),
        YouTubeDebugReducer,
    ) {

    /** The job of each section's latest call. Only dispatch, on the main thread, touches it. */
    private val callJobs = EnumMap<DebugCall, Job>(DebugCall::class.java)

    /** The job that reads a track's lines. Only the main thread touches it. */
    private var linesJob: Job? = null

    /** The tracks of the listing on screen, in its order. Only the main thread touches them. */
    private var listedTracks: List<SubtitleTrack> = emptyList()

    override fun runSideEffects(intent: YouTubeDebugIntent) {
        when (intent) {
            is YouTubeDebugIntent.Run -> {
                callJobs.remove(intent.call)?.cancel()
                // A new listing clears the lines, so their call stops too.
                if (intent.call == DebugCall.SUBTITLE_TRACKS) stopLines()
                start(intent.call)?.let { callJobs[intent.call] = it }
            }
            is YouTubeDebugIntent.Cancel -> callJobs.remove(intent.call)?.cancel()
            is YouTubeDebugIntent.ReadLines -> {
                stopLines()
                linesJob = startLines()
            }
            YouTubeDebugIntent.CancelLines -> stopLines()
            is YouTubeDebugIntent.InputChanged,
            is YouTubeDebugIntent.SearchFinished,
            is YouTubeDebugIntent.VideoDetailsFinished,
            is YouTubeDebugIntent.LatestUploadsFinished,
            is YouTubeDebugIntent.StreamLinksFinished,
            is YouTubeDebugIntent.SubtitleTracksFinished,
            is YouTubeDebugIntent.SubtitleLinesFinished -> Unit
        }
    }

    private fun stopLines() {
        linesJob?.cancel()
        linesJob = null
    }

    /** Starts reading the lines that the state shows as being read, or returns null if none are. */
    private fun startLines(): Job? {
        val pick = (state.value.subtitleLines as? CallStatus.Running)?.request
        val track = pick?.let { listedTracks.getOrNull(it.number - 1) }
        if (pick == null || track == null) return null
        return viewModelScope.launch {
            val result = subtitlesSource.subtitleLines(track)
            dispatch(YouTubeDebugIntent.SubtitleLinesFinished(pick, result))
        }
    }

    /** Starts the call that the state shows as running for [call], or returns null if none is. */
    private fun start(call: DebugCall): Job? {
        val current = state.value
        return when (call) {
            DebugCall.SEARCH -> {
                current.search.runningRequest?.let { query ->
                    viewModelScope.launch {
                        val result = searchSource.search(query)
                        dispatch(YouTubeDebugIntent.SearchFinished(query, result))
                    }
                }
            }
            DebugCall.VIDEO_DETAILS -> {
                current.videoDetails.runningRequest?.let { videoId ->
                    viewModelScope.launch {
                        val result = videoDetailsSource.videoDetails(videoId)
                        dispatch(YouTubeDebugIntent.VideoDetailsFinished(videoId, result))
                    }
                }
            }
            DebugCall.LATEST_UPLOADS -> {
                current.latestUploads.runningRequest?.let { channelId ->
                    viewModelScope.launch {
                        val result = channelUploadsSource.latestUploads(channelId)
                        dispatch(YouTubeDebugIntent.LatestUploadsFinished(channelId, result))
                    }
                }
            }
            DebugCall.STREAM_LINKS -> {
                current.streamLinks.runningRequest?.let { videoId ->
                    viewModelScope.launch {
                        val result = streamLinksSource.streamLinks(videoId)
                        dispatch(YouTubeDebugIntent.StreamLinksFinished(videoId, result))
                    }
                }
            }
            DebugCall.SUBTITLE_TRACKS -> {
                current.subtitleTracks.runningRequest?.let { videoId ->
                    viewModelScope.launch {
                        val result = subtitlesSource.subtitleTracks(videoId)
                        if (result is YouTubeResult.Success) listedTracks = result.value
                        dispatch(YouTubeDebugIntent.SubtitleTracksFinished(videoId, result))
                    }
                }
            }
        }
    }
}
