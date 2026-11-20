package io.github.dewfallapp.core.data.search

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class LayerYouTubeSearchRepositoryTest {
    private val queries = mutableListOf<String>()

    private fun repository(answer: YouTubeResult<SearchResults>) =
        LayerYouTubeSearchRepository(
            SearchRemoteDataSource { query ->
                queries += query
                answer
            },
        )

    @Test
    fun `a search passes the query to the layer and returns its results unchanged`() = runTest {
        val answer = YouTubeResult.Success(SearchResults(emptyList(), emptyList()))

        val result = repository(answer).search("sourdough")

        assertSame(answer, result)
        assertEquals(listOf("sourdough"), queries)
    }

    @Test
    fun `a failure comes back as the layer gave it`() = runTest {
        val answer = YouTubeResult.Failure(YouTubeError.NETWORK)

        assertSame(answer, repository(answer).search("sourdough"))
    }
}
