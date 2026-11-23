package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState
import kotlin.time.Duration

/**
 * One search that the user sent: the text, trimmed of spaces at both ends, and a number that grows
 * with each search. The number tells two searches with the same text apart. The text is user data,
 * so never log it.
 */
data class SearchRequest(val text: String, val number: Long)

/**
 * A video as a row of results shows it. It keeps the ID, so a row can open the video. The title and
 * channel name are user data, so never log them.
 */
data class VideoRow(
    val id: VideoId,
    val title: String,
    val channelName: String,
    /** The length, or null for a live stream, a video that hasn't started, or if unknown. */
    val duration: Duration?,
    val kind: VideoKind,
    /** When it was published, or is scheduled to start, or null if YouTube gave no time. */
    val published: PublishedTime?,
)

/**
 * A channel as a row of results shows it. It keeps the ID, so a row can open the channel page, and
 * the avatar links, so Subscribe can keep them. The name and the excerpt are user data, so never
 * log them.
 */
data class ChannelRow(
    val id: ChannelId,
    val name: String,
    /** The excerpt of the channel's description that YouTube gave with the result, or empty. */
    val excerpt: String,
    val avatars: List<Thumbnail>,
)

/** What the area under the search field shows. */
sealed interface ResultsArea {
    /** Nothing searched yet, or the field was cleared. */
    data object Idle : ResultsArea

    data class Loading(val request: SearchRequest) : ResultsArea

    /**
     * A search found channels or videos to show, each list in YouTube's order. One of them can be
     * empty, never both.
     */
    data class Results(
        val request: SearchRequest,
        val channels: List<ChannelRow>,
        val videos: List<VideoRow>,
    ) : ResultsArea

    /** A search found no channels and no videos to show. */
    data class NoResults(val request: SearchRequest) : ResultsArea

    data class Failed(val request: SearchRequest, val error: YouTubeError) : ResultsArea
}

/**
 * The Search YouTube screen's state. [lastRequestNumber] lives outside [results], so clearing
 * doesn't reset it, and every search gets a new number. [subscribedIds] lives outside it too, so a
 * new search keeps what the database said. The field's text isn't here: the field holds it, and
 * sends it with [YouTubeSearchIntent.Submit]. Channel IDs are user data, so never log them.
 */
data class YouTubeSearchState(
    val lastRequestNumber: Long = 0,
    val results: ResultsArea = ResultsArea.Idle,
    /** The IDs of the subscribed channels, as the database said, or null until it answers. */
    val subscribedIds: Set<ChannelId>? = null,
    /** The channel whose Unsubscribe dialog is open, or null while none is. */
    val confirmingUnsubscribe: ChannelId? = null,
) : MviState {
    /** The channel row whose Unsubscribe dialog shows, or null while none does. */
    val unsubscribeDialogRow: ChannelRow?
        get() {
            val id = confirmingUnsubscribe ?: return null
            return (results as? ResultsArea.Results)?.channels?.firstOrNull { it.id == id }
        }
}

/** What happens on the Search YouTube screen. */
sealed interface YouTubeSearchIntent : MviIntent {
    /** The user sent a search with [text], as the field holds it. */
    data class Submit(val text: String) : YouTubeSearchIntent

    /** The user cleared the field. */
    data object Clear : YouTubeSearchIntent

    /** The user tapped "Try again" after a failure. */
    data object TryAgain : YouTubeSearchIntent

    /** The database said that the user subscribes to the channels with [ids]. */
    data class SubscriptionsRead(val ids: Set<ChannelId>) : YouTubeSearchIntent

    /** The user tapped "Subscribe" in a channel row. The ViewModel keeps the subscription. */
    data class Subscribe(val channelId: ChannelId) : YouTubeSearchIntent

    /** The user tapped "Subscribed" in a channel row, which opens the Unsubscribe dialog. */
    data class UnsubscribeTapped(val channelId: ChannelId) : YouTubeSearchIntent

    /**
     * The user confirmed in the dialog for [channelId]. The ViewModel removes the subscription. It
     * carries the ID because the ViewModel runs after the reducer has closed the dialog.
     */
    data class UnsubscribeConfirmed(val channelId: ChannelId) : YouTubeSearchIntent

    /** The user closed the dialog without unsubscribing. */
    data object UnsubscribeCancelled : YouTubeSearchIntent

    /** The search for [request] answered with [result]. */
    data class SearchFinished(
        val request: SearchRequest,
        val result: YouTubeResult<SearchResults>,
    ) : YouTubeSearchIntent
}
