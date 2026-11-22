package io.github.dewfallapp.feature.youtubesearch.ui

import io.github.dewfallapp.core.data.search.YouTubeSearchRepository
import io.github.dewfallapp.core.data.subscriptions.SubscriptionsRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelOverview
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.SubscribedChannel
import io.github.dewfallapp.core.model.Thumbnail
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.SearchRequest
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.Clear
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.Submit
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.Subscribe
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.TryAgain
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.UnsubscribeConfirmed
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private fun found(id: String) =
    YouTubeResult.Success(
        SearchResults(
            channels = emptyList(),
            videos =
                listOf(
                    VideoSummary(
                        id = VideoId(id),
                        title = "A title",
                        channelId = ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
                        channelName = "A channel",
                        duration = null,
                        published = null,
                        thumbnails = emptyList(),
                        kind = VideoKind.VIDEO,
                    ),
                ),
        ),
    )

private val KETTLE =
    Channel(
        id = ChannelId("UCaaaaaaaaaaaaaaaaaaaaaa"),
        name = "Kettle and Crumb",
        avatars = listOf(Thumbnail("https://example.com/kettle.jpg", width = 88, height = 88)),
        description = "Bread, fermentation and old recipes",
    )

private val JAR = Channel(ChannelId("UCbbbbbbbbbbbbbbbbbbbbbb"), "The Starter Jar", emptyList(), "")

private fun foundChannels(vararg channels: Channel) =
    YouTubeResult.Success(SearchResults(channels = channels.toList(), videos = emptyList()))

/**
 * Keeps subscriptions in memory, as the database would, and records every write. While [kept] is
 * null, its list emits nothing, like a database that hasn't answered yet.
 */
private class FakeSubscriptions : SubscriptionsRepository {
    val kept = MutableStateFlow<List<SubscribedChannel>?>(emptyList())
    val subscribed = mutableListOf<Channel>()
    val unsubscribed = mutableListOf<ChannelId>()

    override fun subscribedChannels(): Flow<List<SubscribedChannel>> = kept.filterNotNull()

    // Search YouTube never reads the Channels screen's list.
    override fun channelOverviews(): Flow<List<ChannelOverview>> =
        subscribedChannels().map { list -> list.map { ChannelOverview(it, lastUpload = null) } }

    override fun subscribedChannel(channelId: ChannelId): Flow<SubscribedChannel?> =
        subscribedChannels().map { list -> list.firstOrNull { it.channel.id == channelId } }

    override fun isSubscribed(channelId: ChannelId): Flow<Boolean> =
        subscribedChannel(channelId).map { it != null }

    override suspend fun subscribe(channel: Channel) {
        subscribed += channel
        val list = kept.value.orEmpty()
        if (list.none { it.channel.id == channel.id }) {
            kept.value = list + SubscribedChannel(channel, Instant.parse("2027-03-15T10:30:00Z"))
        }
    }

    override suspend fun subscribeAll(channels: List<Channel>): Int =
        error("Search YouTube subscribes to one channel at a time")

    override suspend fun unsubscribe(channelId: ChannelId) {
        unsubscribed += channelId
        kept.value = kept.value.orEmpty().filterNot { it.channel.id == channelId }
    }
}

/** One search the fake repository got. It waits until the test completes its answer. */
private class FakeSearch(val query: String) {
    val answer = CompletableDeferred<YouTubeResult<SearchResults>>()
    var wasCancelled = false
}

/** A repository whose searches wait for the test, so a test decides when each one finishes. */
private class FakeRepository : YouTubeSearchRepository {
    val searches = mutableListOf<FakeSearch>()

    override suspend fun search(query: String): YouTubeResult<SearchResults> {
        val search = FakeSearch(query).also { searches += it }
        try {
            return search.answer.await()
        } catch (cancelled: CancellationException) {
            search.wasCancelled = true
            throw cancelled
        }
    }
}

