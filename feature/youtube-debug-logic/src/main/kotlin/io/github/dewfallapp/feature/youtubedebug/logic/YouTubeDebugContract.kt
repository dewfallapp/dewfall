package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoDetails
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

// The YouTube layer's debug screen, which only debug builds contain. It calls each function of the
// layer and shows what came back. The screen has no effects: everything it shows has to survive a
// rotation, so it is all state. See docs/architecture.md#the-youtube-layers-debug-screen.

/** The functions of the YouTube layer that the screen calls, one section each. */
enum class DebugCall {
    SEARCH,
    VIDEO_DETAILS,
    LATEST_UPLOADS,
    STREAM_LINKS,
}

/**
 * The debug screen's state: one [Section] for each [DebugCall]. The inputs start with the calls
 * that the saved YouTube responses in :core:youtube's tests record.
 */
data class YouTubeDebugState(
    val search: Section<String, SearchSummary> = Section(DEFAULT_SEARCH_QUERY),
    val videoDetails: Section<VideoId, VideoDetailsSummary> = Section(DEFAULT_VIDEO_ID),
    val latestUploads: Section<ChannelId, UploadsSummary> = Section(DEFAULT_CHANNEL_ID),
    val streamLinks: Section<VideoId, StreamLinksSummary> = Section(DEFAULT_VIDEO_ID),
) : MviState

/**
 * One call's section: the text in its input field, and where its call stands.
 *
 * @param R what the call asks for: the search text, a VideoId or a ChannelId.
 * @param S what the screen shows when the call works.
 */
data class Section<out R, out S>(val input: String, val status: CallStatus<R, S> = CallStatus.Idle)

/** Where a section's call stands. */
sealed interface CallStatus<out R, out S> {
    /** Nothing has run yet. */
    data object Idle : CallStatus<Nothing, Nothing>

    /** The input isn't a video or channel ID, so nothing was sent. */
    data object InvalidInput : CallStatus<Nothing, Nothing>

    /** The call for [request] is running. */
    data class Running<out R>(val request: R) : CallStatus<R, Nothing>

    /** The call worked, and [summary] is what the screen shows of its answer. */
    data class Succeeded<out S>(val summary: S) : CallStatus<Nothing, S>

    /** The call failed with [error]. */
    data class Failed(val error: YouTubeError) : CallStatus<Nothing, Nothing>

    /** The user cancelled the call before it finished. */
    data object Cancelled : CallStatus<Nothing, Nothing>
}

/** What the request of a running call is, or null if the section's call isn't running. */
val <R> Section<R, *>.runningRequest: R?
    get() =
        when (val current = status) {
            is CallStatus.Running -> current.request
            else -> null
        }

/** What happens to the debug screen: what the user does, and the answers of the calls it ran. */
sealed interface YouTubeDebugIntent : MviIntent {
    /** The user changed the text in [call]'s input field. */
    data class InputChanged(val call: DebugCall, val text: String) : YouTubeDebugIntent

    /**
     * The user asks for [call] with the section's input. If the call is already running, it starts
     * again with the input as it is now.
     */
    data class Run(val call: DebugCall) : YouTubeDebugIntent

    /** The user stops [call]'s running call. */
    data class Cancel(val call: DebugCall) : YouTubeDebugIntent

    /** The search for [query] answered. */
    data class SearchFinished(val query: String, val result: YouTubeResult<SearchResults>) :
        YouTubeDebugIntent

    /** The video details of [videoId] arrived. */
    data class VideoDetailsFinished(val videoId: VideoId, val result: YouTubeResult<VideoDetails>) :
        YouTubeDebugIntent

    /** The latest uploads of [channelId] arrived. */
    data class LatestUploadsFinished(
        val channelId: ChannelId,
        val result: YouTubeResult<ChannelUploads>,
    ) : YouTubeDebugIntent

    /** The stream links of [videoId] arrived. */
    data class StreamLinksFinished(val videoId: VideoId, val result: YouTubeResult<StreamLinks>) :
        YouTubeDebugIntent
}

/** The search that the saved set SEARCH_NASA records. */
internal const val DEFAULT_SEARCH_QUERY = "nasa"

/**
 * Big Buck Bunny, the Blender Foundation's film, which the saved set VIDEO_BIG_BUCK_BUNNY records.
 */
internal const val DEFAULT_VIDEO_ID = "aqz-KE-bpKQ"

/** NASA's channel, which has a Live tab. The saved set UPLOADS_NASA records it. */
internal const val DEFAULT_CHANNEL_ID = "UCLA_DiR1FfKNvjuUpBHmylQ"
