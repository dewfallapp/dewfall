package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The Search YouTube screen's reducer. A search starts only with text that isn't blank, and its
 * answer counts only while that same search is still running. Results keep YouTube's order, and
 * leave out Shorts. A channel or video that YouTube lists twice shows once, at its first place,
 * because the list keys its rows by ID.
 *
 * Subscribing and unsubscribing change no state here: the ViewModel writes to the database, and a
 * row's button changes when the database answers, so it shows what is kept, never a guess, as on
 * the channel page. The screen has no effects, so its effect type is Nothing.
 */
object YouTubeSearchReducer : Reducer<YouTubeSearchState, YouTubeSearchIntent, Nothing> {
    override fun reduce(
        state: YouTubeSearchState,
        intent: YouTubeSearchIntent,
    ): Next<YouTubeSearchState, Nothing> =
        Next(
            when (intent) {
                is YouTubeSearchIntent.Submit -> state.started(intent.text.trim())
                YouTubeSearchIntent.Clear -> {
                    state.copy(results = ResultsArea.Idle, confirmingUnsubscribe = null)
                }
                YouTubeSearchIntent.TryAgain -> state.triedAgain()
                is YouTubeSearchIntent.SearchFinished -> state.finished(intent)
                is YouTubeSearchIntent.SubscriptionsRead -> state.copy(subscribedIds = intent.ids)
                is YouTubeSearchIntent.Subscribe -> state
                is YouTubeSearchIntent.UnsubscribeTapped -> {
                    state.askedToUnsubscribe(intent.channelId)
                }
                is YouTubeSearchIntent.UnsubscribeConfirmed,
                YouTubeSearchIntent.UnsubscribeCancelled -> state.copy(confirmingUnsubscribe = null)
            },
        )
}

/** Starts a search for [text], unless it is empty. A new search closes the dialog. */
private fun YouTubeSearchState.started(text: String): YouTubeSearchState {
    if (text.isEmpty()) return this
    val request = SearchRequest(text, lastRequestNumber + 1)
    return copy(
        lastRequestNumber = request.number,
        results = ResultsArea.Loading(request),
        confirmingUnsubscribe = null,
    )
}

/** Sends the failed search again, or changes nothing when no search failed. */
private fun YouTubeSearchState.triedAgain(): YouTubeSearchState {
    val failed = results as? ResultsArea.Failed ?: return this
    return started(failed.request.text)
}

/** Shows the answer in [intent], if it is for the search that is running. */
private fun YouTubeSearchState.finished(
    intent: YouTubeSearchIntent.SearchFinished,
): YouTubeSearchState {
    if (results != ResultsArea.Loading(intent.request)) return this
    val area =
        when (val result = intent.result) {
            is YouTubeResult.Success -> {
                val channels = result.value.channels.distinctBy { it.id }.map(::toChannelRow)
                val videos =
                    result.value.videos
                        .filter { it.kind != VideoKind.SHORT }
                        .distinctBy { it.id }
                        .map(::toRow)
                if (channels.isEmpty() && videos.isEmpty()) {
                    ResultsArea.NoResults(intent.request)
                } else {
                    ResultsArea.Results(intent.request, channels, videos)
                }
            }
            is YouTubeResult.Failure -> ResultsArea.Failed(intent.request, result.error)
        }
    return copy(results = area)
}

/**
 * Opens the dialog for [channelId], only while the results hold that channel and the database says
 * the user subscribes to it, which is when its row says "Subscribed".
 */
private fun YouTubeSearchState.askedToUnsubscribe(channelId: ChannelId): YouTubeSearchState {
    val shown = (results as? ResultsArea.Results)?.channels.orEmpty().any { it.id == channelId }
    val subscribed = subscribedIds?.contains(channelId) == true
    return if (shown && subscribed) copy(confirmingUnsubscribe = channelId) else this
}

/** A run of spaces, tabs or line breaks, which an excerpt shows as one space. */
private val SPACES = Regex("\\s+")

private fun toChannelRow(channel: Channel): ChannelRow =
    ChannelRow(
        id = channel.id,
        name = channel.name,
        // The row shows at most two lines, so YouTube's runs of spaces and line breaks would only
        // waste them.
        excerpt = channel.description.replace(SPACES, " ").trim(),
        avatars = channel.avatars,
    )

private fun toRow(video: VideoSummary): VideoRow =
    VideoRow(
        id = video.id,
        title = video.title,
        channelName = video.channelName,
        duration = video.duration,
        kind = video.kind,
        published = video.published,
    )
