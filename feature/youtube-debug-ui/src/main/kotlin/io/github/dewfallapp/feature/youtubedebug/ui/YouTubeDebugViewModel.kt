package io.github.dewfallapp.feature.youtubedebug.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.core.youtube.ChannelUploadsRemoteDataSource
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import io.github.dewfallapp.core.youtube.StreamLinksRemoteDataSource
import io.github.dewfallapp.core.youtube.VideoDetailsRemoteDataSource
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
) :
    MviViewModel<YouTubeDebugState, YouTubeDebugIntent, Nothing>(
        YouTubeDebugState(),
        YouTubeDebugReducer,
    ) {

    /** The job of each section's latest call. Only dispatch, on the main thread, touches it. */
    private val callJobs = EnumMap<DebugCall, Job>(DebugCall::class.java)

    override fun runSideEffects(intent: YouTubeDebugIntent) {
        when (intent) {
            is YouTubeDebugIntent.Run -> {
                callJobs.remove(intent.call)?.cancel()
                start(intent.call)?.let { callJobs[intent.call] = it }
            }
            is YouTubeDebugIntent.Cancel -> callJobs.remove(intent.call)?.cancel()
            is YouTubeDebugIntent.InputChanged,
            is YouTubeDebugIntent.SearchFinished,
            is YouTubeDebugIntent.VideoDetailsFinished,
            is YouTubeDebugIntent.LatestUploadsFinished,
            is YouTubeDebugIntent.StreamLinksFinished -> Unit
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
        }
    }
}
