package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

private fun video(id: String, kind: VideoKind = VideoKind.VIDEO) =
    VideoSummary(
        id = VideoId(id),
        title = "Title of $id",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "A channel",
        duration = 12.minutes,
        published = null,
        thumbnails = emptyList(),
        kind = kind,
    )

private fun found(vararg videos: VideoSummary) =
    YouTubeResult.Success(SearchResults(channels = emptyList(), videos = videos.toList()))

class YouTubeSearchReducerTest {
    private fun reduce(state: YouTubeSearchState, intent: YouTubeSearchIntent): YouTubeSearchState {
        val next = YouTubeSearchReducer.reduce(state, intent)
        assertEquals(Next(next.state), next)
        return next.state
    }

    private fun YouTubeSearchState.after(vararg intents: YouTubeSearchIntent): YouTubeSearchState =
        intents.fold(this) { state, intent -> reduce(state, intent) }

    private val sourdough = SearchRequest("sourdough", number = 1)

    @Test
    fun `the screen starts with nothing searched`() {
        assertEquals(ResultsArea.Idle, YouTubeSearchState().results)
    }

    @Test
    fun `sending a search trims its text and starts loading`() {
        val state = YouTubeSearchState().after(YouTubeSearchIntent.Submit("  sourdough "))

        assertEquals(ResultsArea.Loading(sourdough), state.results)
        assertEquals(1, state.lastRequestNumber)
    }

    @Test
    fun `blank text changes nothing`() {
        for (text in listOf("", "   ", "\n")) {
            assertEquals(
                YouTubeSearchState(),
                YouTubeSearchState().after(YouTubeSearchIntent.Submit(text)),
            )
        }
        val loading = YouTubeSearchState().after(YouTubeSearchIntent.Submit("sourdough"))
        assertEquals(loading, loading.after(YouTubeSearchIntent.Submit(" ")))
    }

    @Test
    fun `results keep YouTube's order and leave out Shorts`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(
                        sourdough,
                        found(
                            video("aaaaaaaaaaa"),
                            video("bbbbbbbbbbb", VideoKind.SHORT),
                            video("ccccccccccc", VideoKind.LIVE),
                            video("ddddddddddd"),
                        ),
                    ),
                )

        val rows = (state.results as ResultsArea.Results).rows
        assertEquals(listOf("aaaaaaaaaaa", "ccccccccccc", "ddddddddddd"), rows.map { it.id.value })
        assertEquals(VideoKind.LIVE, rows[1].kind)
        assertEquals("Title of aaaaaaaaaaa", rows[0].title)
        assertEquals("A channel", rows[0].channelName)
        assertEquals(12.minutes, rows[0].duration)
    }

    @Test
    fun `a video that YouTube lists twice shows once, at its first place`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(
                        sourdough,
                        found(video("aaaaaaaaaaa"), video("bbbbbbbbbbb"), video("aaaaaaaaaaa")),
                    ),
                )

        val rows = (state.results as ResultsArea.Results).rows
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), rows.map { it.id.value })
    }

    @Test
    fun `no videos, or only Shorts, show no results`() {
        val searching = YouTubeSearchState().after(YouTubeSearchIntent.Submit("sourdough"))

        assertEquals(
            ResultsArea.NoResults(sourdough),
            searching.after(YouTubeSearchIntent.SearchFinished(sourdough, found())).results,
        )
        assertEquals(
            ResultsArea.NoResults(sourdough),
            searching
                .after(
                    YouTubeSearchIntent.SearchFinished(
                        sourdough,
                        found(video("bbbbbbbbbbb", VideoKind.SHORT)),
                    )
                )
                .results,
        )
    }

    @Test
    fun `a failure keeps its category`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(
                        sourdough,
                        YouTubeResult.Failure(YouTubeError.BLOCKED),
                    ),
                )

        assertEquals(ResultsArea.Failed(sourdough, YouTubeError.BLOCKED), state.results)
    }

    @Test
    fun `a new search replaces a running one, and the old answer is dropped`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("bread"),
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(
                        SearchRequest("bread", number = 1),
                        found(video("aaaaaaaaaaa")),
                    ),
                )

        assertEquals(ResultsArea.Loading(SearchRequest("sourdough", number = 2)), state.results)
    }

    @Test
    fun `the same text after clearing is a new search, and the first answer is dropped`() {
        val first = SearchRequest("bread", number = 1)
        val second = SearchRequest("bread", number = 2)
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("bread"),
                    YouTubeSearchIntent.Clear,
                    YouTubeSearchIntent.Submit("bread"),
                    YouTubeSearchIntent.SearchFinished(first, found(video("aaaaaaaaaaa"))),
                )
        assertEquals(ResultsArea.Loading(second), state.results)

        val answered =
            state.after(YouTubeSearchIntent.SearchFinished(second, found(video("bbbbbbbbbbb"))))
        assertEquals(
            listOf("bbbbbbbbbbb"),
            (answered.results as ResultsArea.Results).rows.map { it.id.value },
        )
    }

    @Test
    fun `clearing shows the screen as it was before the first search, and keeps the number`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(sourdough, found(video("aaaaaaaaaaa"))),
                    YouTubeSearchIntent.Clear,
                )

        assertEquals(YouTubeSearchState(lastRequestNumber = 1, results = ResultsArea.Idle), state)
    }

    @Test
    fun `an answer after clearing is dropped`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.Clear,
                    YouTubeSearchIntent.SearchFinished(sourdough, found(video("aaaaaaaaaaa"))),
                )

        assertEquals(ResultsArea.Idle, state.results)
    }

    @Test
    fun `trying again sends the failed search again as a new request`() {
        val state =
            YouTubeSearchState()
                .after(
                    YouTubeSearchIntent.Submit("sourdough"),
                    YouTubeSearchIntent.SearchFinished(
                        sourdough,
                        YouTubeResult.Failure(YouTubeError.NETWORK),
                    ),
                    YouTubeSearchIntent.TryAgain,
                )

        assertEquals(ResultsArea.Loading(SearchRequest("sourdough", number = 2)), state.results)
    }

    @Test
    fun `trying again with no failure changes nothing`() {
        val loading = YouTubeSearchState().after(YouTubeSearchIntent.Submit("sourdough"))
        val found =
            loading.after(
                YouTubeSearchIntent.SearchFinished(sourdough, found(video("aaaaaaaaaaa")))
            )

        for (state in listOf(YouTubeSearchState(), loading, found)) {
            assertEquals(state, state.after(YouTubeSearchIntent.TryAgain))
        }
    }
}
