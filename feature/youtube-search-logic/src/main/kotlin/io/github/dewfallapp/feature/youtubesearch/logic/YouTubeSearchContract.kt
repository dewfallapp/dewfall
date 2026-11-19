package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.SearchResults
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

/** What the area under the search field shows. */
sealed interface ResultsArea {
    /** Nothing searched yet, or the field was cleared. */
    data object Idle : ResultsArea

    data class Loading(val request: SearchRequest) : ResultsArea

    /** A search found videos to show, in YouTube's order. */
    data class Results(val request: SearchRequest, val rows: List<VideoRow>) : ResultsArea

    /** A search found no videos to show. */
    data class NoResults(val request: SearchRequest) : ResultsArea

    data class Failed(val request: SearchRequest, val error: YouTubeError) : ResultsArea
}

/**
 * The Search YouTube screen's state. [lastRequestNumber] lives outside [results], so clearing
 * doesn't reset it, and every search gets a new number. The field's text isn't here: the field
 * holds it, and sends it with [YouTubeSearchIntent.Submit].
 */
data class YouTubeSearchState(
    val lastRequestNumber: Long = 0,
    val results: ResultsArea = ResultsArea.Idle,
) : MviState

/** What happens on the Search YouTube screen. */
sealed interface YouTubeSearchIntent : MviIntent {
    /** The user sent a search with [text], as the field holds it. */
    data class Submit(val text: String) : YouTubeSearchIntent

    /** The user cleared the field. */
    data object Clear : YouTubeSearchIntent

    /** The user tapped "Try again" after a failure. */
    data object TryAgain : YouTubeSearchIntent

    /** The search for [request] answered with [result]. */
    data class SearchFinished(
        val request: SearchRequest,
        val result: YouTubeResult<SearchResults>,
    ) : YouTubeSearchIntent
}
