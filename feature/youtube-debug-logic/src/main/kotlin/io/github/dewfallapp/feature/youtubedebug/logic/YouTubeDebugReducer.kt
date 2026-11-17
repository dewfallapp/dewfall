package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The debug screen's reducer. It has no effects, so its effect type is Nothing.
 * - Run checks the section's input. A search sends the text as typed. A video or channel ID is
 *   trimmed and must parse as a VideoId or ChannelId, or the section shows InvalidInput and nothing
 *   is sent.
 * - Cancel stops only a running call.
 * - An answer counts only while its section is running the same request. An answer that arrives
 *   after a cancel, or for a request that a Run with other input replaced, is dropped. A Run with
 *   the same input replaces the call too, and the ViewModel cancels the old call before it starts
 *   the new one, so the old answer never arrives.
 */
object YouTubeDebugReducer : Reducer<YouTubeDebugState, YouTubeDebugIntent, Nothing> {
    override fun reduce(
        state: YouTubeDebugState,
        intent: YouTubeDebugIntent,
    ): Next<YouTubeDebugState, Nothing> =
        Next(
            when (intent) {
                is YouTubeDebugIntent.InputChanged -> state.withInput(intent.call, intent.text)
                is YouTubeDebugIntent.Run -> state.started(intent.call)
                is YouTubeDebugIntent.Cancel -> state.cancelled(intent.call)
                is YouTubeDebugIntent.SearchFinished -> {
                    state.copy(
                        search =
                            state.search.finished(intent.query, intent.result) { it.toSummary() }
                    )
                }
                is YouTubeDebugIntent.VideoDetailsFinished -> {
                    state.copy(
                        videoDetails =
                            state.videoDetails.finished(intent.videoId, intent.result) {
                                it.toSummary()
                            }
                    )
                }
                is YouTubeDebugIntent.LatestUploadsFinished -> {
                    state.copy(
                        latestUploads =
                            state.latestUploads.finished(intent.channelId, intent.result) {
                                it.toSummary(askedFor = intent.channelId)
                            }
                    )
                }
                is YouTubeDebugIntent.StreamLinksFinished -> {
                    state.copy(
                        streamLinks =
                            state.streamLinks.finished(intent.videoId, intent.result) {
                                it.toSummary()
                            }
                    )
                }
            }
        )
}

private fun YouTubeDebugState.withInput(call: DebugCall, text: String): YouTubeDebugState =
    when (call) {
        DebugCall.SEARCH -> copy(search = search.copy(input = text))
        DebugCall.VIDEO_DETAILS -> copy(videoDetails = videoDetails.copy(input = text))
        DebugCall.LATEST_UPLOADS -> copy(latestUploads = latestUploads.copy(input = text))
        DebugCall.STREAM_LINKS -> copy(streamLinks = streamLinks.copy(input = text))
    }

private fun YouTubeDebugState.started(call: DebugCall): YouTubeDebugState =
    when (call) {
        DebugCall.SEARCH -> copy(search = search.started { it })
        DebugCall.VIDEO_DETAILS -> copy(videoDetails = videoDetails.started(::parseVideoId))
        DebugCall.LATEST_UPLOADS -> copy(latestUploads = latestUploads.started(::parseChannelId))
        DebugCall.STREAM_LINKS -> copy(streamLinks = streamLinks.started(::parseVideoId))
    }

private fun YouTubeDebugState.cancelled(call: DebugCall): YouTubeDebugState =
    when (call) {
        DebugCall.SEARCH -> copy(search = search.cancelled())
        DebugCall.VIDEO_DETAILS -> copy(videoDetails = videoDetails.cancelled())
        DebugCall.LATEST_UPLOADS -> copy(latestUploads = latestUploads.cancelled())
        DebugCall.STREAM_LINKS -> copy(streamLinks = streamLinks.cancelled())
    }

private fun parseVideoId(input: String): VideoId? = VideoId.parseOrNull(input.trim())

private fun parseChannelId(input: String): ChannelId? = ChannelId.parseOrNull(input.trim())

/** Starts the call for the request that [parse] reads from the input, or shows InvalidInput. */
private fun <R, S> Section<R, S>.started(parse: (String) -> R?): Section<R, S> {
    val request = parse(input)
    return copy(
        status = if (request == null) CallStatus.InvalidInput else CallStatus.Running(request)
    )
}

private fun <R, S> Section<R, S>.cancelled(): Section<R, S> =
    if (status is CallStatus.Running) copy(status = CallStatus.Cancelled) else this

/**
 * Shows the answer to [request], if the section is still running that request. [summarize] turns a
 * successful answer into what the screen shows.
 */
private fun <R, T, S> Section<R, S>.finished(
    request: R,
    result: YouTubeResult<T>,
    summarize: (T) -> S,
): Section<R, S> {
    if (status != CallStatus.Running(request)) return this
    return copy(
        status =
            when (result) {
                is YouTubeResult.Success -> CallStatus.Succeeded(summarize(result.value))
                is YouTubeResult.Failure -> CallStatus.Failed(result.error)
            }
    )
}
