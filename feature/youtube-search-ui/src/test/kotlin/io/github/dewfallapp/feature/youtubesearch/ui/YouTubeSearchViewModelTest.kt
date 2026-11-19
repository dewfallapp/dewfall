package io.github.dewfallapp.feature.youtubesearch.ui

import io.github.dewfallapp.core.data.search.YouTubeSearchRepository
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.core.model.VideoSummary
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.feature.youtubesearch.logic.ResultsArea
import io.github.dewfallapp.feature.youtubesearch.logic.SearchRequest
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.Clear
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.Submit
import io.github.dewfallapp.feature.youtubesearch.logic.YouTubeSearchIntent.TryAgain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                    )
                ),
        )
    )

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
        val viewModel = YouTubeSearchViewModel(repository)

        viewModel.dispatch(Submit(" sourdough "))
        assertEquals(listOf("sourdough"), repository.searches.map { it.query })
        repository.searches.single().answer.complete(found("aaaaaaaaaaa"))

        val results = assertIs<ResultsArea.Results>(viewModel.state.value.results)
        assertEquals("aaaaaaaaaaa", results.rows.single().id.value)
    }

    @Test
    fun `a new search cancels the running one, whose answer never shows`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository)

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
        val viewModel = YouTubeSearchViewModel(repository)

        viewModel.dispatch(Submit("sourdough"))
        viewModel.dispatch(Clear)

        assertTrue(repository.searches.single().wasCancelled)
        assertEquals(ResultsArea.Idle, viewModel.state.value.results)
    }

    @Test
    fun `a blank search, or trying again with nothing to try, sends nothing`() = runTest {
        val viewModel = YouTubeSearchViewModel(repository)

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
        val viewModel = YouTubeSearchViewModel(repository)

        viewModel.dispatch(Submit("sourdough"))
        repository.searches.single().answer.complete(YouTubeResult.Failure(YouTubeError.NETWORK))
        viewModel.dispatch(TryAgain)

        assertEquals(listOf("sourdough", "sourdough"), repository.searches.map { it.query })
        assertEquals(
            ResultsArea.Loading(SearchRequest("sourdough", number = 2)),
            viewModel.state.value.results,
        )
    }
}