/**
 * Tests which searches the ViewModel starts and stops, and that their answers come back through the
 * reducer. The reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeSearchViewModelTest {
    private val repository = FakeRepository()

    private val subscriptions = FakeSubscriptions()

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. An unconfined
        // dispatcher starts each search at once, so the test sees it running right after dispatch.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a search sends one request, and its answer shows`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)

        viewModel.dispatch(Submit(" sourdough "))
        assertEquals(listOf("sourdough"), repository.searches.map { it.query })
        repository.searches.single().answer.complete(found("aaaaaaaaaaa"))

        val results = assertIs<ResultsArea.Results>(viewModel.state.value.results)
        assertEquals("aaaaaaaaaaa", results.videos.single().id.value)
    }

    @Test
    fun `a new search cancels the running one, whose answer never shows`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)

        viewModel.dispatch(Submit("bread"))
        viewModel.dispatch(Submit("sourdough"))

        val (bread, sourdough) = repository.searches
        assertTrue(bread.wasCancelled)
        assertFalse(sourdough.wasCancelled)
        bread.answer.complete(found("aaaaaaaaaaa"))
        assertEquals(
            ResultsArea.Loading(SearchRequest("sourdough", number = 2)),
            viewModel.state.value.results,
        )
    }

    @Test
    fun `clearing cancels the running search`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)

        viewModel.dispatch(Submit("sourdough"))
        viewModel.dispatch(Clear)

        assertTrue(repository.searches.single().wasCancelled)
        assertEquals(ResultsArea.Idle, viewModel.state.value.results)
    }

    @Test
    fun `a blank search, or trying again with nothing to try, sends nothing`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)

        viewModel.dispatch(Submit("  "))
        viewModel.dispatch(TryAgain)
        assertEquals(emptyList(), repository.searches)

        viewModel.dispatch(Submit("sourdough"))
        viewModel.dispatch(Submit(" "))
        viewModel.dispatch(TryAgain)

        val running = repository.searches.single()
        assertFalse(running.wasCancelled)
    }

    @Test
    fun `trying again after a failure sends the same search again`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)

        viewModel.dispatch(Submit("sourdough"))
        repository.searches.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(TryAgain)

        assertEquals(listOf("sourdough", "sourdough"), repository.searches.map { it.query })
        assertEquals(
            ResultsArea.Loading(SearchRequest("sourdough", number = 2)),
            viewModel.state.value.results,
        )
    }

    /** A ViewModel showing [channels] as results, with the database's answer read. */
    private fun showing(vararg channels: Channel): YouTubeSearchViewModel {
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)
        viewModel.dispatch(Submit("sourdough"))
        repository.searches.last().answer.complete(foundChannels(*channels))
        return viewModel
    }

    @Test
    fun `the subscribed channels reach the state, and follow the database`() = runTest {
        subscriptions.kept.value =
            listOf(SubscribedChannel(KETTLE, Instant.parse("2027-03-14T10:30:00Z")))
        val viewModel = YouTubeSearchViewModel(repository, subscriptions)
        assertEquals(setOf(KETTLE.id), viewModel.state.value.subscribedIds)

        subscriptions.kept.value = emptyList()
        assertEquals(emptySet(), viewModel.state.value.subscribedIds)
    }

    @Test
    fun `Subscribe keeps the row's ID, name and avatars, and no description`() = runTest {
        val viewModel = showing(KETTLE, JAR)

        viewModel.dispatch(Subscribe(KETTLE.id))

        assertEquals(listOf(KETTLE.copy(description = "")), subscriptions.subscribed)
        assertEquals(setOf(KETTLE.id), viewModel.state.value.subscribedIds)
    }

    @Test
    fun `Subscribe does nothing when subscribed, not shown, or before the database answers`() =
        runTest {
            subscriptions.kept.value =
                listOf(SubscribedChannel(KETTLE, Instant.parse("2027-03-14T10:30:00Z")))
            val viewModel = showing(KETTLE)
            viewModel.dispatch(Subscribe(KETTLE.id))
            viewModel.dispatch(Subscribe(JAR.id))
            assertEquals(emptyList(), subscriptions.subscribed)

            subscriptions.kept.value = null
            val unread = YouTubeSearchViewModel(repository, subscriptions)
            unread.dispatch(Submit("rye"))
            repository.searches.last().answer.complete(foundChannels(JAR))
            unread.dispatch(Subscribe(JAR.id))
            assertEquals(emptyList(), subscriptions.subscribed)
        }

    @Test
    fun `confirming the dialog removes that subscription`() = runTest {
        subscriptions.kept.value =
            listOf(SubscribedChannel(KETTLE, Instant.parse("2027-03-14T10:30:00Z")))
        val viewModel = showing(KETTLE)

        viewModel.dispatch(YouTubeSearchIntent.UnsubscribeTapped(KETTLE.id))
        viewModel.dispatch(UnsubscribeConfirmed(KETTLE.id))

        assertEquals(listOf(KETTLE.id), subscriptions.unsubscribed)
        assertEquals(emptySet(), viewModel.state.value.subscribedIds)
        assertEquals(null, viewModel.state.value.confirmingUnsubscribe)
    }

    @Test
    fun `cancelling the dialog removes nothing`() = runTest {
        subscriptions.kept.value =
            listOf(SubscribedChannel(KETTLE, Instant.parse("2027-03-14T10:30:00Z")))
        val viewModel = showing(KETTLE)

        viewModel.dispatch(YouTubeSearchIntent.UnsubscribeTapped(KETTLE.id))
        viewModel.dispatch(YouTubeSearchIntent.UnsubscribeCancelled)

        assertEquals(emptyList(), subscriptions.unsubscribed)
        assertEquals(setOf(KETTLE.id), viewModel.state.value.subscribedIds)
    }
}
