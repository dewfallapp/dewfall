package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The Search YouTube screen's reducer. A search starts only with text that isn't blank, and its
 * answer counts only while that same search is still running. Results leave out Shorts and keep
 * YouTube's order. A video that YouTube lists twice shows once, at its first place, because the
 * list keys its rows by video ID. The screen has no effects, so its effect type is Nothing.
 */
object YouTubeSearchReducer : Reducer<YouTubeSearchState, YouTubeSearchIntent, Nothing> {
    override fun reduce(
        state: YouTubeSearchState,
        intent: YouTubeSearchIntent,
    ): Next<YouTubeSearchState, Nothing> =
        Next(
            when (intent) {
                is YouTubeSearchIntent.Submit -> state.started(intent.text.trim())
                YouTubeSearchIntent.Clear -> state.copy(results = ResultsArea.Idle)
                YouTubeSearchIntent.TryAgain -> state.triedAgain()
                is YouTubeSearchIntent.SearchFinished -> state.finished(intent)
            }
        )
}

/** Starts a search for [text], unless it is empty. */
private fun YouTubeSearchState.started(text: String): YouTubeSearchState {
    if (text.isEmpty()) return this
    val request = SearchRequest(text, lastRequestNumber + 1)
    return copy(lastRequestNumber = request.number, results = ResultsArea.Loading(request))
}

/** Sends the failed search again, or changes nothing when no search failed. */
private fun YouTubeSearchState.triedAgain(): YouTubeSearchState {
    val failed = results as? ResultsArea.Failed ?: return this
    return started(failed.request.text)
}

/** Shows the answer in [intent], if it is for the search that is running. */
private fun YouTubeSearchState.finished(
    intent: YouTubeSearchIntent.SearchFinished
): YouTubeSearchState {
    if (results != ResultsArea.Loading(intent.request)) return this
    val area =
        when (val result = intent.result) {
            is YouTubeResult.Success -> {
                val rows =
                    result.value.videos
                        .filter { it.kind != VideoKind.SHORT }
                        .distinctBy { it.id }
                        .map(::toRow)
                if (rows.isEmpty()) {
                    ResultsArea.NoResults(intent.request)
                } else {
                    ResultsArea.Results(intent.request, rows)
                }
            }
            is YouTubeResult.Failure -> ResultsArea.Failed(intent.request, result.error)
        }
    return copy(results = area)
}

private fun toRow(video: VideoSummary): VideoRow =
    VideoRow(
        id = video.id,
        title = video.title,
        channelName = video.channelName,
        duration = video.duration,
        kind = video.kind,
        published = video.published,
    )
