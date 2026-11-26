package io.github.dewfallapp.feature.youtubesearch.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

private fun video(id: String, kind: VideoKind = VideoKind.VIDEO) =
    VideoSummary(
        id = VideoId(id),
        title = "Title of $id",
        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
        channelName = "A channel",
        duration = 12.minutes,
        published = null,
        thumbnails =
            listOf(
                Thumbnail("https://images.example.invalid/$id-small.jpg", width = 168, height = 94),
                Thumbnail(
                    "https://images.example.invalid/$id-large.jpg",
                    width = 336,
                    height = 188,
                ),
            ),
        kind = kind,
    )

private fun channel(id: String, name: String = "Channel $id", description: String = "") =
    Channel(
        id = ChannelId(id),
        name = name,
        avatars = listOf(Thumbnail("https://example.com/$id.jpg", width = 88, height = 88)),
        description = description,
    )

private fun found(vararg videos: VideoSummary, channels: List<Channel> = emptyList()) =
    YouTubeResult.Success(SearchResults(channels = channels, videos = videos.toList()))

private const val KETTLE = "UCaaaaaaaaaaaaaaaaaaaaaa"
private const val JAR = "UCbbbbbbbbbbbbbbbbbbbbbb"

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

        val rows = (state.results as ResultsArea.Results).videos
        assertEquals(listOf("aaaaaaaaaaa", "ccccccccccc", "ddddddddddd"), rows.map { it.id.value })
        assertEquals(VideoKind.LIVE, rows[1].kind)
        assertEquals("Title of aaaaaaaaaaa", rows[0].title)
        assertEquals("A channel", rows[0].channelName)
        assertEquals(12.minutes, rows[0].duration)
        // Every size, so the row picks the one that fits.
        assertEquals(video("aaaaaaaaaaa").thumbnails, rows[0].thumbnails)
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

        val rows = (state.results as ResultsArea.Results).videos
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), rows.map { it.id.value })
    }

    @Test
    fun `no channels and no videos, or only Shorts, show no results`() {
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
                    ),
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
            (answered.results as ResultsArea.Results).videos.map { it.id.value },
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
                YouTubeSearchIntent.SearchFinished(sourdough, found(video("aaaaaaaaaaa"))),
            )

        for (state in listOf(YouTubeSearchState(), loading, found)) {
            assertEquals(state, state.after(YouTubeSearchIntent.TryAgain))
        }
    }

    private fun withResults(channels: List<Channel>, subscribed: Set<String>? = setOf(KETTLE)) =
        YouTubeSearchState()
            .after(
                YouTubeSearchIntent.Submit("sourdough"),
                YouTubeSearchIntent.SearchFinished(
                    sourdough,
                    found(video("aaaaaaaaaaa"), channels = channels),
                ),
            )
            .let { state ->
                if (subscribed == null) {
                    state
                } else {
                    state.after(
                        YouTubeSearchIntent.SubscriptionsRead(subscribed.map(::ChannelId).toSet()),
                    )
                }
            }

    @Test
    fun `channels keep YouTube's order, show once, and keep their name, excerpt and avatars`() {
        val kettle = channel(KETTLE, "Kettle and Crumb", "Bread, fermentation and old recipes")
        val state = withResults(listOf(kettle, channel(JAR), kettle))

        val channels = (state.results as ResultsArea.Results).channels
        assertEquals(listOf(KETTLE, JAR), channels.map { it.id.value })
        assertEquals("Kettle and Crumb", channels[0].name)
        assertEquals("Bread, fermentation and old recipes", channels[0].excerpt)
        assertEquals(kettle.avatars, channels[0].avatars)
    }

    @Test
    fun `an excerpt shows runs of spaces and line breaks as one space`() {
        val state =
            withResults(
                listOf(channel(KETTLE, description = "  Your friendly\n   flight center.  ")),
            )

        val channels = (state.results as ResultsArea.Results).channels
        assertEquals("Your friendly flight center.", channels[0].excerpt)
    }

    @Test
    fun `only channels, or channels with only Shorts, still show results`() {
        val searching = YouTubeSearchState().after(YouTubeSearchIntent.Submit("sourdough"))

        val onlyChannels =
            searching.after(
                YouTubeSearchIntent.SearchFinished(
                    sourdough,
                    found(channels = listOf(channel(JAR))),
                ),
            )
        assertEquals(
            ResultsArea.Results(
                sourdough,
                channels =
                    listOf(ChannelRow(ChannelId(JAR), "Channel $JAR", "", channel(JAR).avatars)),
                videos = emptyList(),
            ),
            onlyChannels.results,
        )

        val withShorts =
            searching.after(
                YouTubeSearchIntent.SearchFinished(
                    sourdough,
                    found(video("bbbbbbbbbbb", VideoKind.SHORT), channels = listOf(channel(JAR))),
                ),
            )
        val results = withShorts.results as ResultsArea.Results
        assertEquals(listOf(JAR), results.channels.map { it.id.value })
        assertEquals(emptyList(), results.videos)
    }

    @Test
    fun `the subscribed channels are unknown until the database answers, then follow it`() {
        assertNull(YouTubeSearchState().subscribedIds)

        val read =
            YouTubeSearchState()
                .after(YouTubeSearchIntent.SubscriptionsRead(setOf(ChannelId(KETTLE))))
        assertEquals(setOf(ChannelId(KETTLE)), read.subscribedIds)
        assertEquals(
            emptySet(),
            read.after(YouTubeSearchIntent.SubscriptionsRead(emptySet())).subscribedIds,
        )
    }

    @Test
    fun `a new search and clearing keep what the database said`() {
        val state = withResults(listOf(channel(KETTLE)))

        assertEquals(setOf(ChannelId(KETTLE)), state.after(YouTubeSearchIntent.Clear).subscribedIds)
        assertEquals(
            setOf(ChannelId(KETTLE)),
            state.after(YouTubeSearchIntent.Submit("rye")).subscribedIds,
        )
    }

    @Test
    fun `subscribing changes no state`() {
        val state = withResults(listOf(channel(KETTLE), channel(JAR)))

        assertEquals(state, state.after(YouTubeSearchIntent.Subscribe(ChannelId(JAR))))
    }

    @Test
    fun `tapping Subscribed opens the dialog for that channel`() {
        val state = withResults(listOf(channel(KETTLE, "Kettle and Crumb"), channel(JAR)))

        val asking = state.after(YouTubeSearchIntent.UnsubscribeTapped(ChannelId(KETTLE)))
        assertEquals(ChannelId(KETTLE), asking.confirmingUnsubscribe)
        assertEquals("Kettle and Crumb", asking.unsubscribeDialogRow?.name)
    }

    @Test
    fun `the dialog opens only for a subscribed channel in the results`() {
        val state = withResults(listOf(channel(KETTLE), channel(JAR)))

        // Not subscribed.
        assertEquals(state, state.after(YouTubeSearchIntent.UnsubscribeTapped(ChannelId(JAR))))
        // Subscribed, but not in the results.
        val elsewhere =
            state.after(
                YouTubeSearchIntent.SubscriptionsRead(
                    setOf(ChannelId(KETTLE), ChannelId("UCcccccccccccccccccccccc")),
                ),
            )
        assertEquals(
            elsewhere,
            elsewhere.after(
                YouTubeSearchIntent.UnsubscribeTapped(ChannelId("UCcccccccccccccccccccccc")),
            ),
        )
        // Before the database answers.
        val unknown = withResults(listOf(channel(KETTLE)), subscribed = null)
        assertEquals(
            unknown,
            unknown.after(YouTubeSearchIntent.UnsubscribeTapped(ChannelId(KETTLE))),
        )
    }

    @Test
    fun `confirming, cancelling, a new search and clearing close the dialog`() {
        val asking =
            withResults(listOf(channel(KETTLE)))
                .after(YouTubeSearchIntent.UnsubscribeTapped(ChannelId(KETTLE)))

        val closing =
            listOf(
                YouTubeSearchIntent.UnsubscribeConfirmed(ChannelId(KETTLE)),
                YouTubeSearchIntent.UnsubscribeCancelled,
                YouTubeSearchIntent.Submit("rye"),
                YouTubeSearchIntent.Clear,
            )
        for (intent in closing) {
            val closed = asking.after(intent)
            assertNull(closed.confirmingUnsubscribe, intent.toString())
            assertNull(closed.unsubscribeDialogRow, intent.toString())
        }
        // Confirming changes nothing else: the button changes when the database answers.
        assertEquals(
            asking.copy(confirmingUnsubscribe = null),
            asking.after(YouTubeSearchIntent.UnsubscribeConfirmed(ChannelId(KETTLE))),
        )
    }
}
